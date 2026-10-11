package io.github.kdroidfilter.seforimapp.framework.database

import io.github.kdroidfilter.seforimapp.core.e2e.E2e
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import java.io.File

private const val SETTINGS_DB_DIR = "settings"
private const val SETTINGS_DB_NAME = "user_settings.db"

/** Qualifies the shared connection to the user database in the graph. */
const val USER_SETTINGS_DRIVER = "userSettingsDriver"

fun getUserSettingsDatabasePath(): String {
    // An E2E run writes highlights, notes and history: in its own output, never the user's
    val root = (E2e.outDir ?: File(FileKit.databasesDir.path, SETTINGS_DB_DIR)).apply { mkdirs() }
    return File(root, SETTINGS_DB_NAME).absolutePath
}
