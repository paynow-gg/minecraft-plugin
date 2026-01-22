package gg.paynow.paynowlib.events;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class PayNowEvent {

    private String eventName;

    private String timestamp;

    private EventData eventData;

    public PayNowEvent(String eventName, Date timestamp, EventData eventData) {
        this.eventName = eventName;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        this.timestamp = sdf.format(timestamp);
        this.eventData = eventData;
    }

    public PayNowEvent() {

    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public EventData getEventData() {
        return eventData;
    }

    public void setEventData(EventData eventData) {
        this.eventData = eventData;
    }

    public static class PayNowEventAdapter implements JsonSerializer<PayNowEvent> {

        @Override
        public JsonElement serialize(PayNowEvent src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("event", src.eventName);
            jsonObject.addProperty("timestamp", src.timestamp);
            jsonObject.add(src.eventName, context.serialize(src.eventData));
            return jsonObject;
        }
    }
}