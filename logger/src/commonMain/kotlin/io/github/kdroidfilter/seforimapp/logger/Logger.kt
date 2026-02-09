package io.github.kdroidfilter.seforimapp.logger

import io.sentry.Sentry
import io.sentry.SentryLevel
import java.text.SimpleDateFormat
import java.util.Date

var isDevEnv: Boolean = true
var loggingLevel: LoggingLevel = LoggingLevel.VERBOSE

object SentryConfig {
    @Volatile var sentryEnabled: Boolean = true
    @Volatile var sentryLevel: LoggingLevel = LoggingLevel.ERROR
}

class LoggingLevel(
    val priority: Int,
) {
    companion object {
        val VERBOSE = LoggingLevel(0)
        val DEBUG = LoggingLevel(1)
        val INFO = LoggingLevel(2)
        val WARN = LoggingLevel(3)
        val ERROR = LoggingLevel(4)
    }
}

private const val COLOR_RED = "\u001b[31m"
private const val COLOR_AQUA = "\u001b[36m"
private const val COLOR_LIGHT_GRAY = "\u001b[37m"
private const val COLOR_ORANGE = "\u001b[38;2;255;165;0m"
private const val COLOR_RESET = "\u001b[0m"

private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")

private fun getCurrentTimestamp(): String = dateFormat.format(Date())

private fun shouldLogToConsole(minLevel: LoggingLevel): Boolean =
    isDevEnv && loggingLevel.priority <= minLevel.priority

private fun shouldLogToSentry(minLevel: LoggingLevel): Boolean =
    SentryConfig.sentryEnabled &&
        Sentry.isEnabled() &&
        minLevel.priority >= SentryConfig.sentryLevel.priority

private fun toSentryLevel(level: LoggingLevel): SentryLevel = when {
    level.priority <= LoggingLevel.DEBUG.priority -> SentryLevel.DEBUG
    level.priority == LoggingLevel.INFO.priority -> SentryLevel.INFO
    level.priority == LoggingLevel.WARN.priority -> SentryLevel.WARNING
    else -> SentryLevel.ERROR
}

private fun logAt(
    minLevel: LoggingLevel,
    color: String,
    throwable: Throwable? = null,
    message: () -> String,
) {
    val sendToConsole = shouldLogToConsole(minLevel)
    val sendToSentry = shouldLogToSentry(minLevel)
    if (!sendToConsole && !sendToSentry) return

    val renderedMessage = message()

    if (sendToConsole) {
        println(color + getCurrentTimestamp() + " " + renderedMessage + COLOR_RESET)
        throwable?.printStackTrace()
    }

    if (sendToSentry) {
        runCatching {
            val sentryLevel = toSentryLevel(minLevel)
            Sentry.withScope { scope ->
                scope.level = sentryLevel
                if (throwable != null) {
                    scope.setExtra("logger.message", renderedMessage)
                    Sentry.captureException(throwable)
                } else {
                    Sentry.captureMessage(renderedMessage, sentryLevel)
                }
            }
        }
    }
}

fun verboseln(message: () -> String) {
    logAt(LoggingLevel.VERBOSE, COLOR_LIGHT_GRAY, message = message)
}

fun debugln(message: () -> String) {
    logAt(LoggingLevel.DEBUG, "", message = message)
}

fun infoln(message: () -> String) {
    logAt(LoggingLevel.INFO, COLOR_AQUA, message = message)
}

fun warnln(message: () -> String) {
    logAt(LoggingLevel.WARN, COLOR_ORANGE, message = message)
}

fun warnln(throwable: Throwable, message: () -> String = { throwable.message ?: "Warning" }) {
    logAt(LoggingLevel.WARN, COLOR_ORANGE, throwable, message)
}

fun errorln(message: () -> String) {
    logAt(LoggingLevel.ERROR, COLOR_RED, message = message)
}

fun errorln(throwable: Throwable, message: () -> String = { throwable.message ?: "Error" }) {
    logAt(LoggingLevel.ERROR, COLOR_RED, throwable, message)
}
