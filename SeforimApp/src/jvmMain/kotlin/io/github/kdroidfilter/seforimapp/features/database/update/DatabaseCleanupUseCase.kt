package io.github.kdroidfilter.seforimapp.features.database.update

import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.logger.debugln
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DatabaseCleanupUseCase {
    suspend fun cleanupDatabaseFiles(): Unit =
        withContext(Dispatchers.IO) {
            try {
                // Get databases directory
                val dbDir = File(FileKit.databasesDir.path)
                if (!dbDir.exists()) {
                    return@withContext // Nothing to clean
                }

                // Get current database path to understand naming pattern
                val currentDbPath = AppSettings.getDatabasePath()
                val currentDbFile = currentDbPath?.let { File(it) }

                // Clear database path in settings first
                AppSettings.setDatabasePath(null)

                // List all files in databases directory
                val files = dbDir.listFiles() ?: emptyArray()

                for (file in files) {
                    val fileName = file.name.lowercase()

                    // Delete database files (.db)
                    if (fileName.endsWith(".db")) {
                        runCatching { file.delete() }
                    }

                    // Delete Lucene index directories (.lucene, .lookup.lucene)
                    if (fileName.endsWith(".lucene") || fileName.contains(".lookup.lucene")) {
                        runCatching { deleteDirectory(file) }
                    }

                    // Delete version files (release_info.txt)
                    if (fileName == "release_info.txt") {
                        runCatching { file.delete() }
                    }

                    // Delete catalog files (.proto)
                    if (fileName.endsWith(".proto") || fileName == "catalog.proto") {
                        runCatching { file.delete() }
                    }

                    // Delete temporary/download files
                    if (fileName.endsWith(".tar.zst") ||
                        fileName.endsWith(".part01") ||
                        fileName.endsWith(".part02") ||
                        fileName.endsWith(".zst") ||
                        fileName.endsWith(".tmp")
                    ) {
                        runCatching { file.delete() }
                    }

                    // If we know the current database file, also clean related files
                    if (currentDbFile != null && file.absolutePath.startsWith(currentDbFile.absolutePath)) {
                        runCatching { file.delete() }
                    }
                }
            } catch (e: Exception) {
                // Log error but don't fail the cleanup process
                debugln { "Warning: Error during database cleanup: ${e.message}" }
            }
        }

    private fun deleteDirectory(directory: File) {
        if (directory.isDirectory) {
            directory.listFiles()?.forEach { child ->
                deleteDirectory(child)
            }
        }
        directory.delete()
    }
}
