package gg.paynow.paynowbukkit;

import gg.paynow.paynowlib.PayNowLib;
import gg.paynow.paynowlib.PayNowUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class PayNowBukkit extends JavaPlugin {

    private static PayNowBukkit instance;

    private PayNowLib payNowLib;

    private int runnableId = -1;
    private int reportEventsRunnableId = -1;

    @Override
    public void onEnable() {
        instance = this;
        Arrays.stream(this.getLogger().getHandlers()).forEach(handler -> handler.setLevel(Level.ALL));
        this.getLogger().setLevel(Level.ALL);

        this.payNowLib = new PayNowLib(command -> {
            Bukkit.getScheduler().runTask(this, () -> this.getServer()
                    .dispatchCommand(this.getServer().getConsoleSender(), command));
            return true;
        }, this.getServer().getIp() + ":" + this.getServer().getPort(), this.getServer().getMotd());
        this.payNowLib.setLogCallback((s, level) -> this.getLogger().log(level, s));

        this.payNowLib.loadPayNowConfig(this.getConfigFile());

        new PayNowBukkitCommand(this);

        this.getServer().getPluginManager().registerEvents(new PlayerJoinListener(), this);

        this.payNowLib.onUpdateConfig(config -> this.startRunnable());

        this.startRunnable();
    }

    @Override
    public void onDisable() {
        this.stopRunnable();
        PayNowUtils.ASYNC_EXEC.shutdown();
    }

    private void startRunnable() {
        this.stopRunnable();

        this.runnableId = this.getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
            List<String> onlinePlayersNames = new ArrayList<>();
            List<UUID> onlinePlayersUUIDs = new ArrayList<>();
            for(Player player : this.getServer().getOnlinePlayers()) {
                onlinePlayersNames.add(player.getName());
                onlinePlayersUUIDs.add(player.getUniqueId());
            }
            payNowLib.fetchPendingCommands(onlinePlayersNames, onlinePlayersUUIDs);
        }, 0, this.payNowLib.getConfig().getApiCheckInterval() * 20L);

        this.reportEventsRunnableId = this.getServer().getScheduler().scheduleSyncRepeatingTask(this,
            () -> payNowLib.reportEvents(), 0, this.payNowLib.getConfig().getEventsQueueReportInterval() * 20L);
    }

    private void stopRunnable() {
        if (this.runnableId != -1) {
            this.getServer().getScheduler().cancelTask(this.runnableId);
        }
        if (this.reportEventsRunnableId != -1) {
            this.getServer().getScheduler().cancelTask(this.reportEventsRunnableId);
        }
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

    public static PayNowBukkit getInstance() {
        return instance;
    }
}
