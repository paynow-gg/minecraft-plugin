package gg.paynow.paynowlib;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import gg.paynow.paynowlib.dto.CheckoutRequest;
import gg.paynow.paynowlib.dto.CommandAttempt;
import gg.paynow.paynowlib.dto.CustomerAuthRequest;
import gg.paynow.paynowlib.dto.LinkRequest;
import gg.paynow.paynowlib.dto.PlayerList;
import gg.paynow.paynowlib.events.PayNowEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
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
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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

    private static final int MAX_QUEUED_EVENTS = 1000;

    private static final URI API_QUEUE_URL = URI.create("https://api.paynow.gg/v1/delivery/command-queue/");
    private static final URI API_LINK_URL = URI.create("https://api.paynow.gg/v1/delivery/gameserver/link");
    private static final URI API_EVENTS_URL = URI.create("https://api.paynow.gg/v1/delivery/events");
    private static final URI API_CUSTOMER_AUTH_URL = URI.create("https://api.paynow.gg/v1/store/customer/auth");
    private static final URI API_CHECKOUT_URL = URI.create("https://api.paynow.gg/v1/checkouts");

    private final CommandHistory executedCommands;
    private final List<String> commandsToAcknowledge;
    private final Object commandLock = new Object();
    private final AtomicBoolean fetchInProgress = new AtomicBoolean(false);
    private final AtomicInteger consecutiveFetchFailures = new AtomicInteger(0);

    private final ConcurrentLinkedQueue<PayNowEvent> eventQueue;

    private final Function<String, Boolean> executeCommandCallback;

    private final List<Consumer<PayNowConfig>> updateConfigCallbacks = new ArrayList<>();
    
    private BiConsumer<String, Level> logCallback = null;

    private PayNowConfig config = null;

    private final String ip;
    private final String motd;

    private volatile LinkedStore linkedStore = null;

    public PayNowLib(Function<String, Boolean> executeCommandCallback, String ip, String motd) {
        this.executeCommandCallback = executeCommandCallback;
        this.executedCommands = new CommandHistory(500);
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
                request.setEntity(new StringEntity(formattedPlayers, ContentType.APPLICATION_JSON));

                ResponseHandler<String> responseHandler = response -> {
                    String body = response.getEntity() == null ? null : EntityUtils.toString(response.getEntity());
                    int statusCode = response.getStatusLine().getStatusCode();
                    if(statusCode != 200) {
                        severe("Failed to fetch commands " + describeError(statusCode, body));
                        return null;
                    }

                    return body;
                };

                String responseBody = PayNowUtils.HTTP_CLIENT.execute(request, responseHandler);
                if(responseBody == null) {
                    this.consecutiveFetchFailures.incrementAndGet();
                    return;
                }

                int failedAttempts = this.consecutiveFetchFailures.getAndSet(0);
                if(failedAttempts > 0) {
                    this.log("Reconnected to PayNow after " + failedAttempts + " failed attempts");
                }

                handleResponse(responseBody);
            } catch (IOException e) {
                this.consecutiveFetchFailures.incrementAndGet();
                severe("Failed to fetch commands: couldn't reach PayNow (" + e.getMessage() + ")");
            } finally {
                this.fetchInProgress.set(false);
            }
        });
    }

    public int handleResponse(String responseBody) {
        List<QueuedCommand> commands;
        try {
            commands = new Gson().fromJson(responseBody, new TypeToken<List<QueuedCommand>>(){}.getType());
        } catch (JsonParseException e) {
            commands = null;
        }

        if(commands == null) {
            this.severe("Failed to parse commands: " + responseBody);
            return 0;
        }

        return processCommands(commands);
    }

    private int processCommands(List<QueuedCommand> commands) {
        if(commands.isEmpty()) return 0;

        synchronized (this.commandLock) {
            int executed = 0;
            for (QueuedCommand command : commands) {
                String attemptId = command.getAttemptId();
                if(this.executedCommands.contains(attemptId) || this.commandsToAcknowledge.contains(attemptId)) continue;

                boolean success;
                try {
                    success = this.executeCommandCallback.apply(command.getCommand());
                } catch (Throwable e) {
                    this.warn("Failed to execute command: " + command.getCommand() + " (" + e + ")");
                    continue;
                }

                if(success) {
                    this.commandsToAcknowledge.add(attemptId);
                    this.executedCommands.add(attemptId);
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
            request.setEntity(new StringEntity(formatted, ContentType.APPLICATION_JSON));

            ResponseHandler<String> responseHandler = response -> {
                String body = response.getEntity() == null ? null : EntityUtils.toString(response.getEntity());
                int statusCode = response.getStatusLine().getStatusCode();
                if(!PayNowUtils.isSuccess(statusCode)) {
                    this.warn("Failed to acknowledge commands " + describeError(statusCode, body));
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
        this.linkedStore = null;
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
                request.setEntity(new StringEntity(requestJson, ContentType.APPLICATION_JSON));

                ResponseHandler<String> responseHandler = response -> {
                    String body = response.getEntity() == null ? null : EntityUtils.toString(response.getEntity());
                    this.debug("Linked token: " + body);
                    int statusCode = response.getStatusLine().getStatusCode();
                    if(!PayNowUtils.isSuccess(statusCode)) {
                        this.debug("Link response (HTTP " + statusCode + "): " + body);
                        if(statusCode == 401 || statusCode == 403) {
                            this.warn("PayNow rejected this token. Copy it again from your PayNow dashboard and run /paynow link <token>.");
                        } else {
                            this.warn("Couldn't link to PayNow " + describeError(statusCode, body) + "." + LINK_RETRY_HINT);
                        }
                        return null;
                    }

                    return body;
                };

                String responseBody = PayNowUtils.HTTP_CLIENT.execute(request, responseHandler);
                if(responseBody == null) return;

                log(responseBody);
                handleLinkResponse(responseBody, apiToken);
            } catch (IOException e) {
                severe("Couldn't reach PayNow to link this server. Check that the server can connect to the internet." + LINK_RETRY_HINT);
            }
        });
    }

    private void handleLinkResponse(String responseBody, String linkedToken) {
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

        String storeId = getString(responseJson, "store_id", null);
        if(storeId == null) {
            this.warn("PayNow didn't return this server's store, so /paynow checkout won't work until the next link.");
        } else if(linkedToken.equals(this.config.getApiToken())) {
            this.linkedStore = new LinkedStore(storeId, getString(responseJson, "store_platform", null));
        }

        this.log("Successfully connected to PayNow using the token for \"" + gsName + "\" (" + gsId + ")");
    }

    private static String describeServer(String hostname, String ip) {
        if(hostname != null && ip != null) return "\"" + hostname + "\" (" + ip + ")";
        if(hostname != null) return "\"" + hostname + "\"";
        if(ip != null) return ip;
        return "another server";
    }

    private static String describeError(int statusCode, String body) {
        String status = "(HTTP " + statusCode + ")";
        JsonObject error = parseJsonObject(body);
        String message = error == null ? null : getString(error, "message", null);
        if(message == null) return status + ": " + body;

        String traceId = getString(error, "trace_id", null);
        return status + ": " + message + (traceId == null ? "" : " (trace " + traceId + ")");
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

    public void sendCheckoutLink(List<String> productIds, CheckoutTarget target, Consumer<Component> sendToSender, Executor serverThread) {
        PayNowUtils.ASYNC_EXEC.submit(() -> {
            try {
                LinkedStore store = this.linkedStore;
                if(store == null) {
                    throw new CheckoutException("this server isn't linked to a PayNow store");
                }

                String customerToken = this.authenticateCustomer(store, target);
                String checkoutUrl = this.createCheckout(productIds, customerToken, target);
                serverThread.execute(() -> {
                    if(!target.isOnline()) {
                        sendToSender.accept(PayNowLang.CHECKOUT_PLAYER_LEFT.get("player", target.getName()));
                        return;
                    }

                    PayNowLang linkMessage = Floodgate.isBedrockPlayer(target.getUuid())
                            ? PayNowLang.CHECKOUT_LINK_BEDROCK
                            : PayNowLang.CHECKOUT_LINK;
                    target.sendMessage(linkMessage.get(
                            TagResolver.resolver("link", Tag.styling(ClickEvent.openUrl(checkoutUrl))),
                            Placeholder.unparsed("url", checkoutUrl),
                            Placeholder.unparsed("player", target.getName())));
                    sendToSender.accept(PayNowLang.CHECKOUT_SENT.get("player", target.getName()));
                });
            } catch (CheckoutException e) {
                this.warn("Failed to create checkout: " + e.getMessage());
                serverThread.execute(() -> sendToSender.accept(PayNowLang.CHECKOUT_FAILED.get("error", e.getMessage())));
            } catch (IOException e) {
                this.severe("Failed to create checkout: error executing request");
                serverThread.execute(() -> sendToSender.accept(PayNowLang.CHECKOUT_FAILED.get("error", "could not reach PayNow")));
            } catch (RuntimeException e) {
                this.severe("Failed to create checkout: " + e);
                this.debug(Arrays.toString(e.getStackTrace()));
            }
        });
    }

    private String authenticateCustomer(LinkedStore store, CheckoutTarget target) throws IOException {
        CustomerAuthRequest authRequest = customerAuthRequest(store, target);
        String requestJson = new Gson().toJson(authRequest);
        HttpPost request = this.storefrontRequest(API_CUSTOMER_AUTH_URL, requestJson, target);
        request.setHeader("x-paynow-store-id", store.getId());

        return PayNowUtils.HTTP_CLIENT.execute(request, response -> this.readResponseField(
                response.getStatusLine().getStatusCode(), readBody(response), "customer_token"));
    }

    private static CustomerAuthRequest customerAuthRequest(LinkedStore store, CheckoutTarget target) {
        if(store.isOfflineMinecraft()) {
            return new CustomerAuthRequest("minecraft", target.getName());
        }

        String bedrockUsername = Floodgate.getBedrockUsername(target.getUuid());
        if(bedrockUsername != null) {
            return new CustomerAuthRequest("minecraft_bedrock_name", bedrockUsername);
        }

        return new CustomerAuthRequest("minecraft_uuid", target.getUuid().toString());
    }

    private String createCheckout(List<String> productIds, String customerToken, CheckoutTarget target) throws IOException {
        String requestJson = new Gson().toJson(new CheckoutRequest(productIds));
        HttpPost request = this.storefrontRequest(API_CHECKOUT_URL, requestJson, target);
        request.setHeader("Authorization", "Customer " + customerToken);

        return PayNowUtils.HTTP_CLIENT.execute(request, response -> this.readResponseField(
                response.getStatusLine().getStatusCode(), readBody(response), "url"));
    }

    private HttpPost storefrontRequest(URI uri, String requestJson, CheckoutTarget target) throws IOException {
        this.debug(requestJson);

        HttpPost request = new HttpPost(uri);
        request.setHeader("Content-Type", "application/json");
        request.setHeader("Accept", "application/json");
        if(target.getIp() != null) {
            request.setHeader("x-paynow-customer-ip", target.getIp());
        }
        request.setEntity(new StringEntity(requestJson, ContentType.APPLICATION_JSON));
        return request;
    }

    private static String readBody(HttpResponse response) throws IOException {
        return response.getEntity() == null ? null : EntityUtils.toString(response.getEntity());
    }

    private String readResponseField(int statusCode, String body, String field) throws CheckoutException {
        JsonObject responseJson = parseJsonObject(body);

        if(!PayNowUtils.isSuccess(statusCode)) {
            this.debug("Checkout response (HTTP " + statusCode + "): " + body);
            String message = responseJson == null ? null : getString(responseJson, "message", null);
            throw new CheckoutException(message != null ? message : "PayNow returned status " + statusCode);
        }

        String value = responseJson == null ? null : getString(responseJson, field, null);
        if(value == null) {
            this.debug("Checkout response missing " + field + ": " + body);
            throw new CheckoutException("PayNow returned an unexpected response");
        }

        return value;
    }

    public void registerEvent(PayNowEvent event) {
        this.eventQueue.add(event);
        this.trimEventQueue();
    }

    private void requeueEvents(List<PayNowEvent> failedEvents) {
        List<PayNowEvent> events = new ArrayList<>(failedEvents);
        PayNowEvent event;
        while ((event = this.eventQueue.poll()) != null) {
            events.add(event);
        }

        int dropped = Math.max(0, events.size() - MAX_QUEUED_EVENTS);
        events.subList(dropped, events.size()).forEach(this.eventQueue::offer);

        if(dropped > 0) {
            this.warn("Event queue is full, dropped " + dropped + " events");
        }
    }

    private void trimEventQueue() {
        int dropped = 0;
        while(this.eventQueue.size() > MAX_QUEUED_EVENTS && this.eventQueue.poll() != null) {
            dropped++;
        }

        if(dropped > 0) {
            this.warn("Event queue is full, dropped " + dropped + " events");
        }
    }

    private static boolean isRetryableStatus(int statusCode) {
        return statusCode == 408 || statusCode == 429 || statusCode >= 500;
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
                request.setEntity(new StringEntity(requestJson, ContentType.APPLICATION_JSON));

                PayNowUtils.HTTP_CLIENT.execute(request, response -> {
                    int statusCode = response.getStatusLine().getStatusCode();
                    String body = readBody(response);
                    if(PayNowUtils.isSuccess(statusCode)) {
                        this.debug("Successfully reported " + eventsToReport.size() + " events");
                    } else if(isRetryableStatus(statusCode)) {
                        this.warn("Failed to report events " + describeError(statusCode, body));
                        this.requeueEvents(eventsToReport);
                    } else {
                        this.warn("Dropping " + eventsToReport.size() + " rejected events " + describeError(statusCode, body));
                        this.debug(requestJson);
                    }
                    return null;
                });
            } catch (IOException ex) {
                severe("Failed to report events: " + ex.getMessage());
                debug(Arrays.toString(ex.getStackTrace()));
                this.requeueEvents(eventsToReport);
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
