package gg.paynow.paynowbungee;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Command;

public class PayNowBungeeCommand extends Command {

    private PayNowBungee plugin;

    public PayNowBungeeCommand(PayNowBungee plugin) {
        super("paynow", "paynow.admin");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if(args.length == 2) {
            if(args[0].equalsIgnoreCase("link")) {
                String token = args[1];
                plugin.getPayNowLib().getConfig().setApiToken(token);
                plugin.triggerConfigUpdate();
                sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GREEN + "API token updated"));
            } else {
                sender.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "Invalid arguments"));
            }
        } else {
            sender.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "Invalid arguments"));
        }
    }
}
