package io.github.kdroidfilter.seforimapp.features.onboarding.diskspace

import io.github.kdroidfilter.nucleus.systeminfo.SystemInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AvailableDiskSpaceUseCase {
    /**
     * Reads available and total disk space via Nucleus SystemInfo.
     * Must be called from a coroutine — dispatched to IO internally.
     */
    suspend fun getDiskSpaceInfo(): DiskSpaceInfo =
        withContext(Dispatchers.IO) {
            val disks = SystemInfo.disks()

            val systemDir =
                disks.firstOrNull {
                    it.mountPoint.contains(System.getProperty("user.home")) ||
                        it.mountPoint == "/" ||
                        it.mountPoint.startsWith("C:")
                } ?: disks.first()

            DiskSpaceInfo(
                availableBytes = systemDir.availableSpace,
                totalBytes = systemDir.totalSpace,
            )
        }

    data class DiskSpaceInfo(
        val availableBytes: Long,
        val totalBytes: Long,
    ) {
        val hasEnoughSpace: Boolean get() = availableBytes >= REQUIRED_SPACE_BYTES
        val remainingAfterInstall: Long get() = availableBytes - REQUIRED_SPACE_BYTES
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
}
