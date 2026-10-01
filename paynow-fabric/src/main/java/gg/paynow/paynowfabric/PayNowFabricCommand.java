package gg.paynow.paynowfabric;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import gg.paynow.paynowlib.PayNowLang;
import net.kyori.adventure.text.Component;
import net.minecraft.server.command.ServerCommandSource;

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
                                })));
    }

    private static void sendFeedback(ServerCommandSource source, Component message) {
        source.sendFeedback(() -> FabricText.toNative(message, source.getRegistryManager()), false);
    }
}
