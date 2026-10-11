package io.github.kdroidfilter.seforimapp.core.shnayimmikra

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class ShnayimMikraStoreTest {
    @Test
    fun `an aliya read counts for its parsha of that year only`() =
        runBlocking {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also(UserSettingsDb.Schema::create)
            val store = ShnayimMikraStore(UserSettingsDb(driver))
            store.setRead(5787, "BERESHIS", 0, read = true)
            store.setRead(5787, "BERESHIS", 2, read = true)
            store.setRead(5787, "BERESHIS", 2, read = false)
            assertEquals(setOf(0), store.read(5787, "BERESHIS"))
            assertEquals(emptySet(), store.read(5788, "BERESHIS"))
            assertEquals(emptySet(), store.read(5787, "NOACH"))
        }
}
