package io.github.kdroidfilter.seforimapp.framework.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import java.util.Properties

/** The desktop's connections; [userSettingsPath] is the user database's (elsewhere in the end-to-end harness). */
actual class AppDatabases(
    private val userSettingsPath: String = defaultUserSettingsDatabasePath(),
) {
    // Persistent single-connection driver with prepared-statement cache + read-tuning PRAGMAs. Replaces
    // `JdbcSqliteDriver` whose ThreadedConnectionManager closes the SQLite connection after every non-transactional
    // query (confirmed by JFR 2026-04-23: ~70 `NativeDB.prepare_utf8` + `NativeDB._close()` pairs / 20 s).
    actual fun openSeforimRepository(databasePath: String): SeforimRepository =
        SeforimRepository(databasePath, PersistentSqliteDriver("jdbc:sqlite:$databasePath"))

    // Separate from the read-only books DB. Waits for a concurrent writer instead of failing: the backup snapshot
    // (VACUUM INTO) runs on its own thread, hence its own connection, while the stores keep writing.
    actual fun openUserSettings(): SqlDriver =
        JdbcSqliteDriver(
            "jdbc:sqlite:$userSettingsPath",
            Properties().apply { setProperty("busy_timeout", USER_DB_BUSY_TIMEOUT_MS.toString()) },
        )

    private companion object {
        const val USER_DB_BUSY_TIMEOUT_MS = 5_000
    }
}
