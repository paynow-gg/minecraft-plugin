package gg.paynow.paynowneoforge;

import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.json.JSONOptions;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;

public class NeoForgeText {

    private static final GsonComponentSerializer SERIALIZER = GsonComponentSerializer.builder()
            .options(JSONOptions.byDataVersion().at(SharedConstants.getCurrentVersion().getDataVersion().getVersion()))
            .build();

    public static Component toNative(net.kyori.adventure.text.Component component, HolderLookup.Provider registries) {
        Component text = Component.Serializer.fromJson(SERIALIZER.serialize(component), registries);
        return text == null ? Component.empty() : text;
    }

}
