package gg.paynow.paynowfolia;

import gg.paynow.paynowlib.PayNowLib;
import gg.paynow.paynowlib.PayNowUtils;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class PayNowFolia extends JavaPlugin {

    private PayNowLib payNowLib;

    private ScheduledTask runnableTask = null;

    @Override
    public void onEnable() {
        Arrays.stream(this.getLogger().getHandlers()).forEach(handler -> handler.setLevel(Level.ALL));
        this.getLogger().setLevel(Level.ALL);

        this.payNowLib = new PayNowLib(command -> {
            Bukkit.getScheduler().runTask(this, () -> this.getServer()
                    .dispatchCommand(this.getServer().getConsoleSender(), command));
            return true;
        }, this.getServer().getIp() + ":" + this.getServer().getPort(), ((TextComponent)this.getServer().motd()).content());
        this.payNowLib.setLogCallback((s, level) -> this.getLogger().log(level, s));

        this.payNowLib.loadPayNowConfig(this.getConfigFile());

        new PayNowFoliaCommand(this);

        this.payNowLib.onUpdateConfig(config -> this.startRunnable());

        this.startRunnable();
    }

    @Override
    public void onDisable() {
        if(this.runnableTask != null) this.runnableTask.cancel();
        PayNowUtils.ASYNC_EXEC.shutdown();
    }

    private void startRunnable() {
        GlobalRegionScheduler scheduler = this.getServer().getGlobalRegionScheduler();
        if(this.runnableTask != null) {
            this.runnableTask.cancel();
        }

        this.runnableTask = scheduler.runAtFixedRate(this, (task) -> {
            List<String> onlinePlayersNames = new ArrayList<>();
            List<UUID> onlinePlayersUUIDs = new ArrayList<>();
            for(Player player : this.getServer().getOnlinePlayers()) {
                onlinePlayersNames.add(player.getName());
                onlinePlayersUUIDs.add(player.getUniqueId());
            }
            payNowLib.fetchPendingCommands(onlinePlayersNames, onlinePlayersUUIDs);
        }, 1, this.payNowLib.getConfig().getApiCheckInterval() * 20L);
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
