package gg.paynow.paynowfolia;

import gg.paynow.paynowlib.CheckoutTarget;
import gg.paynow.paynowlib.PayNowLang;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record PayNowFoliaCommand(PayNowFolia plugin) implements TabExecutor {

    public PayNowFoliaCommand(PayNowFolia plugin) {
        this.plugin = plugin;

        PluginCommand command = plugin.getCommand("paynow");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);

            command.permissionMessage(PayNowLang.NO_PERMISSION.get());
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("link")) {
            String token = args[1];
            plugin.getPayNowLib().getConfig().setApiToken(token);
            plugin.triggerConfigUpdate();
            sender.sendMessage(PayNowLang.TOKEN_UPDATED.get());
        } else if (args.length >= 3 && args[0].equalsIgnoreCase("checkout")) {
            this.createCheckout(sender, args[1], Arrays.asList(args).subList(2, args.length));
        } else {
            sender.sendMessage(PayNowLang.INVALID_ARGUMENTS.get());
        }
        return true;
    }

    private void createCheckout(CommandSender sender, String username, List<String> productIds) {
        Player player = plugin.getServer().getPlayerExact(username);
        if (player == null) {
            sender.sendMessage(PayNowLang.PLAYER_NOT_ONLINE.get("player", username));
            return;
        }

        String ip = player.getAddress() == null ? null : player.getAddress().getHostString();
        CheckoutTarget target = new CheckoutTarget(player.getUniqueId(), player.getName(), ip, player::isOnline, player::sendMessage);
        plugin.getPayNowLib().sendCheckoutLink(productIds, target, sender::sendMessage,
                task -> plugin.getServer().getGlobalRegionScheduler().execute(plugin, task));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            list.add("link");
            list.add("checkout");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("checkout")) {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                list.add(player.getName());
            }
        }
        return list;
    }

}
