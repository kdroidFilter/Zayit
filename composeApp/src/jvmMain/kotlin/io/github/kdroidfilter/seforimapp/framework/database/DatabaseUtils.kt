package io.github.kdroidfilter.seforimapp.framework.database

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import java.io.File

// Fixed database path as specified in the requirements
private const val DB_PATH = "/Users/elie/IdeaProjects/SeforimApp/SeforimLibrary/generator/otzaria.db"

/**
 * Gets the database path.
 * In this implementation, we use a fixed path as specified in the requirements.
 */
@Composable
fun getDatabasePath(): String {
    // Check if the database file exists
    val dbFile = File(DB_PATH)
    if (!dbFile.exists()) {
        throw IllegalStateException("Database file not found at $DB_PATH")
    }
    
    return DB_PATH
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