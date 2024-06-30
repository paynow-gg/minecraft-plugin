package gg.paynow.paynowfabric;

import gg.paynow.paynowlib.PayNowLib;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;

public class PayNowFabric implements DedicatedServerModInitializer {

    private MinecraftServer server;

    private PayNowLib payNowLib;

    public static final Logger LOGGER = Logger.getLogger("PayNow");

    private int lastCheck = 0;

    @Override
    public void onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(PayNowFabricCommand.generateCommand(this)));
    }

    private void onServerStarted(MinecraftServer server) {
        this.server = server;

        this.payNowLib = new PayNowLib(command -> server.getCommandManager().executeWithPrefix(server.getCommandSource(), command) == 1);
        this.payNowLib.setLogger(LOGGER);

        this.payNowLib.loadPayNowConfig(this.getConfigFile());

        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if(lastCheck > 0) {
                lastCheck--;
                return;
            }
            lastCheck = this.payNowLib.getConfig().getApiCheckInterval() * 20;
            this.check();
        });
    }

    private void check() {
        List<String> onlinePlayers = this.server.getPlayerManager().getPlayerList().stream()
                .map(PlayerEntity::getEntityName)
                .toList();
        payNowLib.fetchPendingCommands(onlinePlayers);
    }

    public void triggerConfigUpdate(){
        this.payNowLib.savePayNowConfig(this.getConfigFile());
        this.payNowLib.updateConfig();
    }

    private File getConfigFile() {
        return new File(this.server.getRunDirectory(), "config/paynow.json");
    }

    public PayNowLib getPayNowLib() {
        return payNowLib;
    }
}
