package io.github.kdroidfilter.seforimapp.framework.database

import android.content.Context
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.github.kdroidfilter.seforimapp.framework.search.openSeforimRepository
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository

/** Android's connections, opened with the app's [context]. */
actual class AppDatabases(
    private val context: Context,
    private val userSettingsPath: String = defaultUserSettingsDatabasePath(),
) {
    actual fun openSeforimRepository(databasePath: String): SeforimRepository = openSeforimRepository(context, databasePath)

    actual fun openUserSettings(): SqlDriver = AndroidSqliteDriver(CallerCreatedSchema, context, userSettingsPath)
}

// The caller creates the user tables on each open (CREATE TABLE IF NOT EXISTS), as on the desktop
private object CallerCreatedSchema : SqlSchema<QueryResult.Value<Unit>> {
    override val version: Long = 1

    override fun create(driver: SqlDriver) = QueryResult.Unit

    override fun migrate(
        driver: SqlDriver,
        oldVersion: Long,
        newVersion: Long,
        vararg callbacks: AfterVersion,
    ) = QueryResult.Unit
}
