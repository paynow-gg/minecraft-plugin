package gg.paynow.paynowfabric;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import gg.paynow.paynowlib.PayNowLib;
import gg.paynow.paynowlib.PayNowUtils;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class PayNowFabric implements DedicatedServerModInitializer {

    private static PayNowFabric instance;

    private MinecraftServer server;

    private PayNowLib payNowLib;

    public static final Logger LOGGER = LoggerFactory.getLogger("PayNow");

    private int lastCheck = 0;
    private int lastEventsCheck = 0;

    @Override
    public void onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(PayNowFabricCommand.generateCommand(this)));

        new PlayerJoinListener().register();
    }

    private void onServerStarted(MinecraftServer server) {
        instance = this;
        this.server = server;

        this.payNowLib = new PayNowLib(command -> CompletableFuture.supplyAsync(() -> {
            try {
                server.getCommandManager().getDispatcher().execute(command, server.getCommandSource());
            } catch (CommandSyntaxException ignored) {}
            return true; // Assume the command always succeeds, else it gets stuck.
        }, server).join(), server.getServerIp() + ":" + server.getServerPort(), server.getServerMotd());

        this.payNowLib.setLogCallback((s, level) -> {
            if (level == Level.SEVERE) {
                LOGGER.error(s);
            } else if(level == Level.WARNING) {
                LOGGER.warn(s);
            } else {
                LOGGER.info(s);
            }
        });

        this.payNowLib.loadPayNowConfig(this.getConfigFile());

        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if(lastCheck > 0) {
                lastCheck--;
            } else {
                lastCheck = this.payNowLib.getConfig().getApiCheckInterval() * 20;
                this.check();
            }

            if(lastEventsCheck > 0) {
                lastEventsCheck--;
            } else {
                lastEventsCheck = this.payNowLib.getConfig().getEventsQueueReportInterval() * 20;
                this.payNowLib.reportEvents();
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register((__) -> PayNowUtils.ASYNC_EXEC.shutdown());
    }

    private void check() {
        List<String> onlinePlayersName = new ArrayList<>();
        List<UUID> onlinePlayersUUID = new ArrayList<>();
        for(PlayerEntity player : this.server.getPlayerManager().getPlayerList()) {
            onlinePlayersName.add(player.getName().getString());
            onlinePlayersUUID.add(player.getUuid());
        }
        payNowLib.fetchPendingCommands(onlinePlayersName, onlinePlayersUUID);
    }

    public void triggerConfigUpdate(){
        this.payNowLib.savePayNowConfig(this.getConfigFile());
        this.payNowLib.updateConfig();
    }

    private File getConfigFile() {
        return new File(FabricLoader.getInstance().getConfigDir().toFile(), "paynow.json");
    }

    public PayNowLib getPayNowLib() {
        return payNowLib;
    }

    public static PayNowFabric getInstance() {
        return instance;
    }
}
