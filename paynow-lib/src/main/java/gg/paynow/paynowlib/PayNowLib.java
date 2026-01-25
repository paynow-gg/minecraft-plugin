package gg.paynow.paynowlib;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import gg.paynow.paynowlib.dto.CommandAttempt;
import gg.paynow.paynowlib.dto.LinkRequest;
import gg.paynow.paynowlib.dto.PlayerList;
import gg.paynow.paynowlib.events.PayNowEvent;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.*;
import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;

public class PayNowLib {

    private static final String VERSION = "1.0.0";

    private static final URI API_QUEUE_URL = URI.create("https://api.paynow.gg/v1/delivery/command-queue/");
    private static final URI API_LINK_URL = URI.create("https://api.paynow.gg/v1/delivery/gameserver/link");
    private static final URI API_EVENTS_URL = URI.create("https://api.paynow.gg/v1/delivery/events");

    private final CommandHistory executedCommands;
    private final List<String> successfulCommands;

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
        this.successfulCommands = new ArrayList<>();
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

        String formattedPlayers = formatPlayers(names, uuids);

        PayNowUtils.ASYNC_EXEC.submit(() -> {
            try (CloseableHttpClient client = HttpClients.createDefault()) {
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

                String responseBody = client.execute(request, responseHandler);

                handleResponse(responseBody);
            } catch (IOException e) {
                severe("Failed to fetch commands: error executing request");
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

        this.successfulCommands.clear();
        for (QueuedCommand command : commands) {
            if(this.executedCommands.contains(command.getAttemptId())) continue;

            boolean success = this.executeCommandCallback.apply(command.getCommand());
            if(success) {
                this.successfulCommands.add(command.getAttemptId());
            } else {
                this.warn("Failed to execute command: " + command.getCommand());
            }
        }

        if(this.config.doesLogCommandExecutions()) {
            this.debug("Received " + commands.size() + " commands, executed " + this.successfulCommands.size());
        }

        this.acknowledgeCommands(this.successfulCommands);

        return this.successfulCommands.size();
    }

    private void acknowledgeCommands(List<String> commandsIds) {
        if(commandsIds.isEmpty()) return;

        String apiToken = this.config.getApiToken();
        if(apiToken == null) {
            this.warn("API Token is not set");
            return;
        }

        String formatted = formatCommandIds(commandsIds);

        PayNowUtils.ASYNC_EXEC.submit(() -> {
            try (CloseableHttpClient client = HttpClients.createDefault()) {
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
                        for (String commandId : commandsIds) {
                            this.executedCommands.add(commandId);
                        }
                    }

                    return body;
                };

                client.execute(request, responseHandler);
            } catch (IOException e) {
                severe("Failed to acknowledge commands: error executing request");
            }
        });
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
            try (CloseableHttpClient client = HttpClients.createDefault()) {
                HttpPost request = new HttpPost(API_LINK_URL);
                request.setHeader("Content-Type", "application/json");
                request.setHeader("Authorization", "Gameserver " + apiToken);
                request.setHeader("Accept", "application/json");
                request.setEntity(new StringEntity(requestJson));

                ResponseHandler<String> responseHandler = response -> {
                    String body = response.getEntity() == null ? null : EntityUtils.toString(response.getEntity());
                    this.debug("Linked token: " + body);
                    if(!PayNowUtils.isSuccess(response.getStatusLine().getStatusCode())) {
                        this.warn("Failed to link token: " + body);
                    }

                    return body;
                };

                String responseBody = client.execute(request, responseHandler);
                log(responseBody);
                handleLinkResponse(responseBody);
            } catch (IOException e) {
                severe("Failed to link token: error executing request");
            }
        });
    }

    private void handleLinkResponse(String responseBody) {
        Gson gson = new Gson();
        JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);

        if(responseJson.has("update_available") && responseJson.get("update_available").getAsBoolean()) {
            String latestVersion = responseJson.get("latest_version").getAsString();
            this.warn("A new version of the PayNow plugin is available: " + latestVersion + "! Your version: " + VERSION);
        }

        if(responseJson.has("previously_linked")) {
            JsonObject previouslyLinked = responseJson.get("previously_linked").getAsJsonObject();
            String hostname = previouslyLinked.get("host_name").getAsString();
            String ip = previouslyLinked.get("ip").getAsString();
            this.warn("This token has been previously used on \"" + hostname + "\" (" + ip + "), ensure you have removed this token from the previous server.");
        }

        if(!responseJson.has("gameserver")) {
            this.warn("PayNow API did not return a GameServer object, this may be a transient issue, please try again or contact support.");
            return;
        }

        JsonObject gameServer = responseJson.get("gameserver").getAsJsonObject();
        String gsName = gameServer.get("name").getAsString();
        String gsId = gameServer.get("id").getAsString();

        this.log("Successfully connected to PayNow using the token for \"" + gsName + "\" (" + gsId + ")");
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
            try(CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpPost request = new HttpPost(API_EVENTS_URL);
                request.setHeader("Content-Type", "application/json");
                request.setHeader("Authorization", "Gameserver " + apiToken);
                request.setHeader("Accept", "application/json");
                request.setEntity(new StringEntity(requestJson));

                HttpResponse response = httpClient.execute(request);
                int statusCode = response.getStatusLine().getStatusCode();
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
        }

        if(!exists) {
            this.savePayNowConfig(configFile);
        }

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
