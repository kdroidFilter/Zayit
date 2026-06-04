package io.github.kdroidfilter.seforimapp.core.annotations

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnnotationStoresTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: UserSettingsDb

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        UserSettingsDb.Schema.create(driver)
        db = UserSettingsDb(driver)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun highlight_roundTrips_and_loads_per_book() =
        runTest {
            val store = HighlightStore(db)
            store.addHighlight(BOOK, lineId = 10, startOffset = 0, endOffset = 5, color = HighlightColors.Yellow, timestamp = 1)

            // Reload from a fresh store backed by the same DB.
            val reloaded = HighlightStore(db)
            reloaded.loadBook(BOOK)
            val line = reloaded.highlightsForLine(BOOK, 10)
            assertEquals(1, line.size)
            assertEquals(0 to 5, line[0].startOffset to line[0].endOffset)
        }

    @Test
    fun adding_overlapping_highlight_replaces_previous() =
        runTest {
            val store = HighlightStore(db)
            store.addHighlight(BOOK, 10, 0, 10, HighlightColors.Yellow, timestamp = 1)
            store.addHighlight(BOOK, 10, 3, 7, HighlightColors.Green, timestamp = 2)

            val line = store.highlightsForLine(BOOK, 10)
            assertEquals(1, line.size)
            assertEquals(3 to 7, line[0].startOffset to line[0].endOffset)
            assertEquals(HighlightColors.Green, line[0].color)
        }

    @Test
    fun removing_highlight_clears_it() =
        runTest {
            val store = HighlightStore(db)
            store.addHighlight(BOOK, 10, 0, 5, HighlightColors.Blue, timestamp = 1)
            val highlight = store.highlightsForLine(BOOK, 10).single()

            store.removeHighlight(BOOK, highlight)
            assertTrue(store.highlightsForLine(BOOK, 10).isEmpty())
        }

    @Test
    fun highlights_are_isolated_per_book() =
        runTest {
            val store = HighlightStore(db)
            store.addHighlight(BOOK, 10, 0, 5, HighlightColors.Pink, timestamp = 1)
            store.addHighlight(OTHER_BOOK, 10, 0, 5, HighlightColors.Orange, timestamp = 1)

            assertEquals(1, store.highlightsForLine(BOOK, 10).size)
            assertEquals(HighlightColors.Orange, store.highlightsForLine(OTHER_BOOK, 10).single().color)
        }

    @Test
    fun note_upsert_get_and_blank_deletes() =
        runTest {
            val store = LineNoteStore(db)
            store.loadBook(BOOK)
            store.setNote(BOOK, lineId = 42, note = "first", timestamp = 1)
            assertEquals("first", store.getNote(BOOK, 42))
            assertTrue(store.hasNote(BOOK, 42))

            store.setNote(BOOK, 42, note = "updated", timestamp = 2)
            assertEquals("updated", store.getNote(BOOK, 42))

            store.setNote(BOOK, 42, note = "   ", timestamp = 3)
            assertNull(store.getNote(BOOK, 42))
            assertFalse(store.hasNote(BOOK, 42))
        }

    @Test
    fun note_lineIds_cache_loads_from_db() =
        runTest {
            LineNoteStore(db).setNote(BOOK, 42, "note", timestamp = 1)

            val reloaded = LineNoteStore(db)
            reloaded.loadBook(BOOK)
            assertTrue(reloaded.hasNote(BOOK, 42))
            assertFalse(reloaded.hasNote(BOOK, 99))
        }

    private companion object {
        const val BOOK = 1L
        const val OTHER_BOOK = 2L
    }
}
