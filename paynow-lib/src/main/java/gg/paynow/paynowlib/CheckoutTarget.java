package gg.paynow.paynowlib;

import net.kyori.adventure.text.Component;

import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class CheckoutTarget {

    private final UUID uuid;
    private final String name;
    private final String ip;
    private final BooleanSupplier isOnline;
    private final Consumer<Component> messageSender;

    public CheckoutTarget(UUID uuid, String name, String ip, BooleanSupplier isOnline, Consumer<Component> messageSender) {
        this.uuid = uuid;
        this.name = name;
        this.ip = ip;
        this.isOnline = isOnline;
        this.messageSender = messageSender;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public String getIp() {
        return ip;
    }

    public boolean isOnline() {
        return isOnline.getAsBoolean();
    }

    public void sendMessage(Component message) {
        messageSender.accept(message);
    }

}
