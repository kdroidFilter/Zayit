package io.github.kdroidfilter.seforimapp.logger

import io.github.kdroidfilter.seforimapp.logger.LoggingLevel
import io.github.kdroidfilter.seforimapp.logger.debugln
import io.github.kdroidfilter.seforimapp.logger.errorln
import io.github.kdroidfilter.seforimapp.logger.infoln
import io.github.kdroidfilter.seforimapp.logger.isDevEnv
import io.github.kdroidfilter.seforimapp.logger.loggingLevel
import io.github.kdroidfilter.seforimapp.logger.verboseln
import io.github.kdroidfilter.seforimapp.logger.warnln
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoggerTest {
    private val originalOut = System.out
    private val originalIsDevEnv = isDevEnv
    private val originalLoggingLevel = loggingLevel
    private lateinit var outputStream: ByteArrayOutputStream

    @BeforeTest
    fun setup() {
        outputStream = ByteArrayOutputStream()
        System.setOut(PrintStream(outputStream))
        isDevEnv = true
        loggingLevel = LoggingLevel.VERBOSE
    }

    @AfterTest
    fun tearDown() {
        System.setOut(originalOut)
        isDevEnv = originalIsDevEnv
        loggingLevel = originalLoggingLevel
    }

    @Test
    fun `debugln outputs message when level is DEBUG or lower`() {
        loggingLevel = LoggingLevel.DEBUG
        debugln { "Test debug message" }
        assertTrue(outputStream.toString().contains("Test debug message"))
    }

    @Test
    fun `debugln does not output when level is higher than DEBUG`() {
        loggingLevel = LoggingLevel.INFO
        debugln { "Test debug message" }
        assertFalse(outputStream.toString().contains("Test debug message"))
    }

    @Test
    fun `verboseln outputs message when level is VERBOSE`() {
        loggingLevel = LoggingLevel.VERBOSE
        verboseln { "Test verbose message" }
        assertTrue(outputStream.toString().contains("Test verbose message"))
    }

    @Test
    fun `verboseln does not output when level is higher than VERBOSE`() {
        loggingLevel = LoggingLevel.DEBUG
        verboseln { "Test verbose message" }
        assertFalse(outputStream.toString().contains("Test verbose message"))
    }

    @Test
    fun `infoln outputs message when level is INFO or lower`() {
        loggingLevel = LoggingLevel.INFO
        infoln { "Test info message" }
        assertTrue(outputStream.toString().contains("Test info message"))
    }

    @Test
    fun `infoln does not output when level is higher than INFO`() {
        loggingLevel = LoggingLevel.WARN
        infoln { "Test info message" }
        assertFalse(outputStream.toString().contains("Test info message"))
    }

    @Test
    fun `warnln outputs message when level is WARN or lower`() {
        loggingLevel = LoggingLevel.WARN
        warnln { "Test warn message" }
        assertTrue(outputStream.toString().contains("Test warn message"))
    }

    @Test
    fun `warnln does not output when level is higher than WARN`() {
        loggingLevel = LoggingLevel.ERROR
        warnln { "Test warn message" }
        assertFalse(outputStream.toString().contains("Test warn message"))
    }

    @Test
    fun `errorln outputs message when level is ERROR`() {
        loggingLevel = LoggingLevel.ERROR
        errorln { "Test error message" }
        assertTrue(outputStream.toString().contains("Test error message"))
    }

    @Test
    fun `no logging when isDevEnv is false`() {
        isDevEnv = false
        debugln { "Test message" }
        infoln { "Test message" }
        warnln { "Test message" }
        errorln { "Test message" }
        assertEquals("", outputStream.toString())
    }

    @Test
    fun `LoggingLevel VERBOSE has lowest priority`() {
        assertEquals(0, LoggingLevel.VERBOSE.priority)
    }

    @Test
    fun `LoggingLevel DEBUG has priority 1`() {
        assertEquals(1, LoggingLevel.DEBUG.priority)
    }

    @Test
    fun `LoggingLevel INFO has priority 2`() {
        assertEquals(2, LoggingLevel.INFO.priority)
    }

    @Test
    fun `LoggingLevel WARN has priority 3`() {
        assertEquals(3, LoggingLevel.WARN.priority)
    }

    @Test
    fun `LoggingLevel ERROR has highest priority`() {
        assertEquals(4, LoggingLevel.ERROR.priority)
    }

    @Test
    fun `log message includes timestamp`() {
        debugln { "Test" }
        val output = outputStream.toString()
        // Check for timestamp format YYYY-MM-DD HH:MM:SS.mmm
        assertTrue(output.matches(Regex(".*\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}.*")))
    }

    @Test
    fun `log message lambda is only evaluated when needed`() {
        var evaluated = false
        loggingLevel = LoggingLevel.ERROR

        debugln {
            evaluated = true
            "Test"
        }

        assertFalse(evaluated, "Lambda should not be evaluated when log level is too high")
    }

    @Test
    fun `log message lambda is evaluated when logging`() {
        var evaluated = false
        loggingLevel = LoggingLevel.DEBUG

        debugln {
            evaluated = true
            "Test"
        }

        assertTrue(evaluated, "Lambda should be evaluated when logging")
    }
}
