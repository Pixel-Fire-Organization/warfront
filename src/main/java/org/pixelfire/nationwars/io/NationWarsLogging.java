package org.pixelfire.nationwars.io;

import com.mojang.logging.LogUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.TriggeringPolicy;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.slf4j.Logger;

import java.util.Map;

/**
 * Sets up the mod's own rolling diagnostic log file, entirely in code (no {@code log4j2.xml} edit),
 * separate from the governance-oriented audit log and from vanilla's {@code logs/latest.log}.
 *
 * <p>The {@code nationwars} logger has {@code additivity=false}, so its output (and that of any
 * {@code nationwars.<category>} child, e.g. {@code nationwars.capture}) never bubbles up into
 * {@code logs/latest.log} or {@code logs/debug.log} — only into {@code logs/nationwars/nationwars.log}
 * and, at or above {@code logToServerConsole}, the main console. Category loggers carry no appenders
 * of their own, only a level; a passing event bubbles up to {@code nationwars} for the actual writing,
 * so changing one category's level at runtime ({@link #setCategoryLevel}) can't duplicate or drop
 * where its output goes.
 */
public final class NationWarsLogging
{
    public static final String LOGGER_NAME = "nationwars";

    private static final Logger BOOTSTRAP_LOGGER = LogUtils.getLogger();
    private static final String APPENDER_NAME = "NationWarsRollingFile";
    private static final String CONSOLE_APPENDER_NAME = "Console";
    private static final String DEFAULT_LOG_DIR = "logs/nationwars";
    private static final String PATTERN = "[%d{HH:mm:ss}] [%t/%level] %c{1} - %msg%n";

    private static volatile boolean registered;

    private NationWarsLogging()
    {
    }

    /**
     * Creates the rolling file appender and the {@code nationwars} logger hierarchy. Safe to call
     * only once per JVM (a second call is a no-op with a warning) — this is meant to run once, early,
     * during mod setup.
     *
     * @param logFileSizeMb   size, in megabytes, at which the log file rolls over
     * @param logFileHistory  number of rolled-over archives to keep
     * @param consoleLevel    minimum level from the {@code nationwars} logger that is also echoed to
     *                        the main server console
     * @param categoryLevels  per-category level, keyed by category name (e.g. {@code "capture"});
     *                        each becomes a {@code nationwars.<category>} child logger
     * @param defaultLevel    level for the {@code nationwars} logger itself, used by anything logged
     *                        directly against it rather than a specific category
     */
    public static void register(final int logFileSizeMb, final int logFileHistory, final Level consoleLevel,
            final Map<String, Level> categoryLevels, final Level defaultLevel)
    {
        register(logFileSizeMb, logFileHistory, consoleLevel, categoryLevels, defaultLevel, DEFAULT_LOG_DIR);
    }

    /**
     * Package-visible so tests can point the log directory somewhere disposable instead of the real
     * {@value #DEFAULT_LOG_DIR}; production code should always use the four-argument overload.
     */
    static synchronized void register(final int logFileSizeMb, final int logFileHistory, final Level consoleLevel,
            final Map<String, Level> categoryLevels, final Level defaultLevel, final String logDir)
    {
        if (registered)
        {
            BOOTSTRAP_LOGGER.warn("nationwars diagnostic logging is already registered; ignoring a second registration attempt");
            return;
        }

        final LoggerContext context = (LoggerContext) LogManager.getContext(false);
        final Configuration config = context.getConfiguration();

        final RollingFileAppender fileAppender = createFileAppender(config, logFileSizeMb, logFileHistory, logDir);
        fileAppender.start();
        config.addAppender(fileAppender);

        final Appender consoleAppender = config.getAppender(CONSOLE_APPENDER_NAME);
        registerRootLogger(config, fileAppender, consoleAppender, consoleLevel, defaultLevel);

        for (final Map.Entry<String, Level> entry : categoryLevels.entrySet())
        {
            registerCategoryLogger(config, entry.getKey(), entry.getValue());
        }

        context.updateLoggers();
        registered = true;

        BOOTSTRAP_LOGGER.info("nationwars diagnostic logging registered: file={}/nationwars.log, rollSizeMb={}, history={}, consoleLevel={}, categories={}",
                logDir, logFileSizeMb, logFileHistory, consoleLevel, categoryLevels.keySet());
        LogManager.getLogger(LOGGER_NAME).info("diagnostic log file ready at {}/nationwars.log", logDir);
    }

