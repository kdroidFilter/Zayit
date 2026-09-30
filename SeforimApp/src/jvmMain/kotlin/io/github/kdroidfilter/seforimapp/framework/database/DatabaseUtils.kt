package io.github.kdroidfilter.seforimapp.framework.database

import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.logger.infoln
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import java.io.File

private const val DEFAULT_DB_NAME = "seforim.db"

/**
 * Cached database path. Resolved on first access and kept for the runtime, but can
 * be invalidated with [resetDatabasePathCache] after a reinstall changes the database
 * location (e.g. following [io.github.kdroidfilter.seforimapp.features.database.update.DatabaseCleanupUseCase]).
 */
@Volatile
private var cachedDatabasePath: String? = null
private val databasePathLock = Any()

/**
 * Gets the database path, preferring an environment variable if present,
 * falling back to AppSettings, and finally checking the default location.
 *
 * The path is resolved once and cached (thread-safe); call [resetDatabasePathCache]
 * to force re-resolution after the database is reinstalled or relocated.
 */
fun getDatabasePath(): String {
    cachedDatabasePath?.let { return it }
    return synchronized(databasePathLock) {
        cachedDatabasePath ?: resolveDatabasePath().also { cachedDatabasePath = it }
    }
}

/**
 * Clears the cached database path so the next [getDatabasePath] re-resolves it.
 * Called after a reinstall so the app opens the freshly installed database rather
 * than a stale path captured at startup.
 */
fun resetDatabasePathCache() {
    synchronized(databasePathLock) { cachedDatabasePath = null }
}

private fun resolveDatabasePath(): String {
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
