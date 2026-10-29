package gg.paynow.paynowbukkit;

import gg.paynow.paynowlib.PayNowUtils;
import gg.paynow.paynowlib.events.PayNowEvent;
import gg.paynow.paynowlib.events.PlayerJoinEventData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Date;

public class PlayerJoinListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        String ip = PayNowUtils.getIpAddress(player.getAddress());
        if (ip == null) return;
        PayNowEvent payNowEvent = new PayNowEvent("player_join", new Date(), new PlayerJoinEventData(ip, player.getUniqueId()));
        PayNowBukkit.getInstance().getPayNowLib().registerEvent(payNowEvent);
    }

}
