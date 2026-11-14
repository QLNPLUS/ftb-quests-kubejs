package dev.questjs.ftbquestskubejs;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

public final class QuestJSLog {
    private static final Logger LOGGER = LogManager.getLogger("ftbquestskubejs");
    private static final String EXTERNAL_TASK_LOGGER = "ftbquestskubejs.external_task";

    private QuestJSLog() {
    }

    public static void debug(String message) {
        if (QuestJSConfig.debugLogging()) {
            LOGGER.info("[debug] {}", message);
        }
    }

    public static void applyExternalTaskLogLevel(boolean enabled) {
        try {
            Configurator.setLevel(EXTERNAL_TASK_LOGGER, enabled ? Level.INFO : Level.WARN);
        } catch (Throwable throwable) {
            LOGGER.warn("Failed to configure the {} logger", EXTERNAL_TASK_LOGGER, throwable);
        }
    }
}
