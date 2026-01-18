package io.github.kdroidfilter.seforimapp.framework.platform

import io.github.kdroidfilter.platformtools.OperatingSystem
import io.github.kdroidfilter.platformtools.getOperatingSystem

/**
 * Cached platform information.
 * Values are computed once at class loading time and reused throughout the application lifecycle.
 */
object PlatformInfo {
    /**
     * The current operating system, cached at startup.
     */
    val currentOS: OperatingSystem = getOperatingSystem()

    /**
     * True if running on macOS.
     */
    val isMacOS: Boolean = currentOS == OperatingSystem.MACOS

    /**
     * True if running on Windows.
     */
    val isWindows: Boolean = currentOS == OperatingSystem.WINDOWS

    /**
     * True if running on Linux.
     */
    val isLinux: Boolean = currentOS == OperatingSystem.LINUX
}
