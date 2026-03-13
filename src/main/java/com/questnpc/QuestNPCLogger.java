package com.questnpc;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Обёртка логирования для мода QuestNPC.
 * Все сообщения автоматически получают префикс [QuestNPC] для удобной фильтрации в логах.
 */
public final class QuestNPCLogger {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "[QuestNPC] ";

    private QuestNPCLogger() {}

    // --- INFO ---

    public static void info(String message) {
        LOGGER.info(PREFIX + message);
    }

    public static void info(String format, Object... args) {
        LOGGER.info(PREFIX + format, args);
    }

    // --- WARN ---

    public static void warn(String message) {
        LOGGER.warn(PREFIX + message);
    }

    public static void warn(String format, Object... args) {
        LOGGER.warn(PREFIX + format, args);
    }

    // --- ERROR ---

    public static void error(String message) {
        LOGGER.error(PREFIX + message);
    }

    public static void error(String message, Throwable throwable) {
        LOGGER.error(PREFIX + message, throwable);
    }

    public static void error(String format, Object... args) {
        LOGGER.error(PREFIX + format, args);
    }

    // --- DEBUG ---

    public static void debug(String message) {
        LOGGER.debug(PREFIX + message);
    }

    public static void debug(String format, Object... args) {
        LOGGER.debug(PREFIX + format, args);
    }
}
