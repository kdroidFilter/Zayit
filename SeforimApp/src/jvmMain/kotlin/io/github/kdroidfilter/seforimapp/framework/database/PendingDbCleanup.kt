package io.github.kdroidfilter.seforimapp.framework.database

import io.github.kdroidfilter.seforimapp.logger.infoln
import io.github.kdroidfilter.seforimapp.logger.warnln
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import java.io.File
import java.nio.file.Files

/**
 * Persists a failed database cleanup across restarts.
 *
 * When [io.github.kdroidfilter.seforimapp.features.database.update.DatabaseCleanupUseCase]
 * cannot delete an old artifact — typically a file still locked by an antivirus, the
 * Windows Search indexer, or a leftover handle — it records the absolute paths here.
 * [runOnce] is invoked at startup, BEFORE the repository opens the database, to retry
 * the deletion once the transient lock is gone. This is what lets the app recover on
 * its own instead of asking the user to delete the old database by hand.
 */
object PendingDbCleanup {
    const val MARKER_NAME = "pending-db-cleanup.txt"

    private fun markerFile(): File? = runCatching { File(File(FileKit.databasesDir.path), MARKER_NAME) }.getOrNull()

    /** Records [files] (merged with any existing entries, de-duplicated) for a retry next launch. */
    fun record(files: List<File>) {
        if (files.isEmpty()) return
        val marker = markerFile() ?: return
        val existing =
            if (marker.exists()) runCatching { marker.readLines() }.getOrDefault(emptyList()) else emptyList()
        val all =
            (existing + files.map { it.absolutePath })
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSortedSet()
        runCatching {
            marker.parentFile?.mkdirs()
            marker.writeText(all.joinToString("\n"))
        }.onFailure { warnln { "[PendingDbCleanup] Could not write marker: ${it.message}" } }
    }

    /**
     * Retries any pending deletions. Cheap stat when no marker is present; never throws.
     * Clears the marker only once every recorded path is gone.
     */
    fun runOnce() {
        val marker = markerFile() ?: return
        if (!marker.exists()) return

        val paths =
            runCatching { marker.readLines() }
                .getOrDefault(emptyList())
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        if (paths.isEmpty()) {
            runCatching { Files.deleteIfExists(marker.toPath()) }
            return
        }

        val remaining = paths.filterNot { deleteRecursively(File(it)) }
        if (remaining.isEmpty()) {
            runCatching { Files.deleteIfExists(marker.toPath()) }
            infoln { "[PendingDbCleanup] All pending database deletions completed." }
        } else {
            runCatching { marker.writeText(remaining.joinToString("\n")) }
            warnln { "[PendingDbCleanup] ${remaining.size} file(s) still locked; will retry next launch." }
        }
    }

    /** Deletes a file or directory tree via NIO. Returns true if nothing remains afterwards. */
    private fun deleteRecursively(target: File): Boolean {
        if (!target.exists()) return true
        if (target.isDirectory) target.listFiles()?.forEach { deleteRecursively(it) }
        return runCatching {
            Files.deleteIfExists(target.toPath())
            !target.exists()
        }.getOrDefault(!target.exists())
    }
}
