package io.github.kdroidfilter.seforimapp.features.onboarding.diskspace

import io.github.kdroidfilter.seforimapp.features.onboarding.data.DatabaseInstallLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

class AvailableDiskSpaceUseCase {
    /**
     * Reads available and total disk space from the filesystem that holds the install destination.
     * Must be called from a coroutine — dispatched to IO internally.
     */
    suspend fun getDiskSpaceInfo(directory: File): DiskSpaceInfo =
        withContext(Dispatchers.IO) {
            require(directory.isDirectory) { "Database location is unavailable: $directory" }
            val store = Files.getFileStore(directory.toPath())
            DiskSpaceInfo(
                availableBytes = store.usableSpace,
                totalBytes = store.totalSpace,
            )
        }

    suspend fun getDiskSpaceInfo(): DiskSpaceInfo =
        getDiskSpaceInfo(
            generateSequence(DatabaseInstallLocation.defaultDirectory()) {
                it.parentFile
            }.first { it.isDirectory },
        )

    data class DiskSpaceInfo(
        val availableBytes: Long,
        val totalBytes: Long,
    ) {
        val hasEnoughSpace: Boolean get() = availableBytes >= REQUIRED_SPACE_BYTES
        val remainingAfterInstall: Long get() = availableBytes - REQUIRED_SPACE_BYTES
    }

    companion object {
        /** Total space required during installation (includes temporary files). */
        const val REQUIRED_SPACE_GB = 10L

        /** Temporary space needed only during installation (will be freed after). */
        const val TEMPORARY_SPACE_GB = 2.5

        /** Final space after installation completes. */
        const val FINAL_SPACE_GB = 7.5

        val REQUIRED_SPACE_BYTES = REQUIRED_SPACE_GB * 1024 * 1024 * 1024
    }
}
