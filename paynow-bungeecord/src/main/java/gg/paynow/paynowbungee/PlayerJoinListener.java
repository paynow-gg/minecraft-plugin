package gg.paynow.paynowbungee;

import gg.paynow.paynowlib.events.PayNowEvent;
import gg.paynow.paynowlib.events.PlayerJoinEventData;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Date;

public class PlayerJoinListener implements Listener {

    @EventHandler
    public void onPostLogin(PostLoginEvent e) {
        ProxiedPlayer player = e.getPlayer();
        String ip;
        SocketAddress address = player.getSocketAddress();
        if (address == null) {
            ip = "null";
        } else {
            if(address instanceof InetSocketAddress) {
                ip = ((InetSocketAddress) address).getHostString();
            } else {
                ip = "null";
            }
        }
        PayNowEvent payNowEvent = new PayNowEvent("player_join", new Date(), new PlayerJoinEventData(ip, player.getUniqueId()));
        PayNowBungee.getInstance().getPayNowLib().registerEvent(payNowEvent);
    }

}
