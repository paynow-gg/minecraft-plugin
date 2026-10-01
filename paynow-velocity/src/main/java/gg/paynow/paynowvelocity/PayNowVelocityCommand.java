package gg.paynow.paynowvelocity;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import gg.paynow.paynowlib.CheckoutTarget;
import gg.paynow.paynowlib.PayNowLang;

import java.util.Arrays;
import java.util.List;

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
                                    source.sendMessage(PayNowLang.TOKEN_UPDATED.get());
                                    return 1;
                                })))
                .then(literalArgumentBuilder("checkout")
                        .then(requiredArgumentBuilder("username", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    plugin.getServer().getAllPlayers().forEach(player -> builder.suggest(player.getUsername()));
                                    return builder.buildFuture();
                                })
                                .then(requiredArgumentBuilder("packageIds", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            createCheckout(plugin, context.getSource(),
                                                    StringArgumentType.getString(context, "username"),
                                                    Arrays.asList(StringArgumentType.getString(context, "packageIds").trim().split("\\s+")));
                                            return 1;
                                        }))))
                .build();
        return new BrigadierCommand(node);
    }

    private static void createCheckout(PayNowVelocity plugin, CommandSource source, String username, List<String> productIds) {
        Player player = plugin.getServer().getPlayer(username).orElse(null);
        if (player == null) {
            source.sendMessage(PayNowLang.PLAYER_NOT_ONLINE.get("player", username));
            return;
        }

        CheckoutTarget target = new CheckoutTarget(player.getUniqueId(), player.getUsername(), player.getRemoteAddress().getHostString(), player::isActive, player::sendMessage);
        plugin.getPayNowLib().sendCheckoutLink(productIds, target, source::sendMessage,
                Runnable::run);
    }

}
