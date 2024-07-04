package gg.paynow.paynowlib;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;

public class PayNowLib {

    private static final URI API_URL = URI.create("https://api.paynow.gg/v1/delivery/command-queue/");

    private final CommandHistory executedCommands;
    private final List<String> successfulCommands;

    private final Function<String, Boolean> executeCommandCallback;

    private final List<Consumer<PayNowConfig>> updateConfigCallbacks = new ArrayList<>();
    
    private BiConsumer<String, Level> logCallback = null;

    private PayNowConfig config = null;

    public PayNowLib(Function<String, Boolean> executeCommandCallback) {
        this.executeCommandCallback = executeCommandCallback;
        this.executedCommands = new CommandHistory(25);
        this.successfulCommands = new ArrayList<>();
    }

    public void updateConfig() {
        for(Consumer<PayNowConfig> callback : this.updateConfigCallbacks) {
            callback.accept(config);
        }
    }

    public void onUpdateConfig(Consumer<PayNowConfig> callback) {
        this.updateConfigCallbacks.add(callback);
    }

    public void fetchPendingCommands(List<String> names, List<UUID> uuids) {
        this.log("Fetching pending commands", Level.INFO);
        String apiToken = this.config.getApiToken();
        if(apiToken == null) {
            this.log("API Token is not set", Level.WARNING);
            return;
        }

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(API_URL)
                .setHeader("Content-Type", "application/json")
                .setHeader("Authorization", "Gameserver " + apiToken)
                .POST(HttpRequest.BodyPublishers.ofString(formatPlayers(names, uuids)))
                .build();

        client.sendAsync(request, responseInfo -> HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8))
                .thenAccept(this::handleResponse);
    }

    public int handleResponse(HttpResponse<String> response) {
        if(response.statusCode() != 200) {
            this.log("Failed to fetch commands: " + response.body(), Level.SEVERE);
            return 0;
        }

        Gson gson = new Gson();
        List<QueuedCommand> commands = gson.fromJson(response.body(), new TypeToken<List<QueuedCommand>>(){}.getType());
        if(commands == null) {
            this.log("Failed to parse commands", Level.SEVERE);
            this.log(response.body(), Level.SEVERE);
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
                this.executedCommands.add(command.getAttemptId());
            } else {
                this.log("Failed to execute command: " + command.getCommand(), Level.WARNING);
            }
        }

        if(this.config.doesLogCommandExecutions()) {
            this.log("Received " + commands.size() + " commands, executed " + this.successfulCommands.size(), Level.INFO);
        }

        this.acknowledgeCommands(this.successfulCommands);

        return this.successfulCommands.size();
    }

    private void acknowledgeCommands(List<String> commandsIds) {
        if(commandsIds.isEmpty()) return;

        String apiToken = this.config.getApiToken();
        if(apiToken == null) {
            this.log("API Token is not set", Level.WARNING);
            return;
        }

        HttpClient client = HttpClient.newHttpClient();
        String formatted = formatCommandIds(commandsIds);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(API_URL)
                .setHeader("Content-Type", "application/json")
                .setHeader("Authorization", "Gameserver " + apiToken)
                .method("DELETE", HttpRequest.BodyPublishers.ofString(formatted))
                .build();

        client.sendAsync(request, responseInfo -> HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8))
                .thenAccept(this::handleAcknowledgeResponse);
    }

    private void handleAcknowledgeResponse(HttpResponse<String> response) {
        if(!PayNowUtils.isSuccess(response.statusCode())) {
            this.log("Failed to acknowledge commands: " + response.body(), Level.SEVERE);
        }
    }

    public void loadPayNowConfig(File configFile) {
        if(!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            try {
                configFile.createNewFile();
            } catch (IOException e) {
                this.log("Failed to create config file, using default values", Level.SEVERE);
                this.config = new PayNowConfig();
                return;
            }
        }

        Gson gson = new Gson();
        try(InputStream is = new FileInputStream(configFile)) {
            String configJson = new String(is.readAllBytes());
            PayNowConfig config = gson.fromJson(configJson, PayNowConfig.class);
            if(config == null) {
                this.log("Failed to parse config, using default values", Level.SEVERE);
                this.config = new PayNowConfig();
            } else {
                this.config = config;
            }
        } catch (IOException e) {
            this.log("Failed to read config file, using default values", Level.SEVERE);
            this.config = new PayNowConfig();
        }
    }

    public void savePayNowConfig(File configFile) {
        if(!configFile.exists()) {
            configFile.mkdirs();
            try {
                configFile.createNewFile();
            } catch (IOException e) {
                this.log("Failed to create config file", Level.SEVERE);
                return;
            }

        }
        Gson gson = new Gson();
        try(OutputStream os = new FileOutputStream(configFile)) {
            os.write(gson.toJson(this.config).getBytes());
        } catch (IOException e) {
            this.log("Failed to save config file", Level.SEVERE);
        }
    }

    private String formatPlayers(List<String> names, List<UUID> uuids) {
        StringBuilder formatted = new StringBuilder();
        formatted.append("{\"customer_names\":[");
        for(int i = 0; i < names.size(); i++) {
            formatted.append("\"").append(names.get(i)).append("\"");
            if(i < names.size() - 1) {
                formatted.append(",");
            }
        }
        formatted.append("], \"minecraft_uuids\":[");
        for(int i = 0; i < uuids.size(); i++) {
            formatted.append("\"").append(uuids.get(i)).append("\"");
            if(i < uuids.size() - 1) {
                formatted.append(",");
            }
        }
        formatted.append("]}");
        this.log("Formatted: " + formatted, Level.INFO);
        return formatted.toString();
    }

    private String formatCommandIds(List<String> commandIds) {
        StringBuilder formatted = new StringBuilder();
        formatted.append("[");
        for(int i = 0; i < commandIds.size(); i++) {
            formatted.append("{\"attempt_id\": \"");
            formatted.append(commandIds.get(i));
            formatted.append("\"}");

            if(i < commandIds.size() - 1) {
                formatted.append(",");
            }
        }

        formatted.append("]");

        return formatted.toString();
    }

    public void setLogCallback(BiConsumer<String, Level> logCallback) {
        this.logCallback = logCallback;
    }

    private void log(String message, Level level) {
        if(this.logCallback != null) this.logCallback.accept(message, level);
    }

    public PayNowConfig getConfig() {
        return config;
    }

    // For testing purposes
    public void setConfig(PayNowConfig config) {
        this.config = config;
    }
}
