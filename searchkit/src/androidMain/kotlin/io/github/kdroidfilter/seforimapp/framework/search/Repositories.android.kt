package io.github.kdroidfilter.seforimapp.framework.search

import android.content.Context
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository

/**
 * The repository of the database at [databasePath] (an absolute path: the downloaded database, not one the app ships).
 * Its schema exists already: the driver creates and migrates nothing.
 */
fun openSeforimRepository(
    context: Context,
    databasePath: String,
): SeforimRepository =
    SeforimRepository(
        databasePath,
        AndroidSqliteDriver(ExistingSchema, context, databasePath, windowSizeBytes = CURSOR_WINDOW_BYTES),
    )

// A book's notes reach 2.5 MB: more than the 2 MB a cursor holds by default on Android
private const val CURSOR_WINDOW_BYTES = 8L * 1024 * 1024

// The downloaded database is complete: version 1, never created nor migrated here
private object ExistingSchema : SqlSchema<QueryResult.Value<Unit>> {
    override val version: Long = 1

    override fun create(driver: SqlDriver) = QueryResult.Unit

    override fun migrate(
        driver: SqlDriver,
        oldVersion: Long,
        newVersion: Long,
        vararg callbacks: app.cash.sqldelight.db.AfterVersion,
    ) = QueryResult.Unit
}
