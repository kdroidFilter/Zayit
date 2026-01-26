package io.github.kdroidfilter.seforimapp.logger

import io.github.kdroidfilter.seforimapp.logger.SilenceLogs
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SilenceLogsTest {
    @Test
    fun `SilenceLogs is object singleton`() {
        val instance1 = SilenceLogs
        val instance2 = SilenceLogs
        assertEquals(instance1, instance2)
    }

    @Test
    fun `everything function exists and is callable`() {
        // Just verify the function exists and can be called
        // We don't actually call it with hardMute to avoid affecting other tests
        assertNotNull(SilenceLogs::everything)
    }

    @Test
    fun `everything sets JUL root logger to OFF`() {
        // Save original level
        val rootLogger = Logger.getLogger("")
        val originalLevel = rootLogger.level

        try {
            SilenceLogs.everything()
            assertEquals(Level.OFF, rootLogger.level)
        } finally {
            // Restore original level
            rootLogger.level = originalLevel
        }
    }

    @Test
    fun `everything with default parameters does not mute stdout`() {
        // Save original
        val originalOut = System.out

        try {
            SilenceLogs.everything(hardMuteStdout = false, hardMuteStderr = false)
            // If stdout wasn't muted, we should still have the original
            assertEquals(originalOut, System.out)
        } finally {
            // Ensure restoration
            System.setOut(originalOut)
        }
    }

    @Test
    fun `everything sets SLF4J property`() {
        SilenceLogs.everything()
        assertEquals("off", System.getProperty("org.slf4j.simpleLogger.defaultLogLevel"))
    }

    @Test
    fun `everything sets logging level root property`() {
        SilenceLogs.everything()
        assertEquals("OFF", System.getProperty("logging.level.root"))
    }
}
