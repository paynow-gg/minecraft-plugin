package gg.paynow.paynowbungee;

import gg.paynow.paynowlib.PayNowLib;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ListenerInfo;
import net.md_5.bungee.api.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PayNowBungee extends Plugin {

    private PayNowLib payNowLib;

    private int runnableId = -1;

    @Override
    public void onEnable() {
        String ip;
        String motd;
        try {
            ListenerInfo listenerInfo = this.getProxy().getConfig().getListeners().iterator().next();
            ip = listenerInfo.getHost().getHostString();
            motd = listenerInfo.getMotd();
        } catch (Exception e) {
            e.printStackTrace();
            this.getLogger().severe("Failed to get port and motd from bungeecord config. Please check your bungeecord config.");
            return;
        }
        this.payNowLib = new PayNowLib(command -> {
            CommandSender console = ProxyServer.getInstance().getConsole();
            return ProxyServer.getInstance().getPluginManager().dispatchCommand(console, command);
        }, ip, motd);
        this.payNowLib.setLogCallback((s, level) -> this.getLogger().log(level, s));

        this.payNowLib.loadPayNowConfig(this.getConfigFile());

        this.payNowLib.onUpdateConfig(config -> this.startRunnable());

        this.startRunnable();

        this.getProxy().getPluginManager().registerCommand(this, new PayNowBungeeCommand(this));
    }

    @Override
    public void onDisable() {
        if(runnableId != -1) ProxyServer.getInstance().getScheduler().cancel(runnableId);
    }

    private void startRunnable() {
        if(runnableId != -1) {
            ProxyServer.getInstance().getScheduler().cancel(runnableId);
        }

        runnableId = ProxyServer.getInstance().getScheduler().schedule(this, () -> {
            List<String> onlinePlayersNames = new ArrayList<>();
            List<UUID> onlinePlayersUUIDs = new ArrayList<>();
            ProxyServer.getInstance().getPlayers().forEach(player -> {
                onlinePlayersNames.add(player.getName());
                onlinePlayersUUIDs.add(player.getUniqueId());
            });
            payNowLib.fetchPendingCommands(onlinePlayersNames, onlinePlayersUUIDs);
        }, 0, this.payNowLib.getConfig().getApiCheckInterval(), TimeUnit.SECONDS).getId();
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
}
