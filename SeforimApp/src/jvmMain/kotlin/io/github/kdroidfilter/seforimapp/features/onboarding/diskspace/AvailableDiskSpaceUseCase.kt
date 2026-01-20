package io.github.kdroidfilter.seforimapp.features.onboarding.diskspace

import oshi.SystemInfo
import oshi.software.os.OSFileStore
import oshi.software.os.OperatingSystem

class AvailableDiskSpaceUseCase {
    /** Returns the available space on the main disk in bytes using OSHI. */
    fun getAvailableDiskSpace(): Long {
        val si = SystemInfo()
        val os: OperatingSystem = si.operatingSystem
        val fileStores: List<OSFileStore> = os.fileSystem.fileStores

        // Heuristic: main disk = one that contains the system directory
        val systemDir =
            os.fileSystem.fileStores.firstOrNull {
                it.mount.contains(System.getProperty("user.home")) ||
                    it.mount == "/" ||
                    it.mount.startsWith("C:")
            } ?: fileStores.first()

        return systemDir.usableSpace
    }

    /** Returns the total capacity in bytes of the main disk using OSHI. */
    fun getTotalDiskSpace(): Long {
        val si = SystemInfo()
        val os: OperatingSystem = si.operatingSystem
        val fileStores: List<OSFileStore> = os.fileSystem.fileStores

        val systemDir =
            os.fileSystem.fileStores.firstOrNull {
                it.mount.contains(System.getProperty("user.home")) ||
                    it.mount == "/" ||
                    it.mount.startsWith("C:")
            } ?: fileStores.first()

        return systemDir.totalSpace
    }

    companion object {
        /** Total space required during installation (includes temporary files). */
        const val REQUIRED_SPACE_GB = 11L

        /** Temporary space needed only during installation (will be freed after). */
        const val TEMPORARY_SPACE_GB = 2.5

        /** Final space after installation completes. */
        const val FINAL_SPACE_GB = 8.5

        val REQUIRED_SPACE_BYTES = REQUIRED_SPACE_GB * 1024 * 1024 * 1024
    }

    /** Returns true if there is at least 11 GB free on the main disk. */
    fun hasEnoughSpace(): Boolean {
        val freeBytes = getAvailableDiskSpace()
        return freeBytes >= REQUIRED_SPACE_BYTES
    }

    /**
     * Returns how many bytes remain *after subtracting the required space*.
     * If result is negative, it means not enough space is available.
     */
    fun getRemainingSpaceAfterInstall(): Long {
        val freeBytes = getAvailableDiskSpace()
        return freeBytes - REQUIRED_SPACE_BYTES
    }
}
