package io.github.kdroidfilter.seforimapp.features.bookcontent

import androidx.lifecycle.SavedStateHandle
import io.github.kdroidfilter.seforim.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentStateManager
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.StateKeys
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.*
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for [BookContentViewModel].
 *
 * Note: Due to the complex dependencies (Repository requiring database, TabsViewModel, etc.),
 * these tests focus on state management and initialization behavior that can be tested
 * without a full database.
 */
class BookContentViewModelTest {
    private val testTabId = "test-tab-123"
    private val testBookId = 456L

    @Test
    fun `savedStateHandle extracts tabId correctly`() {
        // Given: A SavedStateHandle with tabId
        val savedStateHandle =
            SavedStateHandle(
                mapOf(StateKeys.TAB_ID to testTabId),
            )

        // When: We extract the tabId
        val tabId: String = savedStateHandle.get<String>(StateKeys.TAB_ID) ?: ""

        // Then: It matches the expected value
        assertEquals(testTabId, tabId)
    }

    @Test
    fun `savedStateHandle extracts bookId correctly`() {
        // Given: A SavedStateHandle with bookId
        val savedStateHandle =
            SavedStateHandle(
                mapOf(
                    StateKeys.TAB_ID to testTabId,
                    StateKeys.BOOK_ID to testBookId,
                ),
            )

        // When: We extract the bookId
        val bookId: Long? = savedStateHandle.get<Long>(StateKeys.BOOK_ID)

        // Then: It matches the expected value
        assertEquals(testBookId, bookId)
    }

    @Test
    fun `savedStateHandle extracts lineId correctly`() {
        // Given: A SavedStateHandle with lineId
        val lineId = 789L
        val savedStateHandle =
            SavedStateHandle(
                mapOf(
                    StateKeys.TAB_ID to testTabId,
                    StateKeys.LINE_ID to lineId,
                ),
            )

        // When: We extract the lineId
        val extractedLineId: Long? = savedStateHandle.get<Long>(StateKeys.LINE_ID)

        // Then: It matches the expected value
        assertEquals(lineId, extractedLineId)
    }

    @Test
    fun `stateManager initializes with correct tabId`() {
        // Given: A persisted store and state manager
        val persistedStore = TabPersistedStateStore()
        val stateManager = BookContentStateManager(testTabId, persistedStore)

        // When: We get the state
        val state = stateManager.state.value

        // Then: The tabId is correct
        assertEquals(testTabId, state.tabId)
    }

    @Test
    fun `stateManager navigation state is initially empty`() {
        // Given: A new state manager
        val persistedStore = TabPersistedStateStore()
        val stateManager = BookContentStateManager(testTabId, persistedStore)

        // When: We get the navigation state
        val navState = stateManager.state.value.navigation

        // Then: It has sensible defaults
        assertTrue(navState.rootCategories.isEmpty())
        assertTrue(navState.expandedCategories.isEmpty())
        assertEquals(null, navState.selectedBook)
        assertEquals(null, navState.selectedCategory)
    }

    @Test
    fun `stateManager toc state is initially empty`() {
        // Given: A new state manager
        val persistedStore = TabPersistedStateStore()
        val stateManager = BookContentStateManager(testTabId, persistedStore)

        // When: We get the TOC state
        val tocState = stateManager.state.value.toc

        // Then: It has sensible defaults
        assertTrue(tocState.entries.isEmpty())
        assertTrue(tocState.expandedEntries.isEmpty())
        assertEquals(null, tocState.selectedEntryId)
    }

    @Test
    fun `stateManager content state is initially empty`() {
        // Given: A new state manager
        val persistedStore = TabPersistedStateStore()
        val stateManager = BookContentStateManager(testTabId, persistedStore)

        // When: We get the content state
        val contentState = stateManager.state.value.content

        // Then: It has sensible defaults
        assertEquals(null, contentState.selectedLine)
        assertEquals(-1L, contentState.anchorId)
        assertEquals(0, contentState.scrollIndex)
        assertEquals(0, contentState.scrollOffset)
    }

    @Test
    fun `persistedStore updates are reflected in new state managers`() {
        // Given: A persisted store with some data
        val persistedStore = TabPersistedStateStore()
        persistedStore.update(testTabId) { current ->
            current.copy(
                bookContent =
                    current.bookContent.copy(
                        selectedBookId = testBookId,
                        isTocVisible = true,
                        isBookTreeVisible = false,
                    ),
            )
        }

        // When: We create a new state manager with the same tabId
        val stateManager = BookContentStateManager(testTabId, persistedStore)
        val state = stateManager.state.value

        // Then: The persisted values are loaded
        assertEquals(true, state.toc.isVisible)
        assertEquals(false, state.navigation.isVisible)
    }

