package io.github.kdroidfilter.seforimapp.framework.platform

import dev.nucleusframework.core.runtime.Platform
import dev.nucleusframework.systeminfo.SystemInfo
import dev.nucleusframework.systeminfo.model.RenderTier

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
     * sold with 8 GB passes even with up to 2 GB taken by its GPU; one with 4 or 6 GB doesn't), or a GPU unfit for 3D
     * (a software rasterizer, or one below Direct3D 11 / GL ES 3, where Filament crashes or blacks out the window,
     * #728). Lazy, and warmed off the UI thread at startup ([warmUp]): the GPU probe loads the driver.
     */
    val isLowEnd: Boolean by lazy {
        val totalMemory = runCatching { SystemInfo.memoryInfo()?.totalMemory }.getOrNull()
        val renderTier = runCatching { SystemInfo.renderTier() }.getOrDefault(RenderTier.UNKNOWN)
        (totalMemory != null && totalMemory < LOW_END_MEMORY_BYTES) ||
            renderTier == RenderTier.SOFTWARE ||
            renderTier == RenderTier.LEGACY
    }

    /** Computes [isLowEnd] on a thread of its own, so the Home's first composition doesn't wait for the GPU probe. */
    fun warmUp() {
        Thread({ isLowEnd }, "platform-info-warmup").apply { isDaemon = true }.start()
    }

    private const val LOW_END_MEMORY_BYTES = 6L * 1024 * 1024 * 1024
}
