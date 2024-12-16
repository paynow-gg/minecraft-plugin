package gg.paynow.paynowvelocity;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import static com.velocitypowered.api.command.BrigadierCommand.*;

public class PayNowVelocityCommand {

    public static BrigadierCommand generateCommand(PayNowVelocity plugin) {
        LiteralCommandNode<CommandSource> node = literalArgumentBuilder("paynow")
                .requires(source -> source.hasPermission("paynow.admin"))
                .then(literalArgumentBuilder("link")
                        .then(requiredArgumentBuilder("token", StringArgumentType.string())
                                .executes(context -> {
                                    CommandSource source = context.getSource();
                                    String token = StringArgumentType.getString(context, "token");
                                    plugin.getPayNowLib().getConfig().setApiToken(token);
                                    plugin.triggerConfigUpdate();
                                    source.sendMessage(Component.text("API token updated").color(NamedTextColor.GREEN));
                                    return 1;
                                })))
                .build();
        return new BrigadierCommand(node);
    }

}
