package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import io.github.kdroidfilter.seforimapp.features.bookcontent.TestFactories
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentStateManager
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for line selection state management.
 *
 * Note: Since SeforimRepository is a concrete class with database dependencies,
 * these tests focus on the state management aspects that can be tested without mocking.
 * The actual ContentUseCase.selectLine method requires repository access which is
 * tested through integration tests with a real database.
 */
class ContentUseCaseLineSelectionTest {
    private lateinit var persistedStore: TabPersistedStateStore
    private lateinit var stateManager: BookContentStateManager

    private val testTabId = "test-tab-selection"

    @BeforeTest
    fun setup() {
        persistedStore = TabPersistedStateStore()
        stateManager = BookContentStateManager(testTabId, persistedStore)
    }

    // ==================== State Management Tests ====================

    @Test
    fun `updateContent replaces selection correctly`() {
        // Given: An existing selection with line1
        val line1 = TestFactories.createLine(id = 1L)
        val line2 = TestFactories.createLine(id = 2L)

        stateManager.updateContent {
            copy(
                selectedLines = setOf(line1),
                primarySelectedLineId = line1.id,
            )
        }

        // When: Updating to select line2 only (simulating normal click behavior)
        stateManager.updateContent {
            copy(
                selectedLines = setOf(line2),
                primarySelectedLineId = line2.id,
                isTocEntrySelection = false,
            )
        }

        // Then: Selection is replaced with line2 only
        val state = stateManager.state.value
        assertEquals(setOf(line2), state.content.selectedLines)
        assertEquals(line2.id, state.content.primarySelectedLineId)
        assertFalse(state.content.isTocEntrySelection)
    }

    @Test
    fun `updateContent adds line to selection with modifier behavior`() {
        // Given: An existing selection with line1
        val line1 = TestFactories.createLine(id = 1L)
        val line2 = TestFactories.createLine(id = 2L)

        stateManager.updateContent {
            copy(
                selectedLines = setOf(line1),
                primarySelectedLineId = line1.id,
            )
        }

        // When: Adding line2 to selection (simulating Ctrl+click)
        stateManager.updateContent {
            val currentSelection = selectedLines
            copy(
                selectedLines = currentSelection + line2,
                primarySelectedLineId = line2.id,
                isTocEntrySelection = false,
            )
        }

        // Then: Both lines are selected, line2 is primary
        val state = stateManager.state.value
        assertEquals(setOf(line1, line2), state.content.selectedLines)
        assertEquals(line2.id, state.content.primarySelectedLineId)
    }

    @Test
    fun `updateContent removes line from selection`() {
        // Given: A selection with both line1 and line2
        val line1 = TestFactories.createLine(id = 1L)
        val line2 = TestFactories.createLine(id = 2L)

        stateManager.updateContent {
            copy(
                selectedLines = setOf(line1, line2),
                primarySelectedLineId = line2.id,
            )
        }

        // When: Removing line2 from selection (simulating Ctrl+click on selected line)
        stateManager.updateContent {
            val newSelection = selectedLines.filterNot { it.id == line2.id }.toSet()
            copy(
                selectedLines = newSelection,
                primarySelectedLineId = newSelection.firstOrNull()?.id,
            )
        }

        // Then: Line2 is removed, line1 becomes primary
        val state = stateManager.state.value
        assertEquals(setOf(line1), state.content.selectedLines)
        assertEquals(line1.id, state.content.primarySelectedLineId)
    }

    @Test
    fun `removing last line clears selection and primarySelectedLineId`() {
        // Given: A selection with only line1
        val line1 = TestFactories.createLine(id = 1L)

        stateManager.updateContent {
            copy(
                selectedLines = setOf(line1),
                primarySelectedLineId = line1.id,
            )
        }

        // When: Removing line1 (the last line)
        stateManager.updateContent {
            val newSelection = selectedLines.filterNot { it.id == line1.id }.toSet()
            copy(
                selectedLines = newSelection,
                primarySelectedLineId = newSelection.firstOrNull()?.id,
            )
        }

        // Then: Selection is empty, primary is null
        val state = stateManager.state.value
        assertTrue(state.content.selectedLines.isEmpty())
        assertNull(state.content.primarySelectedLineId)
    }

    @Test
    fun `TOC heading selection sets isTocEntrySelection true`() {
        // Given: A set of lines representing a TOC section
        val sectionLines =
            (1L..5L)
                .map { id ->
                    TestFactories.createLine(id = id, lineIndex = id.toInt() - 1)
                }.toSet()
        val headingLine = sectionLines.first()

        // When: Selecting all section lines (simulating TOC heading selection)
        stateManager.updateContent {
            copy(
                selectedLines = sectionLines,
                primarySelectedLineId = headingLine.id,
                isTocEntrySelection = true,
            )
        }

        // Then: All section lines are selected with TOC flag
        val state = stateManager.state.value
        assertEquals(sectionLines, state.content.selectedLines)
        assertEquals(headingLine.id, state.content.primarySelectedLineId)
        assertTrue(state.content.isTocEntrySelection)
    }

