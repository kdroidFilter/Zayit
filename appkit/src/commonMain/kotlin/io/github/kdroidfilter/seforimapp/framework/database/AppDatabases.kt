package io.github.kdroidfilter.seforimapp.framework.database

import app.cash.sqldelight.db.SqlDriver
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/** Qualifies the shared connection to the user database in the graph. */
const val USER_SETTINGS_DRIVER = "userSettingsDriver"

private const val SETTINGS_DB_DIR = "settings"
private const val SETTINGS_DB_NAME = "user_settings.db"

/** The user database's usual place: databases/settings/user_settings.db in the app's data (its folder created). */
fun defaultUserSettingsDatabasePath(): String {
    val dir = Path(FileKit.databasesDir.path, SETTINGS_DB_DIR)
    SystemFileSystem.createDirectories(dir)
    return Path(dir, SETTINGS_DB_NAME).toString()
}

/**
 * The app's SQLite connections, as each platform opens them; the app's graph provides it (on Android, with its
 * context). The common bindings (AppKitBindings) build the repository and the user stores on them.
 */
expect class AppDatabases {
    /** The books' database at [databasePath], downloaded complete: nothing created nor migrated. */
    fun openSeforimRepository(databasePath: String): SeforimRepository

    /**
     * The single shared connection to the user database, which waits for a concurrent writer rather than failing; its
     * schema is created by the caller.
     */
    fun openUserSettings(): SqlDriver
}
