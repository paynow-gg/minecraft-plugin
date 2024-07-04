package gg.paynow.paynowlib;

import com.google.gson.annotations.SerializedName;

import java.util.UUID;

public class QueuedCommand {

    @SerializedName("attempt_id")
    private String attemptId;

    @SerializedName("customer_name")
    private String customerName;

    @SerializedName("minecraft_uuid")
    private UUID customerUUID;

    private String command;

    @SerializedName("online_only")
    private boolean onlineOnly;

    @SerializedName("queued_at")
    private String queuedAt;

    public QueuedCommand() {

    }

    public String getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(String attemptId) {
        this.attemptId = attemptId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCommand() {
        return command;
    }

    public boolean isOnlineOnly() {
        return onlineOnly;
    }

    public String getQueuedAt() {
        return queuedAt;
    }

}
