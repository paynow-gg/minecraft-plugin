package gg.paynow.paynowsponge;

import com.google.inject.Inject;
import gg.paynow.paynowlib.PayNowLib;
import org.apache.logging.log4j.Logger;
import org.spongepowered.api.Server;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.SystemSubject;
import org.spongepowered.api.command.Command;
import org.spongepowered.api.command.exception.CommandException;
import org.spongepowered.api.command.manager.CommandManager;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.lifecycle.RegisterCommandEvent;
import org.spongepowered.api.event.lifecycle.StartedEngineEvent;
import org.spongepowered.api.event.lifecycle.StoppingEngineEvent;
import org.spongepowered.api.scheduler.ScheduledTask;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.plugin.PluginContainer;
import org.spongepowered.plugin.builtin.jvm.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

@Plugin("paynow-sponge")
public class PayNowSponge {

    private final PluginContainer container;

    @Inject
    private Logger logger;

    private PayNowLib payNowLib;

    private ScheduledTask task = null;

    @Inject
    PayNowSponge(final PluginContainer container) {
        this.container = container;
    }

    @Listener
    public void onServerStart(final StartedEngineEvent<Server> event) {
        this.payNowLib = new PayNowLib(command -> {
            SystemSubject console = Sponge.systemSubject();

            Server server = Sponge.server();
            server.scheduler().submit(Task.builder().plugin(this.container).execute(() -> {
                Sponge.server().causeStackManager().pushCause(console);
                CommandManager cmdManager = Sponge.server().commandManager();
                try {
                    cmdManager.process(console, command);
                } catch (CommandException ignored) {
                }
            }).build());
            return true;
        });

        this.payNowLib.setLogCallback((s, level) -> {
            if(level == Level.SEVERE) {
                this.logger.error(s);
            } else if(level == Level.WARNING) {
                this.logger.warn(s);
            } else {
                this.logger.info(s);
            }
        });

        this.payNowLib.loadPayNowConfig(this.getConfigFile());

        this.payNowLib.onUpdateConfig(config -> this.startRunnable());

        this.startRunnable();
    }

    private void startRunnable() {
        if (this.task != null) {
            this.task.cancel();
        }

        this.task = Sponge.server().scheduler().submit(Task.builder()
                .plugin(container)
                .interval(this.payNowLib.getConfig().getApiCheckInterval(), TimeUnit.SECONDS)
                        .execute(() -> {
                            List<String> onlinePlayersName = new ArrayList<>();
                            List<UUID> onlinePlayersUUID = new ArrayList<>();
                            for (ServerPlayer onlinePlayer : Sponge.server().onlinePlayers()) {
                                onlinePlayersName.add(onlinePlayer.name());
                                onlinePlayersUUID.add(onlinePlayer.uniqueId());
                            }
                            this.payNowLib.fetchPendingCommands(onlinePlayersName, onlinePlayersUUID);
                        })
                .build());
    }

    @Listener
    public void onRegisterCommands(final RegisterCommandEvent<Command.Parameterized> event) {
        event.register(this.container, PayNowSpongeCommand.generate(this), "paynow");
    }

    @Listener
    public void onServerStopping(final StoppingEngineEvent<Server> event) {
        if (this.task != null) this.task.cancel();
    }

    public void triggerConfigUpdate(){
        this.payNowLib.savePayNowConfig(this.getConfigFile());
        this.payNowLib.updateConfig();
    }

    private File getConfigFile() {
        return new File(Sponge.server().game().gameDirectory().toFile(), "config/paynow.json");
    }

    public PayNowLib getPayNowLib() {
        return payNowLib;
    }
}
