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
        @JvmField val VERBOSE = LoggingLevel(0)
        @JvmField val DEBUG = LoggingLevel(1)
        @JvmField val INFO = LoggingLevel(2)
        @JvmField val WARN = LoggingLevel(3)
        @JvmField val ERROR = LoggingLevel(4)
    }
}

@PublishedApi internal const val COLOR_RED = "\u001b[31m"
@PublishedApi internal const val COLOR_AQUA = "\u001b[36m"
@PublishedApi internal const val COLOR_LIGHT_GRAY = "\u001b[37m"
@PublishedApi internal const val COLOR_ORANGE = "\u001b[38;2;255;165;0m"
@PublishedApi internal const val COLOR_RESET = "\u001b[0m"

@PublishedApi internal val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")

@PublishedApi internal fun getCurrentTimestamp(): String = dateFormat.format(Date())

@PublishedApi internal fun shouldLogToConsole(minLevel: LoggingLevel): Boolean = isDevEnv && loggingLevel.priority <= minLevel.priority

@PublishedApi internal fun shouldLogToSentry(minLevel: LoggingLevel): Boolean =
    SentryConfig.sentryEnabled &&
        Sentry.isEnabled() &&
        minLevel.priority >= SentryConfig.sentryLevel.priority

@PublishedApi internal fun toSentryLevel(level: LoggingLevel): SentryLevel =
    when {
        level.priority <= LoggingLevel.DEBUG.priority -> SentryLevel.DEBUG
        level.priority == LoggingLevel.INFO.priority -> SentryLevel.INFO
        level.priority == LoggingLevel.WARN.priority -> SentryLevel.WARNING
        else -> SentryLevel.ERROR
    }

@PublishedApi internal inline fun logAt(
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

inline fun verboseln(message: () -> String) {
    logAt(LoggingLevel.VERBOSE, COLOR_LIGHT_GRAY, message = message)
}

inline fun debugln(message: () -> String) {
    logAt(LoggingLevel.DEBUG, "", message = message)
}

inline fun infoln(message: () -> String) {
    logAt(LoggingLevel.INFO, COLOR_AQUA, message = message)
}

inline fun warnln(message: () -> String) {
    logAt(LoggingLevel.WARN, COLOR_ORANGE, message = message)
}

inline fun warnln(
    throwable: Throwable,
    message: () -> String = { throwable.message ?: "Warning" },
) {
    logAt(LoggingLevel.WARN, COLOR_ORANGE, throwable, message)
}

inline fun errorln(message: () -> String) {
    logAt(LoggingLevel.ERROR, COLOR_RED, message = message)
}

inline fun errorln(
    throwable: Throwable,
    message: () -> String = { throwable.message ?: "Error" },
) {
    logAt(LoggingLevel.ERROR, COLOR_RED, throwable, message)
}
