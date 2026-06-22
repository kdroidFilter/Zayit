package io.github.kdroidfilter.seforim.tabs

import kotlin.time.Clock

/**
 * Wall-clock milliseconds since epoch, used to version Home tabs for cache-busting.
 *
 * Uses the multiplatform [Clock] (kotlinx-datetime's clock graduated into kotlin.time in Kotlin 2.3),
 * so no expect/actual or platform source set is needed.
 */
fun nowEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()
