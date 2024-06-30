package gg.paynow.paynowlib;

import com.google.gson.annotations.SerializedName;

public class PayNowConfig {

    @SerializedName("API Token")
    private String apiToken = null;

    @SerializedName("Check interval")
    private int apiCheckInterval = 10;

    @SerializedName("Log command executions")
    private boolean logCommandExecutions = true;

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

}
