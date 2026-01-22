package gg.paynow.paynowlib.events;

import com.google.gson.annotations.SerializedName;

import java.util.UUID;

public class PlayerJoinEventData extends EventData {

    @SerializedName("ip_address")
    private String ipAddress;

    @SerializedName("minecraft_uuid")
    private UUID minecraftUUID;

    public PlayerJoinEventData(String ipAddress, UUID minecraftUUID) {
        this.ipAddress = ipAddress;
        this.minecraftUUID = minecraftUUID;
    }

    public PlayerJoinEventData() { }

    public String getIpAddress() {
        return ipAddress;
    }

    public UUID getMinecraftUUID() {
        return minecraftUUID;
    }

}