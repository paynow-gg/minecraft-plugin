package gg.paynow.paynowvelocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import gg.paynow.paynowlib.events.PayNowEvent;
import gg.paynow.paynowlib.events.PlayerJoinEventData;

import java.util.Date;

public class PlayerJoinListener {

    @Subscribe
    public void onPostLogin(PostLoginEvent e) {
        Player player = e.getPlayer();
        String ip;
        if (player.getRemoteAddress() == null) {
            ip = "null";
        } else {
            ip = player.getRemoteAddress().getHostString();
        }
        PayNowEvent payNowEvent = new PayNowEvent("player_join", new Date(), new PlayerJoinEventData(ip, player.getUniqueId()));
        PayNowVelocity.getInstance().getPayNowLib().registerEvent(payNowEvent);
    }

}
