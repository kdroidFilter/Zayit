package io.github.kdroidfilter.seforimapp.framework.platform

import dev.nucleusframework.core.runtime.Platform
import java.lang.management.ManagementFactory

/**
 * Cached platform information.
 * Values are computed once at class loading time and reused throughout the application lifecycle.
 */
object PlatformInfo {
    /**
     * The current operating system, cached at startup.
     */
    val currentOS: Platform = Platform.Current

    /**
     * True if running on macOS.
     */
    val isMacOS: Boolean = currentOS == Platform.MacOS

    /**
     * True if running on Windows.
     */
    val isWindows: Boolean = currentOS == Platform.Windows

    /**
     * True if running on Linux.
     */
    val isLinux: Boolean = currentOS == Platform.Linux

    /**
     * True on a machine too weak for the heavy 3D widgets (the Earth, the solar system): under 6 GB of RAM (a machine
     * sold with 8 GB passes even with up to 2 GB taken by its GPU; one with 4 or 6 GB doesn't), or Windows 10 (older
     * hardware, weaker GPU drivers). Lazy: the memory query is only paid when a widget asks.
     */
    val isLowEnd: Boolean by lazy {
        val windows10 = isWindows && System.getProperty("os.name").orEmpty() == "Windows 10"
        val totalMemory =
            runCatching {
                (ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean).totalMemorySize
            }.getOrNull()
        windows10 || (totalMemory != null && totalMemory < LOW_END_MEMORY_BYTES)
    }

    private const val LOW_END_MEMORY_BYTES = 6L * 1024 * 1024 * 1024
}
