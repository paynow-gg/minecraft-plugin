package gg.paynow.paynowbukkit;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import gg.paynow.paynowlib.PayNowLang;
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

            command.setPermissionMessage(LegacyComponentSerializer.legacySection().serialize(PayNowLang.NO_PERMISSION.get()));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Audience audience = plugin.getAudiences().sender(sender);
        if(args.length == 2) {
            if(args[0].equalsIgnoreCase("link")) {
                String token = args[1];
                plugin.getPayNowLib().getConfig().setApiToken(token);
                plugin.triggerConfigUpdate();
                audience.sendMessage(PayNowLang.TOKEN_UPDATED.get());
            } else {
                audience.sendMessage(PayNowLang.INVALID_ARGUMENTS.get());
            }
        } else {
            audience.sendMessage(PayNowLang.INVALID_ARGUMENTS.get());
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
