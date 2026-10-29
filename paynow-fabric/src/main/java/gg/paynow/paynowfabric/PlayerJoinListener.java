package gg.paynow.paynowfabric;

import gg.paynow.paynowlib.PayNowUtils;
import gg.paynow.paynowlib.events.PayNowEvent;
import gg.paynow.paynowlib.events.PlayerJoinEventData;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Date;

public class PlayerJoinListener {

    public void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            String ip = PayNowUtils.getIpAddress(handler.getConnectionAddress());
            if (ip == null) return;
            PayNowEvent payNowEvent = new PayNowEvent("player_join", new Date(), new PlayerJoinEventData(ip, player.getUuid()));
            PayNowFabric.getInstance().getPayNowLib().registerEvent(payNowEvent);
        });
    }

}
