package gg.paynow.paynowfabric;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.json.JSONOptions;
import net.minecraft.SharedConstants;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;

public class FabricText {

    private static final GsonComponentSerializer SERIALIZER = GsonComponentSerializer.builder()
            .options(JSONOptions.byDataVersion().at(SharedConstants.getGameVersion().getSaveVersion().getId()))
            .build();

    public static Text toNative(Component component, RegistryWrapper.WrapperLookup registries) {
        Text text = Text.Serialization.fromJson(SERIALIZER.serialize(component), registries);
        return text == null ? Text.empty() : text;
    }

}
