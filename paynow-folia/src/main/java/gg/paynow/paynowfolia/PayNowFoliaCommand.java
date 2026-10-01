package gg.paynow.paynowfolia;

import gg.paynow.paynowlib.PayNowLang;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
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
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("link")) {
                String token = args[1];
                plugin.getPayNowLib().getConfig().setApiToken(token);
                plugin.triggerConfigUpdate();
                sender.sendMessage(PayNowLang.TOKEN_UPDATED.get());
            } else {
                sender.sendMessage(PayNowLang.INVALID_ARGUMENTS.get());
            }
        } else {
            sender.sendMessage(PayNowLang.INVALID_ARGUMENTS.get());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            list.add("link");
        }
        return list;
    }

}
