package gg.paynow.paynowfolia;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

            command.permissionMessage(Component.text("You do not have permission to use this command").color(NamedTextColor.RED));
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("link")) {
                String token = args[1];
                plugin.getPayNowLib().getConfig().setApiToken(token);
                plugin.triggerConfigUpdate();
                sender.sendMessage(Component.text("API token updated").color(NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("Invalid arguments").color(NamedTextColor.RED));
            }
        } else {
            sender.sendMessage(Component.text("Invalid arguments").color(NamedTextColor.RED));
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
