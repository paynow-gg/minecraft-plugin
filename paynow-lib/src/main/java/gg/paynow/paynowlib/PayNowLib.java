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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

public class PayNowLib {

    private static final URI API_URL = URI.create("https://api.paynow.gg/v1/delivery/command-queue/");

    private final CommandHistory executedCommands;
    private final List<String> successfulCommands;

    private final Function<String, Boolean> executeCommandCallback;

    private final List<Consumer<PayNowConfig>> updateConfigCallbacks = new ArrayList<>();

    private PayNowConfig config = null;

    private Logger logger = Logger.getLogger("PayNow");

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

    public void fetchPendingCommands(List<String> names) {
        this.logger.info("Fetching pending commands");
        String apiToken = this.config.getApiToken();
        if(apiToken == null) {
            this.logger.severe("API Token is not set");
            return;
        }

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(API_URL)
                .setHeader("Content-Type", "application/json")
                .setHeader("Authorization", "Gameserver " + apiToken)
                .POST(HttpRequest.BodyPublishers.ofString(formatNames(names)))
                .build();

        client.sendAsync(request, responseInfo -> HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8))
                .thenAccept(response -> handleResponse(response, names));
    }

    public void handleResponse(HttpResponse<String> response, List<String> names) {
        if(response.statusCode() != 200) {
            this.logger.severe("Failed to fetch commands: " + response.body());
            return;
        }

        Gson gson = new Gson();
        List<QueuedCommand> commands = gson.fromJson(response.body(), new TypeToken<List<QueuedCommand>>(){}.getType());
        if(commands == null) {
            this.logger.severe("Failed to parse commands");
            this.logger.severe(response.body());
            return;
        }

        processCommands(commands, names);
    }

    private void processCommands(List<QueuedCommand> commands, List<String> names) {
        if(commands.isEmpty()) return;

        this.successfulCommands.clear();
        for (QueuedCommand command : commands) {
            if(this.executedCommands.contains(command.getAttemptId())) continue;

            if(command.isOnlineOnly() && names.stream().noneMatch(s -> s.equalsIgnoreCase(command.getNickname()))) {
                continue;
            }

            boolean success = this.executeCommandCallback.apply(command.getCommand());
            if(success) {
                this.successfulCommands.add(command.getAttemptId());
                this.executedCommands.add(command.getAttemptId());
            } else {
                this.logger.severe("Failed to execute command: " + command.getCommand());
            }
        }

        if(this.config.doesLogCommandExecutions()) {
            this.logger.info("Received " + commands.size() + " commands, executed " + this.successfulCommands.size());
        }

        this.acknowledgeCommands(this.successfulCommands);
    }

    private void acknowledgeCommands(List<String> commandsIds) {
        if(commandsIds.isEmpty()) return;

        String apiToken = this.config.getApiToken();
        if(apiToken == null) {
            this.logger.severe("API Token is not set");
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
            this.logger.severe("Failed to acknowledge commands: " + response.body());
        }
    }

    public void loadPayNowConfig(File configFile) {
        if(!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            try {
                configFile.createNewFile();
            } catch (IOException e) {
                this.getLogger().severe("Failed to create config file, using default values");
                this.config = new PayNowConfig();
                return;
            }
        }

        Gson gson = new Gson();
        try(InputStream is = new FileInputStream(configFile)) {
            String configJson = new String(is.readAllBytes());
            PayNowConfig config = gson.fromJson(configJson, PayNowConfig.class);
            if(config == null) {
                this.getLogger().severe("Failed to parse config, using default values");
                this.config = new PayNowConfig();
            } else {
                this.config = config;
            }
        } catch (IOException e) {
            this.getLogger().severe("Failed to read config file, using default values");
            this.config = new PayNowConfig();
        }
    }

    public void savePayNowConfig(File configFile) {
        if(!configFile.exists()) {
            configFile.mkdirs();
            try {
                configFile.createNewFile();
            } catch (IOException e) {
                this.getLogger().severe("Failed to create config file");
                return;
            }

        }
        Gson gson = new Gson();
        try(OutputStream os = new FileOutputStream(configFile)) {
            os.write(gson.toJson(this.config).getBytes());
        } catch (IOException e) {
            this.getLogger().severe("Failed to save config file");
        }
    }

    private String formatNames(List<String> names) {
        StringBuilder formatted = new StringBuilder();
        formatted.append("{\"steam_ids\":["); // TODO: Change
        for(int i = 0; i < names.size(); i++) {
            formatted.append("\"").append(names.get(i)).append("\"");
            if(i < names.size() - 1) {
                formatted.append(",");
            }
        }
        formatted.append("]}");
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

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    public Logger getLogger() {
        return logger;
    }

    public PayNowConfig getConfig() {
        return config;
    }

    // For testing purposes
    public void setConfig(PayNowConfig config) {
        this.config = config;
    }
}
