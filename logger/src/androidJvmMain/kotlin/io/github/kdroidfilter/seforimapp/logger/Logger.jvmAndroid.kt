package io.github.kdroidfilter.seforimapp.logger

import java.text.SimpleDateFormat
import java.util.Date

// Shared by JVM and Android (both have java.text): timestamp formatting lives here once.
@PublishedApi internal val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")

@PublishedApi internal actual fun loggerTimestamp(): String = dateFormat.format(Date())
