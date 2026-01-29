package io.github.kdroidfilter.seforimapp.features.bookcontent.state

import io.github.kdroidfilter.seforimapp.features.bookcontent.TestFactories
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for state persistence in [BookContentStateManager].
 * Verifies that line selection state is correctly saved and restored.
 */
class BookContentStateManagerPersistenceTest {
    private lateinit var persistedStore: TabPersistedStateStore
    private lateinit var stateManager: BookContentStateManager

    private val testTabId = "test-tab-persistence"

    @BeforeTest
    fun setup() {
        persistedStore = TabPersistedStateStore()
        stateManager = BookContentStateManager(testTabId, persistedStore)
    }

    // ==================== saveAllStates ====================

    @Test
    fun `saveAllStates persists selectedLineIds`() {
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

        // When: Saving state (happens automatically with updateContent)
        stateManager.saveAllStates()

        // Then: Persisted state contains the line IDs
        val persisted = persistedStore.get(testTabId)?.bookContent
        assertEquals(setOf(10L, 20L, 30L), persisted?.selectedLineIds)
    }

    @Test
    fun `saveAllStates persists primarySelectedLineId`() {
        // Given: Selection with a primary line
        val line = TestFactories.createLine(id = 42L)

        stateManager.updateContent {
            copy(
                selectedLines = setOf(line),
                primarySelectedLineId = line.id,
            )
        }

        // When: Saving state
        stateManager.saveAllStates()

        // Then: Primary line ID is persisted
        val persisted = persistedStore.get(testTabId)?.bookContent
        assertEquals(42L, persisted?.primarySelectedLineId)
    }

    @Test
    fun `saveAllStates persists isTocEntrySelection`() {
        // Given: TOC entry selection
        val line = TestFactories.createLine(id = 1L)

        stateManager.updateContent {
            copy(
                selectedLines = setOf(line),
                primarySelectedLineId = line.id,
                isTocEntrySelection = true,
            )
        }

        // When: Saving state
        stateManager.saveAllStates()

        // Then: Flag is persisted
        val persisted = persistedStore.get(testTabId)?.bookContent
        assertTrue(persisted?.isTocEntrySelection == true)
    }

    @Test
    fun `saveAllStates persists isTocEntrySelection as false`() {
        // Given: Manual selection (not TOC entry)
        val line = TestFactories.createLine(id = 1L)

        stateManager.updateContent {
            copy(
                selectedLines = setOf(line),
                primarySelectedLineId = line.id,
                isTocEntrySelection = false,
            )
        }

        // When: Saving state
        stateManager.saveAllStates()

        // Then: Flag is persisted as false
        val persisted = persistedStore.get(testTabId)?.bookContent
        assertFalse(persisted?.isTocEntrySelection == true)
    }

    @Test
    fun `saveAllStates persists null primarySelectedLineId as -1`() {
        // Given: Empty selection
        stateManager.updateContent {
            copy(
                selectedLines = emptySet(),
                primarySelectedLineId = null,
            )
        }

        // When: Saving state
        stateManager.saveAllStates()

        // Then: Null is stored as -1L
        val persisted = persistedStore.get(testTabId)?.bookContent
        assertEquals(-1L, persisted?.primarySelectedLineId)
    }

    // ==================== loadInitialState ====================

    @Test
    fun `loadInitialState restores selection flags`() {
        // Given: Persisted state with selection
        persistedStore.update(testTabId) { current ->
            current.copy(
                bookContent =
                    current.bookContent.copy(
                        selectedLineIds = setOf(10L, 20L),
                        primarySelectedLineId = 10L,
                        isTocEntrySelection = true,
                    ),
            )
        }

        // When: Creating a new state manager (which loads initial state)
        val newStateManager = BookContentStateManager(testTabId, persistedStore)

        // Then: Flags are restored
        val state = newStateManager.state.value
        assertEquals(10L, state.content.primarySelectedLineId)
        assertTrue(state.content.isTocEntrySelection)
        // Note: selectedLines will be empty because Line objects aren't persisted
        // They need to be reloaded from the repository using selectedLineIds
    }

    @Test
    fun `loadInitialState handles -1 as null for primarySelectedLineId`() {
        // Given: Persisted state with -1L for primary ID
        persistedStore.update(testTabId) { current ->
            current.copy(
                bookContent =
                    current.bookContent.copy(
                        selectedLineIds = emptySet(),
                        primarySelectedLineId = -1L,
                    ),
            )
        }

        // When: Creating a new state manager
        val newStateManager = BookContentStateManager(testTabId, persistedStore)

        // Then: Primary ID is null
        val state = newStateManager.state.value
        assertNull(state.content.primarySelectedLineId)
    }

