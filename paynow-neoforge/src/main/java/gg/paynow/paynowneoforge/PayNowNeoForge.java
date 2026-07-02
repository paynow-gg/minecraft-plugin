package gg.paynow.paynowneoforge;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import gg.paynow.paynowlib.PayNowLib;
import gg.paynow.paynowlib.PayNowUtils;
import gg.paynow.paynowlib.events.PayNowEvent;
import gg.paynow.paynowlib.events.PlayerJoinEventData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

@Mod(PayNowNeoForge.MODID)
public class PayNowNeoForge {
    public static final String MODID = "paynow_neoforge";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static PayNowNeoForge instance;
    private MinecraftServer server;
    private PayNowLib payNowLib;

    private int lastCheck = 0;
    private int lastEventsCheck = 0;

    public PayNowNeoForge() {
        instance = this;
        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (ExampleMod) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onCommandsRegistration(RegisterCommandsEvent event) {
        PayNowNeoForgeCommand.register(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection(), instance);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        server = event.getServer();

        String motd = server.getMotd();
        this.payNowLib = new PayNowLib(command -> CompletableFuture.supplyAsync(() -> {
            try {
                server.getCommands().getDispatcher().execute(command, server.createCommandSourceStack());
            } catch (CommandSyntaxException ignored) {}
            return true; // Assume the command always succeeds, else it gets stuck.
        }, server).join(), server.getLocalIp() + ":" + server.getPort(), Objects.equals(motd, "") ? "NeoForge Server" : motd);

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
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
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
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity().level().isClientSide) return;
        ServerPlayer player = event.getEntity() instanceof ServerPlayer ? (ServerPlayer) event.getEntity() : null;
        if (player == null) return;
        String ip = player.getIpAddress();
        PayNowEvent payNowEvent = new PayNowEvent("player_join", new Date(), new PlayerJoinEventData(ip, player.getUUID()));
        instance.getPayNowLib().registerEvent(payNowEvent);
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        server = null;
        PayNowUtils.ASYNC_EXEC.shutdown();
    }

    private void check() {
        List<String> onlinePlayersName = new ArrayList<>();
        List<UUID> onlinePlayersUUID = new ArrayList<>();
        for(ServerPlayer player : this.server.getPlayerList().getPlayers()) {
            onlinePlayersName.add(player.getName().getString());
            onlinePlayersUUID.add(player.getUUID());
        }
        payNowLib.fetchPendingCommands(onlinePlayersName, onlinePlayersUUID);
    }

    public void triggerConfigUpdate(){
        this.payNowLib.savePayNowConfig(this.getConfigFile());
        this.payNowLib.updateConfig();
    }

    private File getConfigFile() {
        return new File(FMLPaths.CONFIGDIR.get().toFile(), "paynow.json");
    }

    public PayNowLib getPayNowLib() {
        return payNowLib;
    }
}
