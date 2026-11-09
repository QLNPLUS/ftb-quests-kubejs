package dev.questjs.ftbquestskubejs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads {@code config/ftbquestskubejs.json}. Debug logging is disabled by default.
 */
public final class QuestJSConfig {
    private static final Logger LOGGER = LogManager.getLogger("ftbquestskubejs");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "ftbquestskubejs.json";
    private static final String DEBUG_LOGGING = "debugLogging";

    private static volatile boolean debugLogging;

    private QuestJSConfig() {
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        JsonObject json = read(path);
        debugLogging = readBoolean(json, DEBUG_LOGGING, false);

        if (!json.has(DEBUG_LOGGING)) {
            json.addProperty(DEBUG_LOGGING, false);
            write(path, json);
        }

        QuestJSLog.applyExternalTaskLogLevel(debugLogging);
    }

    public static boolean debugLogging() {
        return debugLogging;
    }

    private static boolean readBoolean(JsonObject json, String key, boolean fallback) {
        JsonElement element = json.get(key);
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
            return element.getAsBoolean();
        }
        return fallback;
    }

    private static JsonObject read(Path path) {
        if (!Files.isRegularFile(path)) {
            JsonObject defaults = new JsonObject();
            defaults.addProperty(DEBUG_LOGGING, false);
            write(path, defaults);
            return defaults;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element != null && element.isJsonObject()) {
                return element.getAsJsonObject();
            }
            LOGGER.warn("Config file {} is not a JSON object; using defaults", path);
        } catch (Exception exception) {
            LOGGER.warn("Failed to read config file {}; using defaults", path, exception);
        }
        return new JsonObject();
    }

    private static void write(Path path, JsonObject json) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(json, writer);
            }
        } catch (Exception exception) {
            LOGGER.warn("Failed to write config file {}", path, exception);
        }
    }
}
