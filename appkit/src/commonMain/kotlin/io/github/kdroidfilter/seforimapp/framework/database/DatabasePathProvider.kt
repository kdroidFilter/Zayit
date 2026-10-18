package io.github.kdroidfilter.seforimapp.framework.database

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.kdroidfilter.seforimapp.framework.platform.environmentVariable
import io.github.kdroidfilter.seforimapp.logger.infoln
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.exists
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.concurrent.Volatile

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
class DatabasePathProvider(
    private val appSettings: AppSettings,
) {
    private val lock = SynchronizedObject()

    @Volatile
    private var cached: String? = null

    /** The database path; throws [IllegalStateException] when the file does not exist. */
    fun get(): String {
        cached?.let { return it }
        return synchronized(lock) {
            cached ?: resolve().also { cached = it }
        }
    }

    /** Clears the cached path so the next [get] re-resolves it. */
    fun reset() {
        synchronized(lock) { cached = null }
    }

    private fun resolve(): String {
        // 1) Prefer an explicit environment variable override if provided
        val envDbPath = environmentVariable("SEFORIMAPP_DATABASE_PATH")?.takeIf { it.isNotBlank() }

        // 2) Try AppSettings (but fix if it points to lexical.db which is wrong)
        val rawSettingsPath = appSettings.getDatabasePath()
        val settingsPath =
            if (rawSettingsPath?.endsWith("lexical.db", ignoreCase = true) == true) {
                // Fix incorrect path by clearing it
                appSettings.setDatabasePath(null)
                null
            } else {
                rawSettingsPath
            }

        // 3) Fallback to default location
        val defaultDbPath = PlatformFile(FileKit.databasesDir, DEFAULT_DB_NAME).absolutePath()

        val dbPath = envDbPath ?: settingsPath ?: defaultDbPath

        val exists = PlatformFile(dbPath).exists()
        infoln { "[DatabaseUtils] Database path resolved: $dbPath (exists: $exists)" }

        // Check if the database file exists
        if (!exists) {
            throw IllegalStateException("Database file not found at $dbPath")
        }

        return dbPath
    }
}
