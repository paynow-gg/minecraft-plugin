package gg.paynow.paynowlib;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import gg.paynow.paynowlib.dto.CommandAttempt;
import gg.paynow.paynowlib.dto.LinkRequest;
import gg.paynow.paynowlib.dto.PlayerList;
import gg.paynow.paynowlib.events.PayNowEvent;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class PayNowLib {

    private static final String VERSION = loadVersion();

    private static String loadVersion() {
        try (InputStream stream = PayNowLib.class.getResourceAsStream("/paynow-lib.properties")) {
            if (stream == null) return "unknown";
            Properties properties = new Properties();
            properties.load(stream);
            return properties.getProperty("version", "unknown");
        } catch (IOException e) {
            return "unknown";
        }
    }

    private static final String LINK_RETRY_HINT = " Run /paynow link <token> to try again. If it keeps failing, contact PayNow support.";

    private static final URI API_QUEUE_URL = URI.create("https://api.paynow.gg/v1/delivery/command-queue/");
    private static final URI API_LINK_URL = URI.create("https://api.paynow.gg/v1/delivery/gameserver/link");
    private static final URI API_EVENTS_URL = URI.create("https://api.paynow.gg/v1/delivery/events");

    private final CommandHistory executedCommands;
    private final List<String> commandsToAcknowledge;
    private final Object commandLock = new Object();
    private final AtomicBoolean fetchInProgress = new AtomicBoolean(false);

    private final ConcurrentLinkedQueue<PayNowEvent> eventQueue;

    private final Function<String, Boolean> executeCommandCallback;

    private final List<Consumer<PayNowConfig>> updateConfigCallbacks = new ArrayList<>();
    
    private BiConsumer<String, Level> logCallback = null;

    private PayNowConfig config = null;

    private final String ip;
    private final String motd;

    public PayNowLib(Function<String, Boolean> executeCommandCallback, String ip, String motd) {
        this.executeCommandCallback = executeCommandCallback;
        this.executedCommands = new CommandHistory(25);
        this.commandsToAcknowledge = new ArrayList<>();
        this.eventQueue = new ConcurrentLinkedQueue<>();

        this.ip = ip;
        this.motd = motd;
    }

    public void updateConfig() {
        for(Consumer<PayNowConfig> callback : this.updateConfigCallbacks) {
            callback.accept(config);
        }

        this.linkToken();
    }

    public void onUpdateConfig(Consumer<PayNowConfig> callback) {
        this.updateConfigCallbacks.add(callback);
    }

    public void fetchPendingCommands(List<String> names, List<UUID> uuids) {
        this.debug("Fetching pending commands");
        String apiToken = this.config.getApiToken();
        if(apiToken == null) {
            this.warn("API Token is not set");
            return;
        }

        if(!this.fetchInProgress.compareAndSet(false, true)) {
            this.debug("Previous command fetch is still running, skipping this one");
            return;
        }

        String formattedPlayers = formatPlayers(names, uuids);

        PayNowUtils.ASYNC_EXEC.submit(() -> {
            try {
                HttpPost request = new HttpPost(API_QUEUE_URL);
                request.setHeader("Content-Type", "application/json");
                request.setHeader("Authorization", "Gameserver " + apiToken);
                request.setHeader("Accept", "application/json");
                request.setEntity(new StringEntity(formattedPlayers));

                ResponseHandler<String> responseHandler = response -> {
                    String body = response.getEntity() == null ? null : EntityUtils.toString(response.getEntity());
                    if(response.getStatusLine().getStatusCode() != 200) {
                        severe("Failed to fetch commands: " + body);
                        return null;
                    }

                    return body;
                };

                String responseBody = PayNowUtils.HTTP_CLIENT.execute(request, responseHandler);

                handleResponse(responseBody);
            } catch (IOException e) {
                severe("Failed to fetch commands: error executing request");
            } finally {
                this.fetchInProgress.set(false);
            }
        });
    }

    public int handleResponse(String responseBody) {
        Gson gson = new Gson();
        List<QueuedCommand> commands = gson.fromJson(responseBody, new TypeToken<List<QueuedCommand>>(){}.getType());
        if(commands == null) {
            this.severe("Failed to parse commands");
            this.severe(responseBody);
            return 0;
        }

        return processCommands(commands);
    }

    private int processCommands(List<QueuedCommand> commands) {
        if(commands.isEmpty()) return 0;

        synchronized (this.commandLock) {
            int executed = 0;
            for (QueuedCommand command : commands) {
                if(this.executedCommands.contains(command.getAttemptId())) continue;

                boolean success = this.executeCommandCallback.apply(command.getCommand());
                if(success) {
                    this.commandsToAcknowledge.add(command.getAttemptId());
                    this.executedCommands.add(command.getAttemptId());
                    executed++;
                } else {
                    this.warn("Failed to execute command: " + command.getCommand());
                }
            }

            if(this.config.doesLogCommandExecutions()) {
                this.debug("Received " + commands.size() + " commands, executed " + executed);
            }

            this.acknowledgeCommands();

            return executed;
        }
    }

    private void acknowledgeCommands() {
        if(this.commandsToAcknowledge.isEmpty()) return;

        String apiToken = this.config.getApiToken();
        if(apiToken == null) {
            this.warn("API Token is not set");
            return;
        }

        List<String> commands = new ArrayList<>(this.commandsToAcknowledge);
        String formatted = formatCommandIds(commands);

        try {
            HttpDeleteWithBody request = new HttpDeleteWithBody(API_QUEUE_URL);
            request.setHeader("Content-Type", "application/json");
            request.setHeader("Authorization", "Gameserver " + apiToken);
            request.setHeader("Accept", "application/json");
            request.setEntity(new StringEntity(formatted));

            ResponseHandler<String> responseHandler = response -> {
                String body = response.getEntity() == null ? null : EntityUtils.toString(response.getEntity());
                if(!PayNowUtils.isSuccess(response.getStatusLine().getStatusCode())) {
                    this.warn("Failed to acknowledge commands: " + body);
                } else {
                    this.commandsToAcknowledge.removeAll(commands);
                }

                return body;
            };

            PayNowUtils.HTTP_CLIENT.execute(request, responseHandler);
        } catch (IOException e) {
            severe("Failed to acknowledge commands: error executing request");
        }
    }

    private void linkToken() {
        this.debug("Linking token to game server");
        String apiToken = this.config.getApiToken();
        if(apiToken == null) {
            this.warn("API Token is not set");
            return;
        }

        Gson gson = new Gson();

        LinkRequest linkRequest = new LinkRequest(this.ip, this.motd == null ? "" : this.motd, "Minecraft", VERSION);
        String requestJson = gson.toJson(linkRequest);

        this.log(requestJson);

        PayNowUtils.ASYNC_EXEC.submit(() -> {
            try {
                HttpPost request = new HttpPost(API_LINK_URL);
                request.setHeader("Content-Type", "application/json");
                request.setHeader("Authorization", "Gameserver " + apiToken);
                request.setHeader("Accept", "application/json");
                request.setEntity(new StringEntity(requestJson));

                ResponseHandler<String> responseHandler = response -> {
                    String body = response.getEntity() == null ? null : EntityUtils.toString(response.getEntity());
                    this.debug("Linked token: " + body);
                    int statusCode = response.getStatusLine().getStatusCode();
                    if(!PayNowUtils.isSuccess(statusCode)) {
                        this.debug("Link response (HTTP " + statusCode + "): " + body);
                        if(statusCode == 401 || statusCode == 403) {
                            this.warn("PayNow rejected this token. Copy it again from your PayNow dashboard and run /paynow link <token>.");
                        } else {
                            this.warn("Couldn't link to PayNow (HTTP " + statusCode + ")." + LINK_RETRY_HINT);
                        }
                        return null;
                    }

                    return body;
                };

                String responseBody = PayNowUtils.HTTP_CLIENT.execute(request, responseHandler);
                if(responseBody == null) return;

                log(responseBody);
                handleLinkResponse(responseBody);
            } catch (IOException e) {
                severe("Couldn't reach PayNow to link this server. Check that the server can connect to the internet." + LINK_RETRY_HINT);
            }
        });
    }

    private void handleLinkResponse(String responseBody) {
        JsonObject responseJson = parseJsonObject(responseBody);
        if(responseJson == null) {
            this.warn("Couldn't link to PayNow because its response couldn't be read." + LINK_RETRY_HINT);
            return;
        }

        JsonElement updateAvailable = responseJson.get("update_available");
        if(updateAvailable != null && updateAvailable.isJsonPrimitive() && updateAvailable.getAsBoolean()) {
            String latestVersion = getString(responseJson, "latest_version", null);
            String available = latestVersion == null ? "" : " (" + latestVersion + ")";
            this.warn("A new version of the PayNow plugin is available" + available + ". You're running " + VERSION + ".");
        }

        JsonObject previouslyLinked = getObject(responseJson, "previously_linked");
        if(previouslyLinked != null) {
            String hostname = getString(previouslyLinked, "host_name", null);
            String ip = getString(previouslyLinked, "ip", null);
            this.warn("This token is also linked to " + describeServer(hostname, ip) + ". Remove it from that server so commands only run in one place.");
        }

        JsonObject gameServer = getObject(responseJson, "gameserver");
        if(gameServer == null) {
            this.warn("Couldn't link to PayNow because its response didn't include this server." + LINK_RETRY_HINT);
            return;
        }

        String gsName = getString(gameServer, "name", "unknown");
        String gsId = getString(gameServer, "id", "unknown");

        this.log("Successfully connected to PayNow using the token for \"" + gsName + "\" (" + gsId + ")");
    }

    private static String describeServer(String hostname, String ip) {
        if(hostname != null && ip != null) return "\"" + hostname + "\" (" + ip + ")";
        if(hostname != null) return "\"" + hostname + "\"";
        if(ip != null) return ip;
        return "another server";
    }

    private static JsonObject parseJsonObject(String json) {
        try {
            JsonElement element = new Gson().fromJson(json, JsonElement.class);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (JsonParseException e) {
            return null;
        }
    }

    private static JsonObject getObject(JsonObject parent, String key) {
        JsonElement element = parent.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String getString(JsonObject parent, String key, String fallback) {
        JsonElement element = parent.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    public void registerEvent(PayNowEvent event) {
        this.eventQueue.add(event);
    }

    public void reportEvents() {
        if(this.eventQueue.isEmpty()) return;

        String apiToken = this.config.getApiToken();
        if(apiToken == null) {
            this.warn("API Token is not set");
            return;
        }

        Gson gson = new GsonBuilder().registerTypeAdapter(PayNowEvent.class, new PayNowEvent.PayNowEventAdapter()).create();

        // Atomically drain all events from the queue
        List<PayNowEvent> eventsToReport = new ArrayList<>();
        PayNowEvent event;
        while ((event = this.eventQueue.poll()) != null) {
            eventsToReport.add(event);
        }

        if(eventsToReport.isEmpty()) return;

        String requestJson = gson.toJson(eventsToReport);

        this.debug(requestJson);

        // Execute the HTTP request asynchronously
        PayNowUtils.ASYNC_EXEC.submit(() -> {
            try {
                HttpPost request = new HttpPost(API_EVENTS_URL);
                request.setHeader("Content-Type", "application/json");
                request.setHeader("Authorization", "Gameserver " + apiToken);
                request.setHeader("Accept", "application/json");
                request.setEntity(new StringEntity(requestJson));

                int statusCode = PayNowUtils.HTTP_CLIENT.execute(request, response -> response.getStatusLine().getStatusCode());
                if(!PayNowUtils.isSuccess(statusCode)) {
                    this.warn("Failed to report events: " + statusCode);
                    // Re-add events to the front of the queue if failed to report
                    // Using addAll will append them, maintaining order
                    eventsToReport.forEach(this.eventQueue::offer);
                }else {
                    this.debug("Successfully reported " + eventsToReport.size() + " events");
                }
            } catch (IOException ex) {
                severe("Failed to report events: " + ex.getMessage());
                debug(Arrays.toString(ex.getStackTrace()));
                // Re-add events to the queue if failed to report
                eventsToReport.forEach(this.eventQueue::offer);
            }
        });
    }

    public void loadPayNowConfig(File configFile) {
        boolean exists = true;
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            try {
                configFile.createNewFile();
                exists = false;
            } catch (IOException e) {
                this.severe("Failed to create config file, using default values");
                this.config = new PayNowConfig();
                return;
            }
        }

        Gson gson = new Gson();
        try(InputStream is = Files.newInputStream(configFile.toPath())) {
            byte[] bytes = new byte[is.available()];
            DataInputStream dataInputStream = new DataInputStream(is);
            dataInputStream.readFully(bytes);

            String configJson = new String(bytes);
            PayNowConfig config = gson.fromJson(configJson, PayNowConfig.class);
            if(config == null && exists) {
                this.severe("Failed to parse config, using default values");
                this.config = new PayNowConfig();
            } else {
                if (config == null) {
                    this.config = new PayNowConfig();
                } else {
                    this.config = config;
                }
            }
        } catch (IOException e) {
            this.severe("Failed to read config file, using default values");
            this.config = new PayNowConfig();
        } catch (JsonParseException e) {
            this.severe("Failed to parse config, using default values: " + e.getMessage());
            this.config = new PayNowConfig();
        }

        if(!exists) {
            this.savePayNowConfig(configFile);
        }

        PayNowLang.load(new File(configFile.getParentFile(), "lang.json"), this::warn);

        this.linkToken();
    }

    public void savePayNowConfig(File configFile) {
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            try {
                configFile.createNewFile();
            } catch (IOException e) {
                this.severe("Failed to create config file");
                return;
            }

        }
        Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
        try(OutputStream os = Files.newOutputStream(configFile.toPath())) {
            os.write(gson.toJson(this.config).getBytes());
        } catch (IOException e) {
            this.severe("Failed to save config file");
        }
    }

    private String formatPlayers(List<String> names, List<UUID> uuids) {
        Gson gson = new Gson();

        PlayerList playerList = new PlayerList(names, uuids);
        String json = gson.toJson(playerList);
        this.debug(json);
        return json;
    }

    private String formatCommandIds(List<String> commandIds) {
        List<CommandAttempt> attempts = new ArrayList<>();
        for (String commandId : commandIds) {
            attempts.add(new CommandAttempt(commandId));
        }

        Gson gson = new Gson();
        String json = gson.toJson(attempts);
        this.debug(json);
        return json;
    }

    public void setLogCallback(BiConsumer<String, Level> logCallback) {
        this.logCallback = logCallback;
    }

    private void log(String message) {
        if(this.logCallback != null) this.logCallback.accept(message, Level.INFO);
    }

    private void debug(String message) {
        if(this.logCallback != null && this.config.isDebug()) this.logCallback.accept("[DEBUG] " + message, Level.INFO);
    }

    private void warn(String message) {
        if(this.logCallback != null) this.logCallback.accept("[WARN] " + message, Level.WARNING);
    }

    private void severe(String message) {
        if(this.logCallback != null) this.logCallback.accept("[SEVERE] " + message, Level.SEVERE);
    }

    public PayNowConfig getConfig() {
        return config;
    }

    // For testing purposes
    public void setConfig(PayNowConfig config) {
        this.config = config;
    }
}
