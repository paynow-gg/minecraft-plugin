package gg.paynow.paynowsponge;

import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.spongepowered.api.command.Command;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.parameter.Parameter;

public class PayNowSpongeCommand {

    public static Command.Parameterized generate(PayNowSponge plugin) {
        Parameter.Value<String> tokenParameter = Parameter.string().key("token").build();
        Command.Parameterized linkCmd = Command.builder()
                .executor(context -> {
                    String token = context.requireOne(tokenParameter);
                    plugin.getPayNowLib().getConfig().setApiToken(token);
                    plugin.triggerConfigUpdate();
                    context.sendMessage(Identity.nil(), Component.text("API token updated").color(NamedTextColor.GREEN));
                    return CommandResult.success();
                })
                .addParameter(tokenParameter)
                .build();

        return Command
                .builder()
                .addChild(linkCmd, "link")
                .permission("paynow.admin")
                .shortDescription(Component.text("PayNow command"))
                .build();
    }

}
