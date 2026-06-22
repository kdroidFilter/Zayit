package io.github.kdroidfilter.seforimapp.logger

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter

private val dateFormatter = NSDateFormatter().apply { dateFormat = "yyyy-MM-dd HH:mm:ss.SSS" }

@PublishedApi internal actual fun loggerTimestamp(): String = dateFormatter.stringFromDate(NSDate())

// ponytail: stub — no mobile crash reporter wired yet.
@PublishedApi internal actual fun crashReportingEnabled(): Boolean = false

@PublishedApi internal actual fun reportCrash(
    level: LoggingLevel,
    throwable: Throwable?,
    message: String,
) {
    // no-op stub
}
