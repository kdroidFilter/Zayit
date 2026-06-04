package io.github.kdroidfilter.seforimapp.core.annotations

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun notes_are_ranged_and_multiple_per_line() =
        runTest {
            val store = NoteStore(db)
            store.addNote(BOOK, lineId = 42, startOffset = 0, endOffset = 4, note = "on a word", timestamp = 1)
            store.addNote(BOOK, lineId = 42, startOffset = 10, endOffset = 25, note = "on a phrase", timestamp = 2)

            val line = store.notesForLine(BOOK, 42)
            assertEquals(2, line.size)
            assertEquals(setOf(0 to 4, 10 to 25), line.map { it.startOffset to it.endOffset }.toSet())
        }

    @Test
    fun note_update_and_remove() =
        runTest {
            val store = NoteStore(db)
            store.addNote(BOOK, 42, 0, 4, "first", timestamp = 1)
            val note = store.notesForLine(BOOK, 42).single()

            store.updateNote(BOOK, note.id, "edited", timestamp = 2)
            assertEquals("edited", store.notesForLine(BOOK, 42).single().note)

            store.removeNote(BOOK, note.id)
            assertTrue(store.notesForLine(BOOK, 42).isEmpty())
        }

    @Test
    fun blank_note_is_rejected_and_blank_update_deletes() =
        runTest {
            val store = NoteStore(db)
            store.addNote(BOOK, 42, 0, 4, note = "   ", timestamp = 1)
            assertTrue(store.notesForLine(BOOK, 42).isEmpty())

            store.addNote(BOOK, 42, 0, 4, note = "real", timestamp = 2)
            val note = store.notesForLine(BOOK, 42).single()
            store.updateNote(BOOK, note.id, note = "  ", timestamp = 3)
            assertTrue(store.notesForLine(BOOK, 42).isEmpty())
        }

    @Test
    fun notes_load_from_db_and_are_isolated_per_book() =
        runTest {
            NoteStore(db).addNote(BOOK, 42, 0, 4, "note", timestamp = 1)

            val reloaded = NoteStore(db)
            reloaded.loadBook(BOOK)
            assertEquals(1, reloaded.notesForLine(BOOK, 42).size)
            assertTrue(reloaded.notesForLine(OTHER_BOOK, 42).isEmpty())
        }

    private companion object {
        const val BOOK = 1L
        const val OTHER_BOOK = 2L
    }
}
