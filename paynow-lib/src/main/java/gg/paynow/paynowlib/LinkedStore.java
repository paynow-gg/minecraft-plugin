package gg.paynow.paynowlib;

public class LinkedStore {

    private static final String OFFLINE_MINECRAFT_PLATFORM = "minecraft_offline";

    private final String id;
    private final String platform;

    public LinkedStore(String id, String platform) {
        this.id = id;
        this.platform = platform;
    }

    public String getId() {
        return id;
    }

    public boolean isOfflineMinecraft() {
        return OFFLINE_MINECRAFT_PLATFORM.equals(platform);
    }

}
