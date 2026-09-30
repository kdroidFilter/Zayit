package io.github.kdroidfilter.seforimapp.framework.database

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.kdroidfilter.seforimapp.logger.infoln
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import java.io.File

private const val DEFAULT_DB_NAME = "seforim.db"

/**
 * Resolves the database path, preferring an environment variable if present,
 * falling back to AppSettings, and finally checking the default location.
 *
 * The path is resolved once and cached (thread-safe); call [reset] to force
 * re-resolution after the database is reinstalled or relocated (e.g. following
 * [io.github.kdroidfilter.seforimapp.features.database.update.DatabaseCleanupUseCase]).
 */
@Inject
@SingleIn(AppScope::class)
class DatabasePathProvider {
    @Volatile
    private var cached: String? = null

    /** The database path; throws [IllegalStateException] when the file does not exist. */
    fun get(): String {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: resolve().also { cached = it }
        }
    }

    /** Clears the cached path so the next [get] re-resolves it. */
    fun reset() {
        synchronized(this) { cached = null }
    }

    private fun resolve(): String {
        // 1) Prefer an explicit environment variable override if provided
        val envDbPath = System.getenv("SEFORIMAPP_DATABASE_PATH")?.takeIf { it.isNotBlank() }

        // 2) Try AppSettings (but fix if it points to lexical.db which is wrong)
        val rawSettingsPath = AppSettings.getDatabasePath()
        val settingsPath =
            if (rawSettingsPath?.endsWith("lexical.db", ignoreCase = true) == true) {
                // Fix incorrect path by clearing it
                AppSettings.setDatabasePath(null)
                null
            } else {
                rawSettingsPath
            }

        // 3) Fallback to default location
        val defaultDbPath = File(FileKit.databasesDir.path, DEFAULT_DB_NAME).absolutePath

        val dbPath = envDbPath ?: settingsPath ?: defaultDbPath

        infoln { "[DatabaseUtils] Database path resolved: $dbPath (exists: ${File(dbPath).exists()})" }

        // Check if the database file exists
        val dbFile = File(dbPath)
        if (!dbFile.exists()) {
            throw IllegalStateException("Database file not found at $dbPath")
        }

        return dbPath
    }
}
