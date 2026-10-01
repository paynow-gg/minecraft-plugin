package gg.paynow.paynowsponge;

import gg.paynow.paynowlib.CheckoutTarget;
import gg.paynow.paynowlib.PayNowLang;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.Command;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.parameter.Parameter;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.scheduler.Task;

import java.util.Arrays;
import java.util.List;

public class PayNowSpongeCommand {

    public static Command.Parameterized generate(PayNowSponge plugin) {
        Parameter.Value<String> tokenParameter = Parameter.string().key("token").build();
        Command.Parameterized linkCmd = Command.builder()
                .executor(context -> {
                    String token = context.requireOne(tokenParameter);
                    plugin.getPayNowLib().getConfig().setApiToken(token);
                    plugin.triggerConfigUpdate();
                    context.sendMessage(Identity.nil(), PayNowLang.TOKEN_UPDATED.get());
                    return CommandResult.success();
                })
                .addParameter(tokenParameter)
                .build();

        Parameter.Value<ServerPlayer> playerParameter = Parameter.player().key("username").build();
        Parameter.Value<String> packageIdsParameter = Parameter.remainingJoinedStrings().key("packageIds").build();
        Command.Parameterized checkoutCmd = Command.builder()
                .executor(context -> {
                    createCheckout(plugin, context.cause().audience(), context.requireOne(playerParameter),
                            Arrays.asList(context.requireOne(packageIdsParameter).trim().split("\\s+")));
                    return CommandResult.success();
                })
                .addParameter(playerParameter)
                .addParameter(packageIdsParameter)
                .build();

        return Command
                .builder()
                .addChild(linkCmd, "link")
                .addChild(checkoutCmd, "checkout")
                .permission("paynow.admin")
                .shortDescription(Component.text("PayNow command"))
                .build();
    }

    private static void createCheckout(PayNowSponge plugin, Audience source, ServerPlayer player, List<String> productIds) {
        CheckoutTarget target = new CheckoutTarget(player.uniqueId(), player.name(), player.connection().address().getHostString(), player::isOnline, player::sendMessage);
        plugin.getPayNowLib().sendCheckoutLink(productIds, target, source::sendMessage,
                task -> Sponge.server().scheduler().submit(Task.builder().plugin(plugin.getContainer()).execute(task).build()));
    }

}
