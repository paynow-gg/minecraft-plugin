package gg.paynow.paynowbungee;

import gg.paynow.paynowlib.CheckoutTarget;
import gg.paynow.paynowlib.PayNowLang;
import net.kyori.adventure.audience.Audience;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.List;

public class PayNowBungeeCommand extends Command {

    private PayNowBungee plugin;

    public PayNowBungeeCommand(PayNowBungee plugin) {
        super("paynow", "paynow.admin");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Audience audience = plugin.getAudiences().sender(sender);
        if(args.length == 2 && args[0].equalsIgnoreCase("link")) {
            String token = args[1];
            plugin.getPayNowLib().getConfig().setApiToken(token);
            plugin.triggerConfigUpdate();
            audience.sendMessage(PayNowLang.TOKEN_UPDATED.get());
        } else if(args.length >= 3 && args[0].equalsIgnoreCase("checkout")) {
            this.createCheckout(audience, args[1], Arrays.asList(args).subList(2, args.length));
        } else {
            audience.sendMessage(PayNowLang.INVALID_ARGUMENTS.get());
        }
    }

    private void createCheckout(Audience sender, String username, List<String> productIds) {
        ProxiedPlayer player = plugin.getProxy().getPlayer(username);
        if(player == null) {
            sender.sendMessage(PayNowLang.PLAYER_NOT_ONLINE.get("player", username));
            return;
        }

        SocketAddress address = player.getSocketAddress();
        String ip = address instanceof InetSocketAddress ? ((InetSocketAddress) address).getHostString() : null;
        CheckoutTarget target = new CheckoutTarget(player.getUniqueId(), player.getName(), ip, player::isConnected,
                plugin.getAudiences().player(player)::sendMessage);
        plugin.getPayNowLib().sendCheckoutLink(productIds, target, sender::sendMessage, Runnable::run);
    }
}
