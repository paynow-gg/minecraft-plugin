package gg.paynow.paynowlib;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public enum PayNowLang {

    TOKEN_UPDATED("link.token-updated", "<green>API token updated"),
    NO_PERMISSION("command.no-permission", "<red>You do not have permission to use this command"),
    INVALID_ARGUMENTS("command.invalid-arguments", "<red>Invalid arguments"),
    PLAYER_NOT_ONLINE("checkout.player-not-online", "<red><player> is not online"),
    CHECKOUT_LINK("checkout.link", "<green><u><link>Click here to complete your purchase</link></u>"),
    CHECKOUT_LINK_BEDROCK("checkout.link-bedrock", "<green>Open this link in your browser to complete your purchase: <url>"),
    CHECKOUT_SENT("checkout.sent", "<green>Checkout link sent to <player>"),
    CHECKOUT_PLAYER_LEFT("checkout.player-left", "<red><player> went offline before the checkout link was ready"),
    CHECKOUT_FAILED("checkout.failed", "<red>Failed to create checkout: <error>");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final String key;
    private final String defaultTemplate;
    private String template;

    PayNowLang(String key, String defaultTemplate) {
        this.key = key;
        this.defaultTemplate = defaultTemplate;
        this.template = defaultTemplate;
    }

    public Component get(TagResolver... resolvers) {
        return MiniMessage.miniMessage().deserialize(this.template, resolvers);
    }

    public Component get(String placeholder, String value) {
        return this.get(Placeholder.unparsed(placeholder, value));
    }

    public static void load(File langFile, Consumer<String> warn) {
        Map<String, String> fileTemplates = read(langFile, warn);
        boolean missingKeys = fileTemplates == null;

        Map<String, String> templates = new LinkedHashMap<>();
        for(PayNowLang lang : values()) {
            String template = fileTemplates == null ? null : fileTemplates.get(lang.key);
            if(template == null) {
                template = lang.defaultTemplate;
                missingKeys = true;
            }
            lang.template = template;
            templates.put(lang.key, template);
        }

        if(missingKeys) {
            write(langFile, templates, warn);
        }
    }

    private static Map<String, String> read(File langFile, Consumer<String> warn) {
        if(!langFile.exists()) return null;

        try(Reader reader = Files.newBufferedReader(langFile.toPath(), StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, new TypeToken<Map<String, String>>(){}.getType());
        } catch (IOException | JsonParseException e) {
            warn.accept("Failed to read " + langFile.getName() + ", using default messages: " + e.getMessage());
            return null;
        }
    }

    private static void write(File langFile, Map<String, String> templates, Consumer<String> warn) {
        langFile.getParentFile().mkdirs();
        try(Writer writer = Files.newBufferedWriter(langFile.toPath(), StandardCharsets.UTF_8)) {
            GSON.toJson(templates, writer);
        } catch (IOException e) {
            warn.accept("Failed to save " + langFile.getName());
        }
    }

}
