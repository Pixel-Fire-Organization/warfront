package org.pixelfire.nationwars.io;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NationWarsLogging#register} is a call-once-per-JVM registration, matching how it is used in
 * a real server, so this class registers it exactly once in {@link BeforeAll} and has every test
 * method check a different facet of the state that leaves behind rather than each re-registering.
 */
class NationWarsLoggingTest
{
    private static final String CAPTURE_CATEGORY = "capture";
    private static final String WAR_CATEGORY = "war";

    @TempDir
    static File tempDir;

    private static String logDir;

    @BeforeAll
    static void registerOnce()
    {
        logDir = new File(tempDir, "nationwars-logs").getPath();
        NationWarsLogging.register(
                16, 3, Level.WARN,
                Map.of(CAPTURE_CATEGORY, Level.DEBUG, WAR_CATEGORY, Level.INFO),
                Level.INFO,
                logDir);
    }

    @Test
    void theFileAppenderIsConfiguredWithTheGivenPathAndRolloverSettings()
    {
        final RollingFileAppender appender = configuration().getAppender("NationWarsRollingFile");

        assertEquals(logDir + "/nationwars.log", appender.getFileName());
        assertTrue(appender.getFilePattern().startsWith(logDir), "roll pattern should live in the same directory");
        assertTrue(appender.getFilePattern().endsWith(".log.gz"), "archives should be gzipped");
    }

    @Test
    void theRootLoggerIsNotAdditive()
    {
        final LoggerConfig nationwars = configuration().getLoggerConfig(NationWarsLogging.LOGGER_NAME);

        assertEquals(NationWarsLogging.LOGGER_NAME, nationwars.getName());
        assertFalse(nationwars.isAdditive(), "nationwars output must not bubble up into logs/latest.log");
    }

    @Test
    void categoryLoggersStartAtTheirConfiguredLevel()
    {
        final LoggerConfig capture = configuration().getLoggerConfig(NationWarsLogging.LOGGER_NAME + "." + CAPTURE_CATEGORY);
        final LoggerConfig war = configuration().getLoggerConfig(NationWarsLogging.LOGGER_NAME + "." + WAR_CATEGORY);

        assertEquals(Level.DEBUG, capture.getLevel());
        assertEquals(Level.INFO, war.getLevel());
    }

    @Test
    void categoryLoggersAreAdditiveSoTheyBubbleUpToTheSharedAppenders()
    {
        final LoggerConfig capture = configuration().getLoggerConfig(NationWarsLogging.LOGGER_NAME + "." + CAPTURE_CATEGORY);

        assertTrue(capture.isAdditive());
        assertTrue(capture.getAppenders().isEmpty(), "a category logger should carry no appenders of its own");
    }

    @Test
    void setCategoryLevelChangesAnExistingCategoryImmediately()
    {
        final boolean changed = NationWarsLogging.setCategoryLevel(CAPTURE_CATEGORY, Level.TRACE);

        assertTrue(changed);
        assertEquals(Level.TRACE, configuration().getLoggerConfig(NationWarsLogging.LOGGER_NAME + "." + CAPTURE_CATEGORY).getLevel());

        // Put it back so this test's side effect doesn't leak into the other tests in this class.
        NationWarsLogging.setCategoryLevel(CAPTURE_CATEGORY, Level.DEBUG);
    }

    @Test
    void setCategoryLevelRejectsAnUnknownCategory()
    {
        final boolean changed = NationWarsLogging.setCategoryLevel("does-not-exist", Level.DEBUG);

        assertFalse(changed);
    }

    @AfterAll
    static void releaseTheLogFileHandle()
    {
        // Otherwise the rolling file appender keeps nationwars.log open for the rest of the JVM's
        // life, and @TempDir's cleanup fails on Windows because the file is still in use.
        final Appender appender = configuration().getAppender("NationWarsRollingFile");
        appender.stop();
    }

    private static Configuration configuration()
    {
        return ((LoggerContext) LogManager.getContext(false)).getConfiguration();
    }
}
