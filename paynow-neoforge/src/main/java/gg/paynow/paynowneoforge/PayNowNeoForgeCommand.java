package gg.paynow.paynowneoforge;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

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
                                    context.getSource().sendSystemMessage(Component.literal("API token updated").withStyle(ChatFormatting.RED));
                                    return 1;
                                })));
    }
}
