package gg.paynow.paynowlib;

import com.google.gson.annotations.SerializedName;

public class PayNowConfig {

    public static final int MAX_DELAY_BETWEEN_COMMANDS_MILLIS = 10_000;

    @SerializedName(value = "api_token", alternate = "API Token")
    private String apiToken = null;

    @SerializedName(value = "check_interval", alternate = "Check interval")
    private int apiCheckInterval = 10;

    @SerializedName(value = "events_queue_report_interval", alternate = "Events queue report interval")
    private int eventsQueueReportInterval = 10;

    @SerializedName(value = "log_command_executions", alternate = "Log command executions")
    private boolean logCommandExecutions = true;

    @SerializedName("delay_between_commands_ms")
    private int delayBetweenCommandsMillis = 0;

    @SerializedName(value = "debug", alternate = "Debug")
    private boolean debug = false;

    public PayNowConfig() {
    }

    public String getApiToken() {
        return apiToken;
    }

    public int getApiCheckInterval() {
        return apiCheckInterval;
    }

    public int getEventsQueueReportInterval() {
        return eventsQueueReportInterval;
    }

    public boolean doesLogCommandExecutions() {
        return logCommandExecutions;
    }

    public int getDelayBetweenCommandsMillis() {
        return Math.min(delayBetweenCommandsMillis, MAX_DELAY_BETWEEN_COMMANDS_MILLIS);
    }

    public boolean isDelayBetweenCommandsAboveMax() {
        return delayBetweenCommandsMillis > MAX_DELAY_BETWEEN_COMMANDS_MILLIS;
    }

    public void setDelayBetweenCommandsMillis(int delayBetweenCommandsMillis) {
        this.delayBetweenCommandsMillis = delayBetweenCommandsMillis;
    }

    public void setApiCheckInterval(int apiCheckInterval) {
        this.apiCheckInterval = apiCheckInterval;
    }

    public void setEventsQueueReportInterval(int eventsQueueReportInterval) {
        this.eventsQueueReportInterval = eventsQueueReportInterval;
    }

    public void setLogCommandExecutions(boolean logCommandExecutions) {
        this.logCommandExecutions = logCommandExecutions;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }
}