    @Test
    fun `stateManager loading state can be set`() {
        // Given: A state manager
        val persistedStore = TabPersistedStateStore()
        val stateManager = BookContentStateManager(testTabId, persistedStore)

        // Initially not loading
        assertEquals(false, stateManager.state.value.isLoading)

        // When: We set loading to true
        stateManager.setLoading(true)

        // Then: State reflects loading
        assertEquals(true, stateManager.state.value.isLoading)

        // When: We set loading to false
        stateManager.setLoading(false)

        // Then: State reflects not loading
        assertEquals(false, stateManager.state.value.isLoading)
    }

    @Test
    fun `tabTitleUpdateManager can be instantiated`() {
        // Given/When: We create a TabTitleUpdateManager
        val manager = TabTitleUpdateManager()

        // Then: It's not null
        assertNotNull(manager)
    }

    @Test
    fun `tabsViewModel can be created with start destination`() {
        // Given: A title update manager
        val titleManager = TabTitleUpdateManager()
        val startDestination =
            TabsDestination.BookContent(
                bookId = testBookId,
                tabId = testTabId,
            )

        // When: We create a TabsViewModel
        val tabsViewModel =
            TabsViewModel(
                titleUpdateManager = titleManager,
                startDestination = startDestination,
            )

        // Then: It has one tab
        assertEquals(1, tabsViewModel.tabs.value.size)
    }

    @Test
    fun `BookContentEvent types are properly defined`() {
        // Test various event types can be created
        val categoryEvent =
            BookContentEvent.CategorySelected(
                Category(id = 1, parentId = null, title = "Test", order = 0),
            )
        assertNotNull(categoryEvent)

        val bookEvent =
            BookContentEvent.BookSelected(
                Book(id = 1, categoryId = 1, sourceId = 1, title = "Test", order = 0f),
            )
        assertNotNull(bookEvent)

        val lineEvent =
            BookContentEvent.LineSelected(
                Line(id = 1, bookId = 1, lineIndex = 0, content = "Test"),
            )
        assertNotNull(lineEvent)

        val toggleTocEvent = BookContentEvent.ToggleToc
        assertNotNull(toggleTocEvent)

        val toggleBookTreeEvent = BookContentEvent.ToggleBookTree
        assertNotNull(toggleBookTreeEvent)

        val toggleCommentariesEvent = BookContentEvent.ToggleCommentaries
        assertNotNull(toggleCommentariesEvent)
    }

    @Test
    fun `BookContentEvent LoadAndSelectLine contains lineId`() {
        // Given: A LoadAndSelectLine event
        val lineId = 123L
        val event = BookContentEvent.LoadAndSelectLine(lineId)

        // Then: The lineId is accessible
        assertEquals(lineId, event.lineId)
    }

    @Test
    fun `BookContentEvent OpenBookAtLine contains both bookId and lineId`() {
        // Given: An OpenBookAtLine event
        val bookId = 100L
        val lineId = 200L
        val event = BookContentEvent.OpenBookAtLine(bookId, lineId)

        // Then: Both IDs are accessible
        assertEquals(bookId, event.bookId)
        assertEquals(lineId, event.lineId)
    }

    @Test
    fun `BookContentEvent ContentScrolled contains scroll data`() {
        // Given: A ContentScrolled event
        val event =
            BookContentEvent.ContentScrolled(
                anchorId = 1L,
                anchorIndex = 5,
                scrollIndex = 10,
                scrollOffset = 50,
            )

        // Then: All data is accessible
        assertEquals(1L, event.anchorId)
        assertEquals(5, event.anchorIndex)
        assertEquals(10, event.scrollIndex)
        assertEquals(50, event.scrollOffset)
    }

    @Test
    fun `TocEntry has required properties`() {
        // Given: A TocEntry
        val entry =
            TocEntry(
                id = 1L,
                bookId = 100L,
                parentId = null,
                text = "Chapter 1",
                level = 0,
                hasChildren = true,
            )

        // Then: All properties are accessible
        assertEquals(1L, entry.id)
        assertEquals(100L, entry.bookId)
        assertEquals(null, entry.parentId)
        assertEquals("Chapter 1", entry.text)
        assertEquals(0, entry.level)
        assertTrue(entry.hasChildren)
    }

    @Test
    fun `BookContentEvent TocEntryExpanded contains entry`() {
        // Given: A TocEntry and event
        val entry =
            TocEntry(
                id = 1L,
                bookId = 100L,
                parentId = null,
                text = "Test",
                level = 0,
                hasChildren = true,
            )
        val event = BookContentEvent.TocEntryExpanded(entry)

        // Then: The entry is accessible
        assertEquals(entry, event.entry)
    }

    @Test
    fun `persistedStore remove clears tab data`() {
        // Given: A store with data
        val store = TabPersistedStateStore()
        store.update(testTabId) { current ->
            current.copy(
                bookContent = current.bookContent.copy(selectedBookId = testBookId),
            )
        }
        assertNotNull(store.get(testTabId))

        // When: We remove the tab
        store.remove(testTabId)

        // Then: Data is gone
        assertEquals(null, store.get(testTabId))
    }
}
