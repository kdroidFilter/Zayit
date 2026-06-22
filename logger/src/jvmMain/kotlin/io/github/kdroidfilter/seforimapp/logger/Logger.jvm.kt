package io.github.kdroidfilter.seforimapp.logger

import io.sentry.Sentry
import io.sentry.SentryLevel

@PublishedApi internal actual fun crashReportingEnabled(): Boolean = Sentry.isEnabled()

@PublishedApi internal actual fun reportCrash(
    level: LoggingLevel,
    throwable: Throwable?,
    message: String,
) {
    runCatching {
        val sentryLevel = toSentryLevel(level)
        Sentry.withScope { scope ->
            scope.level = sentryLevel
            if (throwable != null) {
                scope.setExtra("logger.message", message)
                Sentry.captureException(throwable)
            } else {
                Sentry.captureMessage(message, sentryLevel)
            }
        }
    }
}

private fun toSentryLevel(level: LoggingLevel): SentryLevel =
    when {
        level.priority <= LoggingLevel.DEBUG.priority -> SentryLevel.DEBUG
        level.priority == LoggingLevel.INFO.priority -> SentryLevel.INFO
        level.priority == LoggingLevel.WARN.priority -> SentryLevel.WARNING
        else -> SentryLevel.ERROR
    }
