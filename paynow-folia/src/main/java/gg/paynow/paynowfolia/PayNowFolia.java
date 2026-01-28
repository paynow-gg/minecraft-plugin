package gg.paynow.paynowfolia;

import gg.paynow.paynowlib.PayNowLib;
import gg.paynow.paynowlib.PayNowUtils;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class PayNowFolia extends JavaPlugin {

    private static PayNowFolia instance;

    private PayNowLib payNowLib;

    private ScheduledTask apiCheckRunnableTask = null;
    private ScheduledTask reportEventsRunnableTask = null;

    @Override
    public void onEnable() {
        instance = this;

        Arrays.stream(this.getLogger().getHandlers()).forEach(handler -> handler.setLevel(Level.ALL));
        this.getLogger().setLevel(Level.ALL);

        String motd = ((TextComponent)this.getServer().motd()).content();
        this.payNowLib = new PayNowLib(command -> {
            GlobalRegionScheduler scheduler = this.getServer().getGlobalRegionScheduler();
            scheduler.run(this, task -> this.getServer()
                    .dispatchCommand(this.getServer().getConsoleSender(), command));
            return true;
        }, this.getServer().getIp() + ":" + this.getServer().getPort(), motd.isEmpty() ? "Folia Server" : motd);
        this.payNowLib.setLogCallback((s, level) -> this.getLogger().log(level, s));

        this.payNowLib.loadPayNowConfig(this.getConfigFile());

        new PayNowFoliaCommand(this);

        this.getServer().getPluginManager().registerEvents(new PlayerJoinListener(), this);

        this.payNowLib.onUpdateConfig(config -> this.startRunnables());

        this.startRunnables();
    }

    @Override
    public void onDisable() {
        this.cancelTasks();
        PayNowUtils.ASYNC_EXEC.shutdown();
    }

    private void startRunnables() {
        GlobalRegionScheduler scheduler = this.getServer().getGlobalRegionScheduler();
        this.cancelTasks();

        this.apiCheckRunnableTask = scheduler.runAtFixedRate(this, (task) -> {
            List<String> onlinePlayersNames = new ArrayList<>();
            List<UUID> onlinePlayersUUIDs = new ArrayList<>();
            for(Player player : this.getServer().getOnlinePlayers()) {
                onlinePlayersNames.add(player.getName());
                onlinePlayersUUIDs.add(player.getUniqueId());
            }
            payNowLib.fetchPendingCommands(onlinePlayersNames, onlinePlayersUUIDs);
        }, 1, this.payNowLib.getConfig().getApiCheckInterval() * 20L);

        this.reportEventsRunnableTask = scheduler.runAtFixedRate(this, task -> this.payNowLib.reportEvents(),
                1, this.payNowLib.getConfig().getEventsQueueReportInterval() * 20L);
    }

    private void cancelTasks() {
        if(this.apiCheckRunnableTask != null) this.apiCheckRunnableTask.cancel();
        if(this.reportEventsRunnableTask != null) this.reportEventsRunnableTask.cancel();
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

    public static PayNowFolia getInstance() {
        return instance;
    }
}
