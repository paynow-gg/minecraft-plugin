import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import gg.paynow.paynowlib.PayNowConfig;
import gg.paynow.paynowlib.PayNowLib;
import gg.paynow.paynowlib.events.PayNowEvent;
import gg.paynow.paynowlib.events.PlayerJoinEventData;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Date;
import java.util.UUID;

public class PayNowLibTest {

    @Test
    public void testHandlingResponse() {
        PayNowConfig config = new PayNowConfig();
        config.setApiToken("test");
        PayNowLib paynowLib = new PayNowLib(command -> {
            System.out.println("Executing command: " + command);
            return true;
        }, "localhost:25565", "Server");
        paynowLib.setConfig(config);

        String responseJson = "[{\"attempt_id\": \"1\",\"customer_name\": \"player1\",\"command\": \"command1\",\"online_only\": true,\"queued_at\": 123456},{\"attempt_id\": \"2\",\"customer_name\": \"player2\",\"command\": \"command2\",\"online_only\": false,\"queued_at\": 123457}]";
        int successCount = paynowLib.handleResponse(responseJson);
        assert successCount == 2;
    }

    @Test
    public void testSerializingEvent() {
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 0);
        PayNowEvent event = new PayNowEvent("player_join", new Date(), new PlayerJoinEventData(address.getHostString(), UUID.randomUUID()));
        Gson gson = new GsonBuilder().setPrettyPrinting().registerTypeAdapter(PayNowEvent.class, new PayNowEvent.PayNowEventAdapter()).create();
        String json = gson.toJson(event);
        System.out.println(json);
    }

}
