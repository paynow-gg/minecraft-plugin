package gg.paynow.paynowvelocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import gg.paynow.paynowlib.PayNowLib;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Plugin(
        id = "paynow-velocity",
        name = "PayNow",
        version = "1.0",
        authors = {"PayNow"},
        url = "https://paynow.gg"
)
public class PayNowVelocity {

    @Inject
    private Logger logger;
    @Inject
    private ProxyServer server;
    private final Path dataDirectory;

    private PayNowLib payNowLib;

    private ScheduledTask task = null;

    @Inject
    public PayNowVelocity(@DataDirectory Path dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        this.payNowLib = new PayNowLib(command -> {
            CommandSource console = this.server.getConsoleCommandSource();
            try {
                return this.server.getCommandManager().executeAsync(console, command).get();
            } catch (InterruptedException | ExecutionException e) {
                return false;
            }
        });

        this.payNowLib.setLogCallback((s, level) -> this.logger.log(level, s));

        this.payNowLib.loadPayNowConfig(this.getConfigFile());

        this.payNowLib.onUpdateConfig(config -> this.startRunnable());

        CommandManager commandManager = this.server.getCommandManager();
        CommandMeta commandMeta = commandManager.metaBuilder("paynow")
                .plugin(this)
                .build();
        this.server.getCommandManager().register(commandMeta, PayNowVelocityCommand.generateCommand(this));

        this.startRunnable();
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (this.task != null) this.task.cancel();
    }

    private void startRunnable() {
        if (this.task != null) {
            this.task.cancel();
        }

        this.task = this.server.getScheduler().buildTask(this, () -> {
            List<String> onlinePlayersName = new ArrayList<>();
            List<UUID> onlinePlayersUUID = new ArrayList<>();
            for (Player player : this.server.getAllPlayers()) {
                onlinePlayersName.add(player.getUsername());
                onlinePlayersUUID.add(player.getUniqueId());
            }
            this.payNowLib.fetchPendingCommands(onlinePlayersName, onlinePlayersUUID);
        }).repeat(this.payNowLib.getConfig().getApiCheckInterval(), TimeUnit.SECONDS).schedule();
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
}
