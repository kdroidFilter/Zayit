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
        val systemDir = os.fileSystem.fileStores.firstOrNull {
            it.mount.contains(System.getProperty("user.home")) ||
                    it.mount == "/" || it.mount.startsWith("C:")
        } ?: fileStores.first()

        return systemDir.usableSpace
    }

    /** Returns true if there is at least 15 GB free on the main disk. */
    fun hasAtLeast15GBFree(): Boolean {
        val freeBytes = getAvailableDiskSpace()
        val fifteenGB = 15L * 1024 * 1024 * 1024
        return freeBytes >= fifteenGB
    }

    /**
     * Returns how many bytes remain *after subtracting 15 GB*.
     * If result is negative, it means less than 15 GB are available.
     */
    fun getRemainingSpaceAfter15GB(): Long {
        val freeBytes = getAvailableDiskSpace()
        val fifteenGB = 15L * 1024 * 1024 * 1024
        return freeBytes - fifteenGB
    }
}
