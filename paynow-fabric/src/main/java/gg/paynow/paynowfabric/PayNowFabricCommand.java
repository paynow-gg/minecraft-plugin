package gg.paynow.paynowfabric;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class PayNowFabricCommand {

    public static LiteralArgumentBuilder<ServerCommandSource> generateCommand(PayNowFabric mod) {
        return literal("paynow")
                .requires(source -> source.hasPermissionLevel(2))
                .then(literal("link")
                        .then(argument("token", string())
                                .executes(context -> {
                                    String token = context.getArgument("token", String.class);
                                    mod.getPayNowLib().getConfig().setApiToken(token);
                                    mod.triggerConfigUpdate();
                                    context.getSource().sendFeedback(() -> Text.literal("API token updated").formatted(Formatting.RED), false);
                                    return 1;
                                })));
    }

}
