package gg.paynow.paynowsponge;

import gg.paynow.paynowlib.events.PayNowEvent;
import gg.paynow.paynowlib.events.PlayerJoinEventData;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.network.ServerSideConnectionEvent;

import java.util.Date;

public class PlayerJoinListener {

    @Listener
    public void onPlayerJoin(ServerSideConnectionEvent.Join event) {
        ServerPlayer player = event.player();
        String ip;
        if (player.connection().address() == null) {
            ip = "null";
        } else {
            ip = player.connection().address().getHostString();
        }
        PayNowEvent payNowEvent = new PayNowEvent("player_join", new Date(), new PlayerJoinEventData(ip, player.uniqueId()));
        PayNowSponge.getInstance().getPayNowLib().registerEvent(payNowEvent);
    }

}
