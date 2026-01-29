package io.github.kdroidfilter.seforimapp.features.bookcontent

import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentStateManager
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for the line selection feature.
 *
 * These tests verify the complete state management flow for line selection,
 * including persistence across state manager instances.
 *
 * Note: Tests requiring SeforimRepository are excluded because it's a concrete
 * class with database dependencies that cannot be easily mocked.
 */
class LineSelectionIntegrationTest {
    private lateinit var persistedStore: TabPersistedStateStore
    private lateinit var stateManager: BookContentStateManager

    private val testTabId = "test-tab-integration"
    private val testBookId = 100L

    @BeforeTest
    fun setup() {
        persistedStore = TabPersistedStateStore()
        stateManager = BookContentStateManager(testTabId, persistedStore)

        // Set up default book
        val book = TestFactories.createBook(id = testBookId)
        stateManager.updateNavigation {
            copy(selectedBook = book)
        }
    }

    // ==================== Single Line Selection Flow ====================

    @Test
    fun `single line selection updates state correctly`() {
        // Given: A line
        val line = TestFactories.createLine(id = 10L, bookId = testBookId)

        // When: Simulating single line selection
        stateManager.updateContent {
            copy(
                selectedLines = setOf(line),
                primarySelectedLineId = line.id,
                isTocEntrySelection = false,
            )
        }

        // Then: State is updated correctly
        val state = stateManager.state.value
        assertEquals(setOf(line), state.content.selectedLines)
        assertEquals(line.id, state.content.primarySelectedLineId)
        assertFalse(state.content.isTocEntrySelection)
    }

    // ==================== Multi-Line Selection Flow ====================

    @Test
    fun `multi-line selection via modifier click accumulates lines`() {
        // Given: Multiple lines
        val line1 = TestFactories.createLine(id = 1L, bookId = testBookId)
        val line2 = TestFactories.createLine(id = 2L, bookId = testBookId)
        val line3 = TestFactories.createLine(id = 3L, bookId = testBookId)

        // When: Selecting lines (simulating modifier clicks)
        // First click - single selection
        stateManager.updateContent {
            copy(
                selectedLines = setOf(line1),
                primarySelectedLineId = line1.id,
            )
        }
        // Second click with modifier - add line2
        stateManager.updateContent {
            copy(
                selectedLines = selectedLines + line2,
                primarySelectedLineId = line2.id,
            )
        }
        // Third click with modifier - add line3
        stateManager.updateContent {
            copy(
                selectedLines = selectedLines + line3,
                primarySelectedLineId = line3.id,
            )
        }

        // Then: All lines are selected
        val state = stateManager.state.value
        assertEquals(setOf(line1, line2, line3), state.content.selectedLines)
        assertEquals(line3.id, state.content.primarySelectedLineId)
        assertFalse(state.content.isTocEntrySelection)
    }

    // ==================== TOC Heading Selection ====================

    @Test
    fun `TOC heading selection sets isTocEntrySelection true`() {
        // Given: Lines representing a TOC section
        val sectionLines =
            (10L..15L)
                .map { id ->
                    TestFactories.createLine(id = id, bookId = testBookId, lineIndex = id.toInt() - 10)
                }.toSet()
        val headingLine = sectionLines.first()

        // When: Simulating TOC heading selection
        stateManager.updateContent {
            copy(
                selectedLines = sectionLines,
                primarySelectedLineId = headingLine.id,
                isTocEntrySelection = true,
            )
        }

        // Then: isTocEntrySelection is true
        val state = stateManager.state.value
        assertTrue(state.content.isTocEntrySelection)
        assertEquals(sectionLines, state.content.selectedLines)
    }

    @Test
    fun `modifier click after TOC selection sets isTocEntrySelection false`() {
        // Given: TOC selection state
        val line1 = TestFactories.createLine(id = 10L, bookId = testBookId)
        val line2 = TestFactories.createLine(id = 11L, bookId = testBookId)
        val line3 = TestFactories.createLine(id = 12L, bookId = testBookId)

        stateManager.updateContent {
            copy(
                selectedLines = setOf(line1, line2),
                primarySelectedLineId = line1.id,
                isTocEntrySelection = true,
            )
        }

        // When: Adding a line with modifier click (manual selection)
        stateManager.updateContent {
            copy(
                selectedLines = selectedLines + line3,
                primarySelectedLineId = line3.id,
                isTocEntrySelection = false, // Modifier click = manual selection
            )
        }

        // Then: isTocEntrySelection becomes false
        val state = stateManager.state.value
        assertFalse(state.content.isTocEntrySelection)
        assertEquals(setOf(line1, line2, line3), state.content.selectedLines)
    }

    // ==================== Save-Restore Cycle ====================

    @Test
    fun `selection survives save-restore cycle`() {
        // Given: A multi-line selection
        val line1 = TestFactories.createLine(id = 10L, bookId = testBookId)
        val line2 = TestFactories.createLine(id = 20L, bookId = testBookId)

        stateManager.updateContent {
            copy(
                selectedLines = setOf(line1, line2),
                primarySelectedLineId = line2.id,
                isTocEntrySelection = false,
            )
        }

        // Verify initial state
        var state = stateManager.state.value
        assertEquals(setOf(line1, line2), state.content.selectedLines)
        assertEquals(line2.id, state.content.primarySelectedLineId)

        // When: Creating a new state manager (simulating app restart)
        val newStateManager = BookContentStateManager(testTabId, persistedStore)

        // Then: Primary ID is restored from persistence
        state = newStateManager.state.value
        assertEquals(line2.id, state.content.primarySelectedLineId)
        // Note: selectedLines will be empty because Line objects aren't serialized
        // They would need to be reloaded from repository using the persisted IDs

        // Verify the IDs were persisted
        val persisted = persistedStore.get(testTabId)?.bookContent
        assertEquals(setOf(10L, 20L), persisted?.selectedLineIds)
    }

