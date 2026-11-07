import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import gg.paynow.paynowlib.PayNowConfig;
import gg.paynow.paynowlib.PayNowLib;
import gg.paynow.paynowlib.events.PayNowEvent;
import gg.paynow.paynowlib.events.PlayerJoinEventData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{\"api_token\": \"token\"} | 0",
            "{\"delay_between_commands_ms\": 250} | 250",
            "{\"delay_between_commands_ms\": 10000} | 10000",
            "{\"delay_between_commands_ms\": 10001} | 10000",
            "{\"delay_between_commands_ms\": 2147483647} | 10000",
    })
    public void readsDelayBetweenCommandsFromConfig(String configJson, int expectedDelayMillis) {
        PayNowConfig config = new Gson().fromJson(configJson, PayNowConfig.class);
        assertEquals(expectedDelayMillis, config.getDelayBetweenCommandsMillis());
    }

    @ParameterizedTest
    @CsvSource({"10000, false", "10001, true"})
    public void warnsOnlyWhenDelayIsAboveMaximum(int configuredDelayMillis, boolean expectWarning, @TempDir Path configDir) throws IOException {
        Path configFile = configDir.resolve("config.json");
        Files.write(configFile, ("{\"delay_between_commands_ms\": " + configuredDelayMillis + "}").getBytes());
        List<String> warnings = new ArrayList<>();
        PayNowLib paynowLib = new PayNowLib(command -> true, "localhost:25565", "Server");
        paynowLib.setLogCallback((message, level) -> {
            if (level == Level.WARNING) warnings.add(message);
        });

        paynowLib.loadPayNowConfig(configFile.toFile());

        assertEquals(expectWarning, warnings.stream().anyMatch(warning -> warning.contains("delay_between_commands_ms")));
    }

    @Test
    public void waitsBetweenCommands() {
        List<Long> runTimes = new ArrayList<>();
        PayNowLib paynowLib = libWithDelay(100, command -> runTimes.add(System.nanoTime()));

        assertEquals(3, paynowLib.handleResponse(queueResponse("1", "2", "3")));

        for (int i = 1; i < runTimes.size(); i++) {
            long gapMillis = TimeUnit.NANOSECONDS.toMillis(runTimes.get(i) - runTimes.get(i - 1));
            assertTrue(gapMillis >= 100, "gap before command " + (i + 1) + " was " + gapMillis + "ms");
        }
    }

    @Test
    public void doesNotWaitBeforeFirstOrAfterLastCommand() {
        PayNowLib paynowLib = libWithDelay(1000, command -> true);

        long startedAt = System.nanoTime();
        assertEquals(1, paynowLib.handleResponse(queueResponse("1")));

        assertTrue(millisSince(startedAt) < 500);
    }

    @Test
    public void doesNotWaitForSkippedCommands() {
        List<String> executedCommands = new ArrayList<>();
        PayNowLib paynowLib = libWithDelay(1000, executedCommands::add);
        paynowLib.handleResponse(queueResponse("1"));

        long startedAt = System.nanoTime();
        assertEquals(1, paynowLib.handleResponse(queueResponse("1", "2")));

        assertTrue(millisSince(startedAt) < 500);
        assertEquals(Arrays.asList("command1", "command2"), executedCommands);
    }

    @Test
    public void stopsBatchWhenInterrupted() {
        List<String> executedCommands = new ArrayList<>();
        PayNowLib paynowLib = libWithDelay(1000, command -> {
            executedCommands.add(command);
            Thread.currentThread().interrupt();
            return true;
        });

        try {
            assertEquals(1, paynowLib.handleResponse(queueResponse("1", "2")));
        } finally {
            Thread.interrupted();
        }

        assertEquals(Collections.singletonList("command1"), executedCommands);
    }

    private static PayNowLib libWithDelay(int delayMillis, Function<String, Boolean> executeCommand) {
        PayNowConfig config = new PayNowConfig();
        config.setDelayBetweenCommandsMillis(delayMillis);
        PayNowLib paynowLib = new PayNowLib(executeCommand, "localhost:25565", "Server");
        paynowLib.setConfig(config);
        return paynowLib;
    }

    private static String queueResponse(String... attemptIds) {
        List<Map<String, String>> commands = new ArrayList<>();
        for (String attemptId : attemptIds) {
            Map<String, String> command = new HashMap<>();
            command.put("attempt_id", attemptId);
            command.put("command", "command" + attemptId);
            commands.add(command);
        }
        return new Gson().toJson(commands);
    }

    private static long millisSince(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }

}
