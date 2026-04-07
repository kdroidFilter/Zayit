package io.github.kdroidfilter.seforimapp.core.coroutines

import io.github.kdroidfilter.nucleus.energymanager.EnergyManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * A coroutine dispatcher backed by a fixed thread pool where every thread
 * is pinned to efficiency cores via [EnergyManager.enableThreadEfficiencyMode].
 *
 * This provides real parallelism on low-priority cores without competing
 * with [kotlinx.coroutines.Dispatchers.Default] for performance cores.
 *
 * Each thread calls [EnergyManager.enableThreadEfficiencyMode] on first run,
 * which sets OS-level QoS (macOS QOS_CLASS_BACKGROUND, Windows EcoQoS, Linux nice +19).
 */
val EfficiencyCoreDispatcher: CoroutineDispatcher by lazy {
    val threadCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
    val counter = AtomicInteger(0)
    Executors.newFixedThreadPool(threadCount) { runnable ->
        Thread({
            EnergyManager.enableThreadEfficiencyMode()
            runnable.run()
        }, "efficiency-core-${counter.getAndIncrement()}").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }.asCoroutineDispatcher()
}
