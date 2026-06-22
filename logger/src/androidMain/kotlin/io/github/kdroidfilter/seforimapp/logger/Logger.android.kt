package io.github.kdroidfilter.seforimapp.logger

// ponytail: stub — no mobile crash reporter wired yet. Add Sentry-Android/Crashlytics here when needed.
@PublishedApi internal actual fun crashReportingEnabled(): Boolean = false

@PublishedApi internal actual fun reportCrash(
    level: LoggingLevel,
    throwable: Throwable?,
    message: String,
) {
    // no-op stub
}
