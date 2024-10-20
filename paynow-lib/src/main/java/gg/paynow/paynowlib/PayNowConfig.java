package gg.paynow.paynowlib;

import com.google.gson.annotations.SerializedName;

public class PayNowConfig {

    @SerializedName("API Token")
    private String apiToken = null;

    @SerializedName("Check interval")
    private int apiCheckInterval = 10;

    @SerializedName("Log command executions")
    private boolean logCommandExecutions = true;

    @SerializedName("IP Address")
    private String ipAddress = null;

    @SerializedName("Hostname")
    private String hostname = null;

    @SerializedName("Online")
    private boolean online = true;

    @SerializedName("Debug")
    private boolean debug = false;

    public PayNowConfig() {
    }

    public String getApiToken() {
        return apiToken;
    }

    public int getApiCheckInterval() {
        return apiCheckInterval;
    }

    public boolean doesLogCommandExecutions() {
        return logCommandExecutions;
    }

    public void setApiCheckInterval(int apiCheckInterval) {
        this.apiCheckInterval = apiCheckInterval;
    }

    public void setLogCommandExecutions(boolean logCommandExecutions) {
        this.logCommandExecutions = logCommandExecutions;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getHostname() {
        return hostname;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }
}
