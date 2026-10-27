package io.github.kdroidfilter.seforimapp.framework.search

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository

/**
 * The repository of the database at [databasePath] (the downloaded database). Its schema exists already: the driver
 * creates and migrates nothing.
 */
fun openSeforimRepository(databasePath: String): SeforimRepository {
    val driver =
        NativeSqliteDriver(
            DatabaseConfiguration(
                name = databasePath.substringAfterLast('/'),
                version = 1,
                create = {},
                upgrade = { _, _, _ -> },
                extendedConfig = DatabaseConfiguration.Extended(basePath = databasePath.substringBeforeLast('/')),
            ),
            // Several readers at once: the search reads snippets while the screens read books and lines
            maxReaderConnections = 4,
        )
    return SeforimRepository(databasePath, driver)
}