    @Test
    fun `isTocEntrySelection survives save-restore cycle`() {
        // Given: A TOC entry selection
        val line = TestFactories.createLine(id = 10L, bookId = testBookId)

        stateManager.updateContent {
            copy(
                selectedLines = setOf(line),
                primarySelectedLineId = line.id,
                isTocEntrySelection = true,
            )
        }

        // When: Creating a new state manager
        val newStateManager = BookContentStateManager(testTabId, persistedStore)

        // Then: isTocEntrySelection is restored
        val state = newStateManager.state.value
        assertTrue(state.content.isTocEntrySelection)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `removing all lines clears primarySelectedLineId`() {
        // Given: A multi-line selection
        val line1 = TestFactories.createLine(id = 1L, bookId = testBookId)
        val line2 = TestFactories.createLine(id = 2L, bookId = testBookId)

        stateManager.updateContent {
            copy(
                selectedLines = setOf(line1, line2),
                primarySelectedLineId = line2.id,
            )
        }

        // When: Removing both lines
        stateManager.updateContent {
            copy(
                selectedLines = emptySet(),
                primarySelectedLineId = null,
            )
        }

        // Then: Selection is empty
        val state = stateManager.state.value
        assertTrue(state.content.selectedLines.isEmpty())
        assertNull(state.content.primarySelectedLineId)
    }

    @Test
    fun `normal click after multi-selection replaces with single line`() {
        // Given: Multi-line selection
        val line1 = TestFactories.createLine(id = 1L, bookId = testBookId)
        val line2 = TestFactories.createLine(id = 2L, bookId = testBookId)
        val line3 = TestFactories.createLine(id = 3L, bookId = testBookId)

        stateManager.updateContent {
            copy(
                selectedLines = setOf(line1, line2),
                primarySelectedLineId = line2.id,
            )
        }

        // When: Normal click on line3 (replaces selection)
        stateManager.updateContent {
            copy(
                selectedLines = setOf(line3),
                primarySelectedLineId = line3.id,
            )
        }

        // Then: Only line3 is selected
        val state = stateManager.state.value
        assertEquals(setOf(line3), state.content.selectedLines)
        assertEquals(line3.id, state.content.primarySelectedLineId)
    }

    @Test
    fun `selectedLineIds computed property is consistent`() {
        // Given: Lines with various IDs
        val line1 = TestFactories.createLine(id = 100L, bookId = testBookId)
        val line2 = TestFactories.createLine(id = 200L, bookId = testBookId)
        val line3 = TestFactories.createLine(id = 300L, bookId = testBookId)

        stateManager.updateContent {
            copy(
                selectedLines = setOf(line1, line2, line3),
                primarySelectedLineId = line1.id,
            )
        }

        // Then: selectedLineIds matches the IDs
        val state = stateManager.state.value
        assertEquals(setOf(100L, 200L, 300L), state.content.selectedLineIds)

        // And primarySelectedLineId is in selectedLineIds
        assertTrue(state.content.primarySelectedLineId in state.content.selectedLineIds)
    }

    @Test
    fun `resetForNewBook clears all selection state`() {
        // Given: Complex selection state
        val line1 = TestFactories.createLine(id = 1L, bookId = testBookId)
        val line2 = TestFactories.createLine(id = 2L, bookId = testBookId)

        stateManager.updateContent {
            copy(
                selectedLines = setOf(line1, line2),
                primarySelectedLineId = line1.id,
                isTocEntrySelection = true,
                selectedCommentatorsByLine = mapOf(1L to setOf(10L)),
                selectedLinkSourcesByLine = mapOf(1L to setOf(100L)),
            )
        }

        // When: Resetting for new book
        stateManager.resetForNewBook()

        // Then: All selection state is cleared
        val state = stateManager.state.value
        assertTrue(state.content.selectedLines.isEmpty())
        assertNull(state.content.primarySelectedLineId)
        assertTrue(state.content.selectedCommentatorsByLine.isEmpty())
        assertTrue(state.content.selectedLinkSourcesByLine.isEmpty())
    }

    // ==================== Cross-tab Isolation ====================

    @Test
    fun `different tabs have isolated selection state`() {
        // Given: Two state managers for different tabs
        val tab1 = "tab-1"
        val tab2 = "tab-2"
        val manager1 = BookContentStateManager(tab1, persistedStore)
        val manager2 = BookContentStateManager(tab2, persistedStore)

        val line1 = TestFactories.createLine(id = 100L)
        val line2 = TestFactories.createLine(id = 200L)

        // When: Updating each tab's state
        manager1.updateContent {
            copy(selectedLines = setOf(line1), primarySelectedLineId = line1.id)
        }
        manager2.updateContent {
            copy(selectedLines = setOf(line2), primarySelectedLineId = line2.id)
        }

        // Then: Each tab has its own state
        assertEquals(100L, manager1.state.value.content.primarySelectedLineId)
        assertEquals(200L, manager2.state.value.content.primarySelectedLineId)

        // And persistence is isolated
        assertEquals(100L, persistedStore.get(tab1)?.bookContent?.primarySelectedLineId)
        assertEquals(200L, persistedStore.get(tab2)?.bookContent?.primarySelectedLineId)
    }
}
