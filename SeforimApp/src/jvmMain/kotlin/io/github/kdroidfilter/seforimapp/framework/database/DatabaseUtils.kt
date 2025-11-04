package io.github.kdroidfilter.seforimapp.framework.database

import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import java.io.File

/**
 * Gets the database path from AppSettings. No environment variable usage.
 */
fun getDatabasePath(): String {
    val dbPath = AppSettings.getDatabasePath()
        ?: throw IllegalStateException("Database path is not configured in settings")

    // Check if the database file exists
    val dbFile = File(dbPath)
    if (!dbFile.exists()) {
        throw IllegalStateException("Database file not found at $dbPath")
    }

    return dbPath
}


//fun getDatabasePath() = "/home/elie-gambache/IdeaProjects/SeforimApp/SeforimLibrary/generator/build/seforim.db"