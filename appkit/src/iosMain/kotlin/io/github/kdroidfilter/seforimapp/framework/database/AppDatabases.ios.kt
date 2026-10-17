package io.github.kdroidfilter.seforimapp.framework.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration
import io.github.kdroidfilter.seforimapp.framework.search.openSeforimRepository
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository

/** iOS's connections. */
actual class AppDatabases(
    private val userSettingsPath: String = defaultUserSettingsDatabasePath(),
) {
    actual fun openSeforimRepository(databasePath: String): SeforimRepository = openSeforimRepository(databasePath)

    // The caller creates the user tables on each open (CREATE TABLE IF NOT EXISTS), as on the desktop; SQLiter waits
    // for a concurrent writer (its busy timeout)
    actual fun openUserSettings(): SqlDriver =
        NativeSqliteDriver(
            DatabaseConfiguration(
                name = userSettingsPath.substringAfterLast('/'),
                version = 1,
                create = {},
                upgrade = { _, _, _ -> },
                extendedConfig = DatabaseConfiguration.Extended(basePath = userSettingsPath.substringBeforeLast('/')),
            ),
        )
}
