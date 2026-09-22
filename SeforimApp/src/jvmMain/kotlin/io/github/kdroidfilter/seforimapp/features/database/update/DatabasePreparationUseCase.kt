package io.github.kdroidfilter.seforimapp.features.database.update

import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.onboarding.data.DatabaseInstallLocation
import io.github.kdroidfilter.seforimapp.features.onboarding.diskspace.AvailableDiskSpaceUseCase
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * Single gate that must pass before any database download or extraction:
 *  1. remove the previous database (which frees the disk space it occupied), then
 *  2. verify enough free space remains for a fresh install.
 *
 * Returning a typed [Result] lets callers refuse to start a multi-GB transfer that
 * would otherwise fill the disk and make the app appear to "freeze" mid-download —
 * the exact failure mode users hit when an old database was left in place.
 */
class DatabasePreparationUseCase(
    private val cleanupUseCase: DatabaseCleanupUseCase,
    private val diskSpaceUseCase: AvailableDiskSpaceUseCase,
) {
    sealed interface Result {
        /** Old database removed and enough free space — safe to download/extract. */
        data object Ready : Result

        /** The old database could not be deleted (locked); a restart will retry it. */
        data class CleanupFailed(
            val undeletable: List<String>,
        ) : Result

        /** Not enough free disk space after cleanup. */
        data class InsufficientSpace(
            val availableBytes: Long,
            val requiredBytes: Long,
        ) : Result

        data class DestinationUnavailable(val reason: String) : Result
    }

    suspend fun prepareForInstall(destination: File = DatabaseInstallLocation.currentDirectoryOrDefault()): Result {
        try {
            DatabaseInstallLocation.prepareDirectory(destination)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return Result.DestinationUnavailable(e.message ?: destination.absolutePath)
        }
        // A partially extracted file can exist after a crash. Persist this before
        // deleting the old database so startup routes through recovery until commit.
        AppSettings.setDatabaseInstallInProgress(true)
        when (val cleanup = cleanupUseCase.cleanupDatabaseFiles(destination)) {
            is DatabaseCleanupUseCase.CleanupResult.Incomplete ->
                return Result.CleanupFailed(cleanup.undeletable.map { it.absolutePath })
            is DatabaseCleanupUseCase.CleanupResult.Success -> Unit
        }

        val disk = try {
            diskSpaceUseCase.getDiskSpaceInfo(destination)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return Result.DestinationUnavailable(e.message ?: destination.absolutePath)
        }
        return if (disk.hasEnoughSpace) {
            Result.Ready
        } else {
            Result.InsufficientSpace(
                availableBytes = disk.availableBytes,
                requiredBytes = AvailableDiskSpaceUseCase.REQUIRED_SPACE_BYTES,
            )
        }
    }
}
