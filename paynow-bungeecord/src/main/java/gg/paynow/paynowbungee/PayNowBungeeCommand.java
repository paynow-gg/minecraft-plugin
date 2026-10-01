package gg.paynow.paynowbungee;

import gg.paynow.paynowlib.PayNowLang;
import net.kyori.adventure.audience.Audience;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;

public class PayNowBungeeCommand extends Command {

    private PayNowBungee plugin;

    public PayNowBungeeCommand(PayNowBungee plugin) {
        super("paynow", "paynow.admin");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
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
    }
}
