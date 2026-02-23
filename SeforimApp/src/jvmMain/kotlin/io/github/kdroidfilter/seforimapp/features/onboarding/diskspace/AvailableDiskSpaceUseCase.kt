package io.github.kdroidfilter.seforimapp.features.onboarding.diskspace

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import oshi.SystemInfo
import oshi.software.os.OSFileStore

class AvailableDiskSpaceUseCase {
    /**
     * Reads available and total disk space in a single blocking OSHI call.
     * Must be called from a coroutine — dispatched to IO internally.
     */
    suspend fun getDiskSpaceInfo(): DiskSpaceInfo =
        withContext(Dispatchers.IO) {
            val si = SystemInfo()
            val fileStores: List<OSFileStore> = si.operatingSystem.fileSystem.fileStores

            val systemDir =
                fileStores.firstOrNull {
                    it.mount.contains(System.getProperty("user.home")) ||
                        it.mount == "/" ||
                        it.mount.startsWith("C:")
                } ?: fileStores.first()

            DiskSpaceInfo(
                availableBytes = systemDir.usableSpace,
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
