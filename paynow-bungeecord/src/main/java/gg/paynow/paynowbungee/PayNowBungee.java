package gg.paynow.paynowbungee;

import gg.paynow.paynowlib.PayNowLib;
import gg.paynow.paynowlib.PayNowUtils;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ListenerInfo;
import net.md_5.bungee.api.plugin.Plugin;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PayNowBungee extends Plugin {

    private static PayNowBungee instance;

    private PayNowLib payNowLib;

    private int runnableId = -1;
    private int reportEventsRunnableId = -1;

    @Override
    public void onEnable() {
        instance = this;
        String ip;
        String motd;
        try {
            ListenerInfo listenerInfo = this.getProxy().getConfig().getListeners().iterator().next();
            InetSocketAddress socketAddress = (InetSocketAddress) listenerInfo.getSocketAddress();
            ip = socketAddress.getHostString();
            motd = listenerInfo.getMotd();
        } catch (Exception e) {
            this.getLogger().severe("Failed to get port and motd from bungeecord config. Please check your bungeecord config.");
            this.getLogger().severe("PayNowBungee will not be enabled.");
            this.getLogger().severe(e.getMessage());
            return;
        }
        this.payNowLib = new PayNowLib(command -> {
            CommandSender console = ProxyServer.getInstance().getConsole();
            return ProxyServer.getInstance().getPluginManager().dispatchCommand(console, command);
        }, ip, motd.isEmpty() ? "BungeeCord Server" : motd);
        this.payNowLib.setLogCallback((s, level) -> this.getLogger().log(level, s));

        this.payNowLib.loadPayNowConfig(this.getConfigFile());

        this.payNowLib.onUpdateConfig(config -> this.startRunnable());

        this.startRunnable();

        this.getProxy().getPluginManager().registerCommand(this, new PayNowBungeeCommand(this));
        this.getProxy().getPluginManager().registerListener(this, new PlayerJoinListener());
    }

    @Override
    public void onDisable() {
        this.cancelTasks();
        PayNowUtils.ASYNC_EXEC.shutdown();
    }

    private void startRunnable() {
        this.cancelTasks();

        runnableId = ProxyServer.getInstance().getScheduler().schedule(this, () -> {
            List<String> onlinePlayersNames = new ArrayList<>();
            List<UUID> onlinePlayersUUIDs = new ArrayList<>();
            ProxyServer.getInstance().getPlayers().forEach(player -> {
                onlinePlayersNames.add(player.getName());
                onlinePlayersUUIDs.add(player.getUniqueId());
            });
            payNowLib.fetchPendingCommands(onlinePlayersNames, onlinePlayersUUIDs);
        }, 0, this.payNowLib.getConfig().getApiCheckInterval(), TimeUnit.SECONDS).getId();

        reportEventsRunnableId = ProxyServer.getInstance().getScheduler().schedule(this,
            () -> payNowLib.reportEvents(), 0, this.payNowLib.getConfig().getEventsQueueReportInterval(), TimeUnit.SECONDS).getId();
    }

    private void cancelTasks() {
        if(runnableId != -1) ProxyServer.getInstance().getScheduler().cancel(runnableId);
        if(reportEventsRunnableId != -1) ProxyServer.getInstance().getScheduler().cancel(reportEventsRunnableId);
    }

    public void triggerConfigUpdate(){
        this.payNowLib.savePayNowConfig(this.getConfigFile());
        this.payNowLib.updateConfig();
    }

    private File getConfigFile() {
        return new File(this.getDataFolder(), "config.json");
    }

    public PayNowLib getPayNowLib() {
        return payNowLib;
    }

    public static PayNowBungee getInstance() {
        return instance;
    }
}
