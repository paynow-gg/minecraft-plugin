package gg.paynow.paynowfabric;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import gg.paynow.paynowlib.CheckoutTarget;
import gg.paynow.paynowlib.PayNowLang;
import net.kyori.adventure.text.Component;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Arrays;
import java.util.List;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class PayNowFabricCommand {

    public static LiteralArgumentBuilder<ServerCommandSource> generateCommand(PayNowFabric mod) {
        return literal("paynow")
                .requires(source -> source.hasPermissionLevel(4))
                .then(literal("link")
                        .then(argument("token", string())
                                .executes(context -> {
                                    String token = context.getArgument("token", String.class);
                                    mod.getPayNowLib().getConfig().setApiToken(token);
                                    mod.triggerConfigUpdate();
                                    sendFeedback(context.getSource(), PayNowLang.TOKEN_UPDATED.get());
                                    return 1;
                                })))
                .then(literal("checkout")
                        .then(argument("username", EntityArgumentType.player())
                                .then(argument("packageIds", greedyString())
                                        .executes(context -> {
                                            createCheckout(mod, context.getSource(),
                                                    EntityArgumentType.getPlayer(context, "username"),
                                                    Arrays.asList(getString(context, "packageIds").trim().split("\\s+")));
                                            return 1;
                                        }))));
    }

    private static void createCheckout(PayNowFabric mod, ServerCommandSource source, ServerPlayerEntity player, List<String> productIds) {
        CheckoutTarget target = new CheckoutTarget(player.getUuid(), player.getName().getString(), player.getIp(), () -> !player.isDisconnected(),
                message -> player.sendMessage(FabricText.toNative(message, source.getRegistryManager())));
        mod.getPayNowLib().sendCheckoutLink(productIds, target, message -> sendFeedback(source, message), source.getServer());
    }

    private static void sendFeedback(ServerCommandSource source, Component message) {
        source.sendFeedback(() -> FabricText.toNative(message, source.getRegistryManager()), false);
    }
}
