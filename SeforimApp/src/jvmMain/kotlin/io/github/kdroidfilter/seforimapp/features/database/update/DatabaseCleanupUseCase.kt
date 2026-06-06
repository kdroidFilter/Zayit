package io.github.kdroidfilter.seforimapp.features.database.update

import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.database.PendingDbCleanup
import io.github.kdroidfilter.seforimapp.framework.database.resetDatabasePathCache
import io.github.kdroidfilter.seforimapp.logger.debugln
import io.github.kdroidfilter.seforimapp.logger.warnln
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import kotlin.coroutines.cancellation.CancellationException

/**
 * Removes a previously installed database and all of its companion artifacts
 * (Lucene indexes, catalog, lexical dictionary, version stamp, download leftovers)
 * before a fresh install.
 *
 * Deletion is authoritative: it targets the directory of the *actual* configured
 * database ([AppSettings.getDatabasePath]) as well as the default databases
 * directory, so a database installed by an older build in a non-default location is
 * still removed. NIO [Files.deleteIfExists] is used so a locked file — common on
 * Windows when an antivirus, the Windows Search indexer, or a leftover handle holds
 * it — surfaces an exception we can log instead of the silent `false` returned by
 * [File.delete]. Files that cannot be removed are reported via [CleanupResult.Incomplete]
 * and recorded for a retry at the next launch via [PendingDbCleanup], so the caller
 * can refuse to start a multi-GB download that would otherwise fill the disk.
 */
class DatabaseCleanupUseCase {
    sealed interface CleanupResult {
        /** Every known artifact was removed (or was already absent). */
        data class Success(
            val freedBytes: Long,
        ) : CleanupResult

        /** Some files could not be deleted (e.g. locked by another process). */
        data class Incomplete(
            val undeletable: List<File>,
        ) : CleanupResult
    }

    suspend fun cleanupDatabaseFiles(): CleanupResult =
        withContext(Dispatchers.IO) {
            val currentDbPath = AppSettings.getDatabasePath()

            // The old database is going away: forget the recorded path and the cached
            // resolution so the app re-resolves the freshly installed location later.
            AppSettings.setDatabasePath(null)
            resetDatabasePathCache()

            // Candidate directories: the real DB directory (may be non-default for
            // legacy installs) plus the current default databases directory.
            val dirs = LinkedHashSet<File>()
            currentDbPath?.let { File(it).parentFile?.let(dirs::add) }
            runCatching { File(FileKit.databasesDir.path) }.getOrNull()?.let(dirs::add)

            var freed = 0L
            val undeletable = mutableListOf<File>()

            try {
                for (dir in dirs) {
                    val files = dir.takeIf { it.exists() }?.listFiles() ?: continue
                    for (file in files) {
                        if (!isDatabaseArtifact(file)) continue
                        val size = sizeOf(file)
                        if (deleteRecursively(file)) {
                            freed += size
                        } else {
                            undeletable += file
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                warnln { "[DatabaseCleanup] Unexpected error during cleanup: ${e.message}" }
            }

            if (undeletable.isEmpty()) {
                debugln { "[DatabaseCleanup] Removed previous database artifacts, freed ${freed / (1024 * 1024)} MB" }
                CleanupResult.Success(freed)
            } else {
                warnln {
                    "[DatabaseCleanup] ${undeletable.size} file(s) could not be deleted (locked?): " +
                        undeletable.joinToString { it.name }
                }
                PendingDbCleanup.record(undeletable)
                CleanupResult.Incomplete(undeletable)
            }
        }

    /** True for files this app installs alongside the database and must remove on reinstall. */
    private fun isDatabaseArtifact(file: File): Boolean {
        val name = file.name.lowercase()
        return name.endsWith(".db") ||
            // seforim.db, lexical.db
            name.endsWith(".db-wal") ||
            name.endsWith(".db-shm") ||
            // SQLite WAL/SHM sidecars
            name.endsWith(".lucene") ||
            name.contains(".lookup.lucene") ||
            // Lucene index dirs
            name == "catalog.pb" ||
            // precomputed catalog (previously mis-targeted as ".proto")
            name == "release_info.txt" ||
            // version stamp
            name == "delta-cache" ||
            // delta updater work dir
            name == PendingDbCleanup.MARKER_NAME ||
            // stale pending-cleanup marker
            // download / extraction leftovers
            name.endsWith(".tar.zst") ||
            name.endsWith(".part01") ||
            name.endsWith(".part02") ||
            name.endsWith(".zst") ||
            name.endsWith(".tmp")
    }

    private fun sizeOf(file: File): Long =
        runCatching {
            if (file.isDirectory) {
                file.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
            } else {
                file.length()
            }
        }.getOrDefault(0L)

    /**
     * Deletes a file or directory tree using NIO so failures throw (and are logged)
     * instead of silently returning false. Returns true if nothing remains afterwards.
     */
    private fun deleteRecursively(target: File): Boolean {
        if (target.isDirectory) {
            target.listFiles()?.forEach { deleteRecursively(it) }
        }
        return try {
            Files.deleteIfExists(target.toPath())
            !target.exists()
        } catch (e: Exception) {
            warnln { "[DatabaseCleanup] Could not delete ${target.absolutePath}: ${e.javaClass.simpleName} ${e.message}" }
            !target.exists()
        }
    }
}
