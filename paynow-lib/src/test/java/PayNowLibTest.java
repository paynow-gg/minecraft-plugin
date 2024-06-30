import gg.paynow.paynowlib.PayNowConfig;
import gg.paynow.paynowlib.PayNowLib;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;

public class PayNowLibTest {

    @Test
    public void testHandlingResponse() {
        PayNowConfig config = new PayNowConfig();
        config.setApiToken("test");
        PayNowLib paynowLib = new PayNowLib(command -> {
            System.out.println("Executing command: " + command);
            return true;
        });
        paynowLib.setConfig(config);

        String responseJson = """
                [
                    {
                        "attempt_id": "1",
                        "steam_id": "player1",
                        "command": "command1",
                        "online_only": true,
                        "queued_at": 123456
                    },
                    {
                        "attempt_id": "2",
                        "steam_id": "player2",
                        "command": "command2",
                        "online_only": false,
                        "queued_at": 123457
                    }
                ]
                """;
        HttpResponse<String> response = new SimulatedHttpResponse(responseJson);
        paynowLib.handleResponse(response, List.of("player1", "player2"));
    }

}