    @Test
    fun `modifier click after TOC selection sets isTocEntrySelection false`() {
        // Given: A TOC entry selection
        val line1 = TestFactories.createLine(id = 1L)
        val line2 = TestFactories.createLine(id = 2L)
        val line3 = TestFactories.createLine(id = 3L)

        stateManager.updateContent {
            copy(
                selectedLines = setOf(line1, line2),
                primarySelectedLineId = line1.id,
                isTocEntrySelection = true,
            )
        }

        // When: Adding line3 via modifier click (manual selection)
        stateManager.updateContent {
            val currentSelection = selectedLines
            copy(
                selectedLines = currentSelection + line3,
                primarySelectedLineId = line3.id,
                isTocEntrySelection = false, // Ctrl+click = manual selection
            )
        }

        // Then: isTocEntrySelection becomes false
        val state = stateManager.state.value
        assertEquals(setOf(line1, line2, line3), state.content.selectedLines)
        assertFalse(state.content.isTocEntrySelection)
    }

    @Test
    fun `primarySelectedLineId is always in selectedLines when not null`() {
        // Given: Multiple lines
        val line1 = TestFactories.createLine(id = 1L)
        val line2 = TestFactories.createLine(id = 2L)

        // When: Setting up selection
        stateManager.updateContent {
            copy(
                selectedLines = setOf(line1, line2),
                primarySelectedLineId = line2.id,
            )
        }

        // Then: primarySelectedLineId is in selectedLines
        val state = stateManager.state.value
        val primaryId = state.content.primarySelectedLineId
        assertTrue(primaryId != null)
        assertTrue(state.content.selectedLines.any { it.id == primaryId })
    }

    @Test
    fun `selectedLineIds computed property matches selectedLines ids`() {
        // Given: Selection with multiple lines
        val line1 = TestFactories.createLine(id = 10L)
        val line2 = TestFactories.createLine(id = 20L)
        val line3 = TestFactories.createLine(id = 30L)

        stateManager.updateContent {
            copy(
                selectedLines = setOf(line1, line2, line3),
                primarySelectedLineId = line1.id,
            )
        }

        // Then: selectedLineIds contains all line IDs
        val state = stateManager.state.value
        assertEquals(setOf(10L, 20L, 30L), state.content.selectedLineIds)
    }

    @Test
    fun `empty selection has empty selectedLineIds`() {
        // Given: Empty selection
        stateManager.updateContent {
            copy(
                selectedLines = emptySet(),
                primarySelectedLineId = null,
            )
        }

        // Then: selectedLineIds is empty
        val state = stateManager.state.value
        assertTrue(state.content.selectedLineIds.isEmpty())
    }

    @Test
    fun `selection is limited to 128 lines for TOC section`() {
        // Given: A large section with 200 lines
        val allLines =
            (1L..200L).map { id ->
                TestFactories.createLine(id = id, lineIndex = id.toInt() - 1)
            }

        // When: Applying 128-line limit (simulating sliding window)
        val limitedLines = allLines.take(128).toSet()
        stateManager.updateContent {
            copy(
                selectedLines = limitedLines,
                primarySelectedLineId = limitedLines.first().id,
                isTocEntrySelection = true,
            )
        }

        // Then: At most 128 lines are selected
        val state = stateManager.state.value
        assertEquals(128, state.content.selectedLines.size)
    }

    @Test
    fun `primaryLine computed property returns primary selected line`() {
        // Given: Selection with a primary line
        val line1 = TestFactories.createLine(id = 10L)
        val line2 = TestFactories.createLine(id = 20L)

        stateManager.updateContent {
            copy(
                selectedLines = setOf(line1, line2),
                primarySelectedLineId = line2.id,
            )
        }

        // Then: primaryLine returns the line with primarySelectedLineId
        val state = stateManager.state.value
        assertEquals(line2, state.content.primaryLine)
    }

    @Test
    fun `primaryLine returns first line when primarySelectedLineId not found`() {
        // Given: Selection where primarySelectedLineId doesn't match any line
        val line1 = TestFactories.createLine(id = 10L)
        val line2 = TestFactories.createLine(id = 20L)

        stateManager.updateContent {
            copy(
                selectedLines = setOf(line1, line2),
                primarySelectedLineId = 999L, // Non-existent ID
            )
        }

        // Then: primaryLine returns the first line
        val state = stateManager.state.value
        assertEquals(state.content.selectedLines.firstOrNull(), state.content.primaryLine)
    }

    @Test
    fun `primaryLine returns null when selection is empty`() {
        // Given: Empty selection
        stateManager.updateContent {
            copy(
                selectedLines = emptySet(),
                primarySelectedLineId = null,
            )
        }

        // Then: primaryLine is null
        val state = stateManager.state.value
        assertNull(state.content.primaryLine)
    }
}