    private static RollingFileAppender createFileAppender(final Configuration config, final int logFileSizeMb, final int logFileHistory,
            final String logDir)
    {
        final PatternLayout layout = PatternLayout.newBuilder()
                .withConfiguration(config)
                .withPattern(PATTERN)
                .build();

        final TriggeringPolicy sizePolicy = SizeBasedTriggeringPolicy.createPolicy(logFileSizeMb + "MB");
        final DefaultRolloverStrategy rolloverStrategy = DefaultRolloverStrategy.newBuilder()
                .withMax(String.valueOf(Math.max(logFileHistory, 1)))
                .withConfig(config)
                .build();

        return RollingFileAppender.newBuilder()
                .setName(APPENDER_NAME)
                .withFileName(logDir + "/nationwars.log")
                .withFilePattern(logDir + "/nationwars-%d{yyyy-MM-dd}-%i.log.gz")
                .setLayout(layout)
                .withPolicy(sizePolicy)
                .withStrategy(rolloverStrategy)
                .setConfiguration(config)
                .build();
    }

    private static void registerRootLogger(final Configuration config, final Appender fileAppender, final Appender consoleAppender,
            final Level consoleLevel, final Level defaultLevel)
    {
        final AppenderRef fileRef = AppenderRef.createAppenderRef(APPENDER_NAME, null, null);
        final AppenderRef[] refs = consoleAppender != null
                ? new AppenderRef[]{ fileRef, AppenderRef.createAppenderRef(CONSOLE_APPENDER_NAME, consoleLevel, null) }
                : new AppenderRef[]{ fileRef };

        final LoggerConfig loggerConfig = LoggerConfig.newBuilder()
                .withAdditivity(false)
                .withLevel(defaultLevel)
                .withLoggerName(LOGGER_NAME)
                .withRefs(refs)
                .withConfig(config)
                .build();
        loggerConfig.addAppender(fileAppender, null, null);
        if (consoleAppender != null)
        {
            loggerConfig.addAppender(consoleAppender, consoleLevel, null);
        }
        config.addLogger(LOGGER_NAME, loggerConfig);
    }

    private static void registerCategoryLogger(final Configuration config, final String category, final Level level)
    {
        final LoggerConfig loggerConfig = LoggerConfig.newBuilder()
                .withAdditivity(true)
                .withLevel(level)
                .withLoggerName(categoryLoggerName(category))
                .withRefs(new AppenderRef[0])
                .withConfig(config)
                .build();
        config.addLogger(categoryLoggerName(category), loggerConfig);
    }

    /**
     * Changes one category's level immediately, with no restart or reload required. Returns false
     * (and changes nothing) if no such category was registered.
     */
    public static boolean setCategoryLevel(final String category, final Level level)
    {
        final LoggerContext context = (LoggerContext) LogManager.getContext(false);
        final Configuration config = context.getConfiguration();
        final String name = categoryLoggerName(category);

        final LoggerConfig loggerConfig = config.getLoggerConfig(name);
        if (!name.equals(loggerConfig.getName()))
        {
            // getLoggerConfig() falls back to the nearest registered ancestor when there's no exact
            // match; that means this category was never registered, not that we found it.
            return false;
        }

        loggerConfig.setLevel(level);
        context.updateLoggers();
        return true;
    }

    private static String categoryLoggerName(final String category)
    {
        return LOGGER_NAME + "." + category;
    }
}
