package gg.paynow.paynowlib;

import com.google.gson.annotations.SerializedName;

public class QueuedCommand {

    @SerializedName("attempt_id")
    private String attemptId;

    @SerializedName("steam_id") // TODO: use nickname
    private String nickname;

    private String command;

    @SerializedName("online_only")
    private boolean onlineOnly;

    @SerializedName("queued_at")
    private String queuedAt;

    public QueuedCommand(String attemptId, String nickname, String command, boolean onlineOnly, String queuedAt) {
        this.attemptId = attemptId;
        this.nickname = nickname;
        this.command = command;
        this.onlineOnly = onlineOnly;
        this.queuedAt = queuedAt;
    }

    public QueuedCommand() {

    }

    public String getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(String attemptId) {
        this.attemptId = attemptId;
    }

    public String getNickname() {
        return nickname;
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
