package gg.paynow.paynowneoforge;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import gg.paynow.paynowlib.CheckoutTarget;
import gg.paynow.paynowlib.PayNowLang;
import net.kyori.adventure.text.Component;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.List;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;

public class PayNowNeoForgeCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> generateCommand(PayNowNeoForge mod) {
        return Commands.literal("paynow")
                .requires(source -> source.hasPermission(4))
                .then(Commands.literal("link")
                        .then(Commands.argument("token", string())
                                .executes(context -> {
                                    String token = context.getArgument("token", String.class);
                                    mod.getPayNowLib().getConfig().setApiToken(token);
                                    mod.triggerConfigUpdate();
                                    sendMessage(context.getSource(), PayNowLang.TOKEN_UPDATED.get());
                                    return 1;
                                })))
                .then(Commands.literal("checkout")
                        .then(Commands.argument("username", EntityArgument.player())
                                .then(Commands.argument("packageIds", greedyString())
                                        .executes(context -> {
                                            createCheckout(mod, context.getSource(),
                                                    EntityArgument.getPlayer(context, "username"),
                                                    Arrays.asList(getString(context, "packageIds").trim().split("\\s+")));
                                            return 1;
                                        }))));
    }

    private static void createCheckout(PayNowNeoForge mod, CommandSourceStack source, ServerPlayer player, List<String> productIds) {
        CheckoutTarget target = new CheckoutTarget(player.getUUID(), player.getName().getString(), player.getIpAddress(), () -> !player.hasDisconnected(),
                message -> player.sendSystemMessage(NeoForgeText.toNative(message, source.registryAccess())));
        mod.getPayNowLib().sendCheckoutLink(productIds, target, message -> sendMessage(source, message), source.getServer());
    }

    private static void sendMessage(CommandSourceStack source, Component message) {
        source.sendSystemMessage(NeoForgeText.toNative(message, source.registryAccess()));
    }
}
