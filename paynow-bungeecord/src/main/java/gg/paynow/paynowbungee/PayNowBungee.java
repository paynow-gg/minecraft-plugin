package gg.paynow.paynowbungee;

import gg.paynow.paynowlib.PayNowLib;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class PayNowBungee extends Plugin {

    private PayNowLib payNowLib;

    private int runnableId = -1;

    @Override
    public void onEnable() {
        this.payNowLib = new PayNowLib(command -> {
            CommandSender console = ProxyServer.getInstance().getConsole();
            return ProxyServer.getInstance().getPluginManager().dispatchCommand(console, command);
        });
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
            List<String> onlinePlayers = ProxyServer.getInstance().getPlayers().stream()
                    .map(CommandSender::getName)
                    .toList();
            payNowLib.fetchPendingCommands(onlinePlayers);
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
