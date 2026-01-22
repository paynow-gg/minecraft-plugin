package gg.paynow.paynowvelocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import gg.paynow.paynowlib.PayNowLib;
import gg.paynow.paynowlib.PayNowUtils;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class PayNowVelocity {

    private static PayNowVelocity instance;

    @Inject
    private Logger logger;
    @Inject
    private ProxyServer server;
    private final Path dataDirectory;

    private PayNowLib payNowLib;

    private ScheduledTask task = null;
    private ScheduledTask reportEventsTask = null;

    @Inject
    public PayNowVelocity(@DataDirectory Path dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        instance = this;
        String ip = this.server.getBoundAddress().getHostString();
        String motd = this.server.getConfiguration().getMotd().toString();
        this.payNowLib = new PayNowLib(command -> {
            CommandSource console = this.server.getConsoleCommandSource();
            try {
                return this.server.getCommandManager().executeAsync(console, command).get();
            } catch (InterruptedException | ExecutionException e) {
                return false;
            }
        }, ip, motd);

        this.payNowLib.setLogCallback((s, level) -> this.logger.log(level, s));

        this.payNowLib.loadPayNowConfig(this.getConfigFile());

        this.payNowLib.onUpdateConfig(config -> this.startRunnable());

        CommandManager commandManager = this.server.getCommandManager();
        CommandMeta commandMeta = commandManager.metaBuilder("paynow")
                .plugin(this)
                .build();
        this.server.getCommandManager().register(commandMeta, PayNowVelocityCommand.generateCommand(this));

        this.server.getEventManager().register(this, new PlayerJoinListener());

        this.startRunnable();
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        this.stopRunnable();
        PayNowUtils.ASYNC_EXEC.shutdown();
    }

    private void startRunnable() {
        this.stopRunnable();

        this.task = this.server.getScheduler().buildTask(this, () -> {
            List<String> onlinePlayersName = new ArrayList<>();
            List<UUID> onlinePlayersUUID = new ArrayList<>();
            for (Player player : this.server.getAllPlayers()) {
                onlinePlayersName.add(player.getUsername());
                onlinePlayersUUID.add(player.getUniqueId());
            }
            this.payNowLib.fetchPendingCommands(onlinePlayersName, onlinePlayersUUID);
        }).repeat(this.payNowLib.getConfig().getApiCheckInterval(), TimeUnit.SECONDS).schedule();

        this.reportEventsTask = this.server.getScheduler().buildTask(this,
            () -> this.payNowLib.reportEvents()).repeat(this.payNowLib.getConfig().getEventsQueueReportInterval(), TimeUnit.SECONDS).schedule();
    }

    private void stopRunnable() {
        if (this.task != null) {
            this.task.cancel();
        }
        if (this.reportEventsTask != null) {
            this.reportEventsTask.cancel();
        }
    }

    public void triggerConfigUpdate(){
        this.payNowLib.savePayNowConfig(this.getConfigFile());
        this.payNowLib.updateConfig();
    }

    private File getConfigFile() {
        return new File(this.dataDirectory.toFile(), "paynow.json");
    }

    public PayNowLib getPayNowLib() {
        return payNowLib;
    }

    public static PayNowVelocity getInstance() {
        return instance;
    }
}
