package gg.paynow.paynowlib;

import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.util.UUID;

public class Floodgate {

    public static boolean isBedrockPlayer(UUID uuid) {
        return getBedrockUsername(uuid) != null;
    }

    public static String getBedrockUsername(UUID uuid) {
        try {
            FloodgateApi api = FloodgateApi.getInstance();
            FloodgatePlayer player = api == null ? null : api.getPlayer(uuid);
            return player == null ? null : player.getUsername();
        } catch (NoClassDefFoundError e) {
            return null;
        }
    }

}
