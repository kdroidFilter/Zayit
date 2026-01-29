package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import io.github.kdroidfilter.seforimapp.features.bookcontent.TestFactories
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentStateManager
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import io.github.kdroidfilter.seforimlibrary.dao.repository.LineSelectionRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for line selection functionality in [ContentUseCase].
 * Covers single-line selection, multi-line selection with modifier keys,
 * and TOC heading section selection.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContentUseCaseLineSelectionTest {
    private lateinit var repository: LineSelectionRepository
    private lateinit var persistedStore: TabPersistedStateStore
    private lateinit var stateManager: BookContentStateManager
    private lateinit var useCase: ContentUseCase

    private val testTabId = "test-tab-selection"

    @BeforeTest
    fun setup() {
        repository = mockk()
        persistedStore = TabPersistedStateStore()
        stateManager = BookContentStateManager(testTabId, persistedStore)
        useCase = ContentUseCase(repository, stateManager)

        // Default mocks for all tests
        coEvery { repository.getHeadingTocEntryByLineId(any()) } returns null
        coEvery { repository.getTocEntryIdForLine(any()) } returns null
        coEvery { repository.getLineIdsForTocEntry(any()) } returns emptyList()
        coEvery { repository.getLine(any()) } returns null
        coEvery { repository.getTocEntry(any()) } returns null
    }

    // ==================== selectLine Tests ====================

    @Test
    fun `selectLine with normal click replaces existing selection`() =
        runTest {
            // Given: An existing selection with line1
            val line1 = TestFactories.createLine(id = 1L)
            val line2 = TestFactories.createLine(id = 2L)

            stateManager.updateContent {
                copy(
                    selectedLines = setOf(line1),
                    primarySelectedLineId = line1.id,
                )
            }

            // When: Selecting line2 without modifier
            useCase.selectLine(line2, isModifierPressed = false)

            // Then: Selection is replaced with line2 only
            val state = stateManager.state.value
            assertEquals(setOf(line2), state.content.selectedLines)
            assertEquals(line2.id, state.content.primarySelectedLineId)
            assertFalse(state.content.isTocEntrySelection)
        }

    @Test
    fun `selectLine with modifier adds line to selection`() =
        runTest {
            // Given: An existing selection with line1
            val line1 = TestFactories.createLine(id = 1L)
            val line2 = TestFactories.createLine(id = 2L)

            stateManager.updateContent {
                copy(
                    selectedLines = setOf(line1),
                    primarySelectedLineId = line1.id,
                )
            }

            // When: Ctrl+clicking line2
            useCase.selectLine(line2, isModifierPressed = true)

            // Then: Both lines are selected, line2 is primary
            val state = stateManager.state.value
            assertEquals(setOf(line1, line2), state.content.selectedLines)
            assertEquals(line2.id, state.content.primarySelectedLineId)
            assertFalse(state.content.isTocEntrySelection)
        }

    @Test
    fun `selectLine with modifier removes already selected line`() =
        runTest {
            // Given: A selection with both line1 and line2
            val line1 = TestFactories.createLine(id = 1L)
            val line2 = TestFactories.createLine(id = 2L)

            stateManager.updateContent {
                copy(
                    selectedLines = setOf(line1, line2),
                    primarySelectedLineId = line2.id,
                )
            }

            // When: Ctrl+clicking line2 (already selected)
            useCase.selectLine(line2, isModifierPressed = true)

            // Then: Line2 is removed, line1 becomes primary
            val state = stateManager.state.value
            assertEquals(setOf(line1), state.content.selectedLines)
            assertEquals(line1.id, state.content.primarySelectedLineId)
        }

    @Test
    fun `selectLine with modifier on last selected line clears selection`() =
        runTest {
            // Given: A selection with only line1
            val line1 = TestFactories.createLine(id = 1L)

            stateManager.updateContent {
                copy(
                    selectedLines = setOf(line1),
                    primarySelectedLineId = line1.id,
                )
            }

            // When: Ctrl+clicking line1 (removing the last line)
            useCase.selectLine(line1, isModifierPressed = true)

            // Then: Selection is empty, primary is null
            val state = stateManager.state.value
            assertTrue(state.content.selectedLines.isEmpty())
            assertNull(state.content.primarySelectedLineId)
        }

    @Test
    fun `selectLine on TOC heading selects all section lines`() =
        runTest {
            // Given: A line that is a TOC heading
            val headingLine = TestFactories.createLine(id = 10L, lineIndex = 0)
            val tocEntry = TestFactories.createTocEntry(id = 5L, lineId = 10L, bookId = 100L)
            val sectionLineIds = listOf(10L, 11L, 12L, 13L, 14L)
            val sectionLines =
                sectionLineIds.mapIndexed { idx, id ->
                    TestFactories.createLine(id = id, lineIndex = idx)
                }

            coEvery { repository.getHeadingTocEntryByLineId(headingLine.id) } returns tocEntry
            coEvery { repository.getLineIdsForTocEntry(tocEntry.id) } returns sectionLineIds
            sectionLineIds.forEachIndexed { idx, id ->
                coEvery { repository.getLine(id) } returns sectionLines[idx]
            }
            coEvery { repository.getTocEntryIdForLine(headingLine.id) } returns tocEntry.id
            coEvery { repository.getTocEntry(tocEntry.id) } returns tocEntry

            // When: Selecting the heading line without modifier
            useCase.selectLine(headingLine, isModifierPressed = false)

            // Then: All section lines are selected
            val state = stateManager.state.value
            assertEquals(sectionLines.toSet(), state.content.selectedLines)
            assertEquals(headingLine.id, state.content.primarySelectedLineId)
            assertTrue(state.content.isTocEntrySelection)
        }

    @Test
    fun `selectLine on TOC heading limits selection to 128 lines`() =
        runTest {
            // Given: A TOC section with more than 128 lines
            val headingLine = TestFactories.createLine(id = 64L, lineIndex = 64)
            val tocEntry = TestFactories.createTocEntry(id = 5L, lineId = 64L, bookId = 100L)
            val totalLines = 200
            val allLineIds = (1L..totalLines.toLong()).toList()

            coEvery { repository.getHeadingTocEntryByLineId(headingLine.id) } returns tocEntry
            coEvery { repository.getLineIdsForTocEntry(tocEntry.id) } returns allLineIds
            allLineIds.forEach { id ->
                coEvery { repository.getLine(id) } returns
                    TestFactories.createLine(id = id, lineIndex = id.toInt() - 1)
            }
            coEvery { repository.getTocEntryIdForLine(headingLine.id) } returns tocEntry.id
            coEvery { repository.getTocEntry(tocEntry.id) } returns tocEntry

            // When: Selecting the heading line
            useCase.selectLine(headingLine, isModifierPressed = false)

            // Then: At most 128 lines are selected (sliding window around heading)
            val state = stateManager.state.value
            assertTrue(state.content.selectedLines.size <= 128)
            assertTrue(state.content.isTocEntrySelection)
            // The heading line should be in the selection
            assertTrue(state.content.selectedLines.any { it.id == headingLine.id })
        }

    @Test
    fun `selectLine on TOC heading with exactly 128 lines selects all`() =
        runTest {
            // Given: A TOC section with exactly 128 lines
            val headingLine = TestFactories.createLine(id = 1L, lineIndex = 0)
            val tocEntry = TestFactories.createTocEntry(id = 5L, lineId = 1L, bookId = 100L)
            val sectionLineIds = (1L..128L).toList()

            coEvery { repository.getHeadingTocEntryByLineId(headingLine.id) } returns tocEntry
            coEvery { repository.getLineIdsForTocEntry(tocEntry.id) } returns sectionLineIds
            sectionLineIds.forEach { id ->
                coEvery { repository.getLine(id) } returns
                    TestFactories.createLine(id = id, lineIndex = id.toInt() - 1)
            }
            coEvery { repository.getTocEntryIdForLine(headingLine.id) } returns tocEntry.id
            coEvery { repository.getTocEntry(tocEntry.id) } returns tocEntry

            // When: Selecting the heading line
            useCase.selectLine(headingLine, isModifierPressed = false)

            // Then: All 128 lines are selected
            val state = stateManager.state.value
            assertEquals(128, state.content.selectedLines.size)
        }

    @Test
    fun `selectLine on non-TOC line selects single line`() =
        runTest {
            // Given: A regular line (not a TOC heading)
            val regularLine = TestFactories.createLine(id = 50L)

            // When: Selecting the regular line
            useCase.selectLine(regularLine, isModifierPressed = false)

            // Then: Only the single line is selected
            val state = stateManager.state.value
            assertEquals(setOf(regularLine), state.content.selectedLines)
            assertEquals(regularLine.id, state.content.primarySelectedLineId)
            assertFalse(state.content.isTocEntrySelection)
        }

    @Test
    fun `selectLine updates TOC breadcrumb path`() =
        runTest {
            // Given: A line with a TOC entry
            val line = TestFactories.createLine(id = 10L)
            val tocEntry = TestFactories.createTocEntry(id = 5L, bookId = 100L, text = "Chapter 1")
            val parentToc = TestFactories.createTocEntry(id = 1L, bookId = 100L, text = "Part I")

            coEvery { repository.getTocEntryIdForLine(line.id) } returns tocEntry.id
            coEvery { repository.getTocEntry(tocEntry.id) } returns tocEntry.copy(parentId = parentToc.id)
            coEvery { repository.getTocEntry(parentToc.id) } returns parentToc

            // When: Selecting the line
            useCase.selectLine(line, isModifierPressed = false)

            // Then: The breadcrumb path is updated
            val state = stateManager.state.value
            assertEquals(tocEntry.id, state.toc.selectedEntryId)
            assertTrue(state.toc.breadcrumbPath.isNotEmpty())
        }

    @Test
    fun `selectLine handles repository exception gracefully`() =
        runTest {
            // Given: Repository throws exception for TOC lookup
            val line = TestFactories.createLine(id = 10L)
            coEvery { repository.getHeadingTocEntryByLineId(line.id) } throws RuntimeException("DB error")

            // When: Selecting the line (should not crash)
            useCase.selectLine(line, isModifierPressed = false)

            // Then: Line is selected despite the error (fallback behavior)
            val state = stateManager.state.value
            assertEquals(setOf(line), state.content.selectedLines)
        }

    @Test
    fun `selectLine with modifier preserves isTocEntrySelection as false`() =
        runTest {
            // Given: A TOC entry selection
            val line1 = TestFactories.createLine(id = 1L)
            val line2 = TestFactories.createLine(id = 2L)
            val line3 = TestFactories.createLine(id = 3L)

            stateManager.updateContent {
                copy(
                    selectedLines = setOf(line1, line2),
                    primarySelectedLineId = line1.id,
                    isTocEntrySelection = true, // Simulate a TOC selection
                )
            }

            // When: Ctrl+clicking to add line3
            useCase.selectLine(line3, isModifierPressed = true)

            // Then: isTocEntrySelection becomes false (manual selection)
            val state = stateManager.state.value
            assertEquals(setOf(line1, line2, line3), state.content.selectedLines)
            assertFalse(state.content.isTocEntrySelection)
        }

    @Test
    fun `primarySelectedLineId is always in selectedLines`() =
        runTest {
            // Given: Multiple lines
            val line1 = TestFactories.createLine(id = 1L)
            val line2 = TestFactories.createLine(id = 2L)

            // When: Adding lines via modifier clicks
            useCase.selectLine(line1, isModifierPressed = false)
            useCase.selectLine(line2, isModifierPressed = true)

            // Then: primarySelectedLineId is in selectedLines
            val state = stateManager.state.value
            val primaryId = state.content.primarySelectedLineId
            assertTrue(primaryId != null)
            assertTrue(state.content.selectedLines.any { it.id == primaryId })
        }

    @Test
    fun `selectedLineIds computed property matches selectedLines ids`() =
        runTest {
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
}
