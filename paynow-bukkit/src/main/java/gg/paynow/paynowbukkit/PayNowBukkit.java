package gg.paynow.paynowbukkit;

import gg.paynow.paynowlib.PayNowLib;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.List;

public class PayNowBukkit extends JavaPlugin {

    private PayNowLib payNowLib;

    private int runnableId = -1;

    @Override
    public void onEnable() {
        this.payNowLib = new PayNowLib(command -> this.getServer()
                .dispatchCommand(this.getServer().getConsoleSender(), command));
        this.payNowLib.setLogger(this.getLogger());

        this.payNowLib.loadPayNowConfig(this.getConfigFile());

        new PayNowBukkitCommand(this);

        this.payNowLib.onUpdateConfig(config -> this.startRunnable());

        this.startRunnable();
    }

    @Override
    public void onDisable() {
        if(runnableId != -1) this.getServer().getScheduler().cancelTask(runnableId);
    }

    private void startRunnable() {
        if(runnableId != -1) {
            this.getServer().getScheduler().cancelTask(runnableId);
        }

        this.runnableId = this.getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
            List<String> onlinePlayers = this.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .toList();
            payNowLib.fetchPendingCommands(onlinePlayers);
        }, 0, this.payNowLib.getConfig().getApiCheckInterval() * 20L);
    }

    private File getConfigFile() {
        return new File(this.getDataFolder(), "config.json");
    }

    public void triggerConfigUpdate(){
        this.payNowLib.savePayNowConfig(this.getConfigFile());
        this.payNowLib.updateConfig();
    }

    public PayNowLib getPayNowLib() {
        return payNowLib;
    }
}