    @Test
    fun `loadInitialState handles positive primarySelectedLineId`() {
        // Given: Persisted state with valid primary ID
        persistedStore.update(testTabId) { current ->
            current.copy(
                bookContent =
                    current.bookContent.copy(
                        primarySelectedLineId = 100L,
                    ),
            )
        }

        // When: Creating a new state manager
        val newStateManager = BookContentStateManager(testTabId, persistedStore)

        // Then: Primary ID is restored
        val state = newStateManager.state.value
        assertEquals(100L, state.content.primarySelectedLineId)
    }

    @Test
    fun `loadInitialState handles 0 as valid primarySelectedLineId`() {
        // Given: Persisted state with 0L primary ID (edge case - unlikely but valid)
        persistedStore.update(testTabId) { current ->
            current.copy(
                bookContent =
                    current.bookContent.copy(
                        primarySelectedLineId = 0L,
                    ),
            )
        }

        // When: Creating a new state manager
        val newStateManager = BookContentStateManager(testTabId, persistedStore)

        // Then: Primary ID is null (0 is treated same as -1 for "no selection")
        // Based on the code: takeIf { it > 0 }
        val state = newStateManager.state.value
        assertNull(state.content.primarySelectedLineId)
    }

    // ==================== resetForNewBook ====================

    @Test
    fun `resetForNewBook clears selection`() {
        // Given: State with selection
        val line1 = TestFactories.createLine(id = 10L)
        val line2 = TestFactories.createLine(id = 20L)

        stateManager.updateContent {
            copy(
                selectedLines = setOf(line1, line2),
                primarySelectedLineId = line1.id,
                isTocEntrySelection = true,
            )
        }

        // When: Resetting for new book
        stateManager.resetForNewBook()

        // Then: Selection is cleared
        val state = stateManager.state.value
        assertTrue(state.content.selectedLines.isEmpty())
        assertNull(state.content.primarySelectedLineId)
        // Note: isTocEntrySelection is not explicitly reset by resetForNewBook
    }

    @Test
    fun `resetForNewBook clears per-line selections`() {
        // Given: State with per-line commentator selections
        stateManager.updateContent {
            copy(
                selectedCommentatorsByLine = mapOf(10L to setOf(1L, 2L)),
                selectedLinkSourcesByLine = mapOf(10L to setOf(100L)),
                selectedSourcesByLine = mapOf(10L to setOf(500L)),
            )
        }

        // When: Resetting for new book
        stateManager.resetForNewBook()

        // Then: Per-line selections are cleared
        val state = stateManager.state.value
        assertTrue(state.content.selectedCommentatorsByLine.isEmpty())
        assertTrue(state.content.selectedLinkSourcesByLine.isEmpty())
        assertTrue(state.content.selectedSourcesByLine.isEmpty())
    }

    @Test
    fun `resetForNewBook resets scroll positions`() {
        // Given: State with scroll positions
        stateManager.updateContent {
            copy(
                scrollIndex = 50,
                scrollOffset = 100,
                anchorId = 42L,
            )
        }

        // When: Resetting for new book
        stateManager.resetForNewBook()

        // Then: Scroll positions are reset
        val state = stateManager.state.value
        assertEquals(0, state.content.scrollIndex)
        assertEquals(0, state.content.scrollOffset)
        assertEquals(-1L, state.content.anchorId)
    }

    // ==================== updateContent save parameter ====================

    @Test
    fun `updateContent with save=true persists immediately`() {
        // Given: Initial state
        val line = TestFactories.createLine(id = 99L)

        // When: Updating with save=true (default)
        stateManager.updateContent(save = true) {
            copy(
                selectedLines = setOf(line),
                primarySelectedLineId = line.id,
            )
        }

        // Then: State is persisted
        val persisted = persistedStore.get(testTabId)?.bookContent
        assertEquals(99L, persisted?.primarySelectedLineId)
    }

    @Test
    fun `updateContent with save=false does not persist immediately`() {
        // Given: Initial state
        val line = TestFactories.createLine(id = 88L)

        // First persist a known value
        stateManager.updateContent(save = true) {
            copy(primarySelectedLineId = 1L)
        }

        // When: Updating with save=false
        stateManager.updateContent(save = false) {
            copy(
                selectedLines = setOf(line),
                primarySelectedLineId = line.id,
            )
        }

        // Then: In-memory state is updated
        assertEquals(88L, stateManager.state.value.content.primarySelectedLineId)

        // But persisted state still has old value
        val persisted = persistedStore.get(testTabId)?.bookContent
        assertEquals(1L, persisted?.primarySelectedLineId)
    }

    // ==================== Cross-tab isolation ====================

    @Test
    fun `different tabs have isolated state`() {
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
