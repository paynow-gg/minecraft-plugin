package gg.paynow.paynowneoforge;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import gg.paynow.paynowlib.PayNowLang;
import net.kyori.adventure.text.Component;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

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
                                })));
    }

    private static void sendMessage(CommandSourceStack source, Component message) {
        source.sendSystemMessage(NeoForgeText.toNative(message, source.registryAccess()));
    }
}
