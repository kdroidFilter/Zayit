package io.github.kdroidfilter.seforimapp.framework.database

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
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

/**
 * Creates and returns a SeforimRepository instance.
 */
@Composable
fun getRepository(): SeforimRepository {
    val dbPath = getDatabasePath()
    val driver: SqlDriver = remember(dbPath) {
        // Use the SQLite driver for desktop
        JdbcSqliteDriver("jdbc:sqlite:$dbPath")
    }

    return remember(driver) {
        SeforimRepository(dbPath, driver)
    }
}