package gg.paynow.paynowlib.dto;

import com.google.gson.annotations.SerializedName;

public class CommandAttempt {

    @SerializedName("attempt_id")
    String attemptId;

    public CommandAttempt(String attemptId) {
        this.attemptId = attemptId;
    }

}
