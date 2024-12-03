package gg.paynow.paynowbukkit;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;

import java.util.ArrayList;
import java.util.List;

public class PayNowBukkitCommand implements TabExecutor {

    private final PayNowBukkit plugin;

    public PayNowBukkitCommand(PayNowBukkit plugin) {
        this.plugin = plugin;

        PluginCommand command = plugin.getCommand("paynow");
        if(command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);

            command.setPermissionMessage(ChatColor.RED + "You do not have permission to use this command");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(args.length == 2) {
            if(args[0].equalsIgnoreCase("link")) {
                String token = args[1];
                plugin.getPayNowLib().getConfig().setApiToken(token);
                plugin.triggerConfigUpdate();
                sender.sendMessage(ChatColor.GREEN + "API token updated");
            } else {
                sender.sendMessage(ChatColor.RED + "Invalid arguments");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Invalid arguments");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> list = new ArrayList<>();
        if(args.length == 1) {
            list.add("link");
        }
        return list;
    }

}
