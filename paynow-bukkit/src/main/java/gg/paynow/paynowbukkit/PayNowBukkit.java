package gg.paynow.paynowbukkit;

import gg.paynow.paynowlib.PayNowLib;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PayNowBukkit extends JavaPlugin {

    private PayNowLib payNowLib;

    private int runnableId = -1;

    @Override
    public void onEnable() {
        this.payNowLib = new PayNowLib(command -> {
            Bukkit.getScheduler().runTask(this, () -> this.getServer()
                    .dispatchCommand(this.getServer().getConsoleSender(), command));
            return true;
        });
        this.payNowLib.setLogCallback((s, level) -> {
            this.getLogger().log(level, s);
        });

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
            List<String> onlinePlayersNames = new ArrayList<>();
            List<UUID> onlinePlayersUUIDs = new ArrayList<>();
            for(Player player : this.getServer().getOnlinePlayers()) {
                onlinePlayersNames.add(player.getName());
                onlinePlayersUUIDs.add(player.getUniqueId());
            }
            payNowLib.fetchPendingCommands(onlinePlayersNames, onlinePlayersUUIDs);
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
