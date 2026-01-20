package io.github.kdroidfilter.seforimapp.integration

import io.github.kdroidfilter.seforimapp.framework.session.BookContentPersistedState
import io.github.kdroidfilter.seforimapp.framework.session.SearchPersistedState
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedState
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for session persistence components.
 * Tests the TabPersistedStateStore and related state classes.
 */
class SessionPersistenceIntegrationTest {
    private lateinit var store: TabPersistedStateStore

    @BeforeTest
    fun setup() {
        store = TabPersistedStateStore()
    }

    // ==================== TabPersistedStateStore Basic Tests ====================

    @Test
    fun `get returns null for non-existent tab`() {
        assertNull(store.get("non-existent"))
    }

    @Test
    fun `getOrCreate creates new state for new tab`() {
        val state = store.getOrCreate("new-tab")
        assertNotNull(state)
    }

    @Test
    fun `getOrCreate returns same state for existing tab`() {
        val state1 = store.getOrCreate("tab-1")
        val state2 = store.getOrCreate("tab-1")
        assertEquals(state1, state2)
    }

    @Test
    fun `set stores state for tab`() {
        val state =
            TabPersistedState(
                bookContent = BookContentPersistedState(selectedBookId = 123),
            )
        store.set("tab-1", state)

        val retrieved = store.get("tab-1")
        assertNotNull(retrieved)
        assertEquals(123L, retrieved.bookContent.selectedBookId)
    }

    @Test
    fun `set overwrites existing state`() {
        store.set("tab-1", TabPersistedState(bookContent = BookContentPersistedState(selectedBookId = 100)))
        store.set("tab-1", TabPersistedState(bookContent = BookContentPersistedState(selectedBookId = 200)))

        val retrieved = store.get("tab-1")
        assertEquals(200L, retrieved?.bookContent?.selectedBookId)
    }

    // ==================== Update Tests ====================

    @Test
    fun `update transforms existing state`() {
        store.set("tab-1", TabPersistedState(bookContent = BookContentPersistedState(selectedBookId = 100)))

        store.update("tab-1") { current ->
            current.copy(bookContent = current.bookContent.copy(selectedBookId = 200))
        }

        val retrieved = store.get("tab-1")
        assertEquals(200L, retrieved?.bookContent?.selectedBookId)
    }

    @Test
    fun `update creates state if not exists`() {
        store.update("new-tab") { current ->
            current.copy(bookContent = current.bookContent.copy(selectedBookId = 500))
        }

        val retrieved = store.get("new-tab")
        assertNotNull(retrieved)
        assertEquals(500L, retrieved.bookContent.selectedBookId)
    }

    @Test
    fun `update preserves other state fields`() {
        store.set(
            "tab-1",
            TabPersistedState(
                bookContent =
                    BookContentPersistedState(
                        selectedBookId = 100,
                        isTocVisible = true,
                        isBookTreeVisible = false,
                    ),
            ),
        )

        store.update("tab-1") { current ->
            current.copy(bookContent = current.bookContent.copy(selectedBookId = 200))
        }

        val retrieved = store.get("tab-1")
        assertNotNull(retrieved)
        assertEquals(200L, retrieved.bookContent.selectedBookId)
        assertTrue(retrieved.bookContent.isTocVisible)
        assertEquals(false, retrieved.bookContent.isBookTreeVisible)
    }

    // ==================== Remove Tests ====================

    @Test
    fun `remove deletes tab state`() {
        store.set("tab-1", TabPersistedState())
        assertNotNull(store.get("tab-1"))

        store.remove("tab-1")

        assertNull(store.get("tab-1"))
    }

    @Test
    fun `remove non-existent tab does nothing`() {
        // Should not throw
        store.remove("non-existent")
        assertNull(store.get("non-existent"))
    }

    @Test
    fun `clearAll removes all states`() {
        store.set("tab-1", TabPersistedState())
        store.set("tab-2", TabPersistedState())
        store.set("tab-3", TabPersistedState())

        store.clearAll()

        assertNull(store.get("tab-1"))
        assertNull(store.get("tab-2"))
        assertNull(store.get("tab-3"))
    }

    // ==================== Snapshot and Restore Tests ====================

    @Test
    fun `snapshot returns copy of all states`() {
        store.set("tab-1", TabPersistedState(bookContent = BookContentPersistedState(selectedBookId = 100)))
        store.set("tab-2", TabPersistedState(bookContent = BookContentPersistedState(selectedBookId = 200)))

        val snapshot = store.snapshot()

        assertEquals(2, snapshot.size)
        assertEquals(100L, snapshot["tab-1"]?.bookContent?.selectedBookId)
        assertEquals(200L, snapshot["tab-2"]?.bookContent?.selectedBookId)
    }

    @Test
    fun `snapshot is independent of store modifications`() {
        store.set("tab-1", TabPersistedState(bookContent = BookContentPersistedState(selectedBookId = 100)))
        val snapshot = store.snapshot()

        store.set("tab-1", TabPersistedState(bookContent = BookContentPersistedState(selectedBookId = 999)))

        assertEquals(100L, snapshot["tab-1"]?.bookContent?.selectedBookId)
    }

    @Test
    fun `restore replaces all states`() {
        store.set("old-tab", TabPersistedState())

        val newStates =
            mapOf(
                "tab-1" to TabPersistedState(bookContent = BookContentPersistedState(selectedBookId = 100)),
                "tab-2" to TabPersistedState(bookContent = BookContentPersistedState(selectedBookId = 200)),
            )

        store.restore(newStates)

        assertNull(store.get("old-tab"))
        assertEquals(100L, store.get("tab-1")?.bookContent?.selectedBookId)
        assertEquals(200L, store.get("tab-2")?.bookContent?.selectedBookId)
    }

    @Test
    fun `restore with empty map clears all states`() {
        store.set("tab-1", TabPersistedState())
        store.set("tab-2", TabPersistedState())

        store.restore(emptyMap())

        assertNull(store.get("tab-1"))
        assertNull(store.get("tab-2"))
    }

    // ==================== BookContentPersistedState Tests ====================

    @Test
    fun `BookContentPersistedState has sensible defaults`() {
        val state = BookContentPersistedState()

        assertEquals(-1L, state.selectedBookId)
        assertEquals(-1L, state.selectedLineId)
        assertEquals(-1L, state.contentAnchorLineId)
        assertEquals(0, state.contentScrollIndex)
        assertEquals(0, state.contentScrollOffset)
        assertEquals(false, state.isTocVisible)
        assertTrue(state.isBookTreeVisible)
        assertEquals(false, state.showCommentaries)
    }

    @Test
    fun `BookContentPersistedState copy preserves all fields`() {
        val original =
            BookContentPersistedState(
                selectedBookId = 100,
                selectedLineId = 200,
                contentAnchorLineId = 300,
                contentScrollIndex = 10,
                contentScrollOffset = 50,
                isTocVisible = true,
                isBookTreeVisible = false,
                showCommentaries = true,
            )

        val copy = original.copy(selectedBookId = 999)

        assertEquals(999L, copy.selectedBookId)
        assertEquals(200L, copy.selectedLineId)
        assertEquals(300L, copy.contentAnchorLineId)
        assertEquals(10, copy.contentScrollIndex)
        assertEquals(50, copy.contentScrollOffset)
        assertTrue(copy.isTocVisible)
        assertEquals(false, copy.isBookTreeVisible)
        assertTrue(copy.showCommentaries)
    }

    // ==================== SearchPersistedState Tests ====================

    @Test
    fun `SearchPersistedState has sensible defaults`() {
        val state = SearchPersistedState()

        assertEquals("", state.query)
        assertEquals(0, state.scrollIndex)
        assertEquals(0, state.scrollOffset)
        assertEquals(-1L, state.anchorId)
    }

    @Test
    fun `SearchPersistedState copy preserves all fields`() {
        val original =
            SearchPersistedState(
                query = "Torah",
                scrollIndex = 100,
                scrollOffset = 50,
                anchorId = 12345,
            )

        val copy = original.copy(query = "Talmud")

        assertEquals("Talmud", copy.query)
        assertEquals(100, copy.scrollIndex)
        assertEquals(50, copy.scrollOffset)
        assertEquals(12345L, copy.anchorId)
    }

    // ==================== TabPersistedState Tests ====================

    @Test
    fun `TabPersistedState combines book and search state`() {
        val state =
            TabPersistedState(
                bookContent = BookContentPersistedState(selectedBookId = 100),
                search = SearchPersistedState(query = "test"),
            )

        assertEquals(100L, state.bookContent.selectedBookId)
        assertEquals("test", state.search?.query)
    }

    @Test
    fun `TabPersistedState copy updates specific sub-state`() {
        val original =
            TabPersistedState(
                bookContent = BookContentPersistedState(selectedBookId = 100),
                search = SearchPersistedState(query = "test"),
            )

        val updated =
            original.copy(
                bookContent = original.bookContent.copy(selectedBookId = 200),
            )

        assertEquals(200L, updated.bookContent.selectedBookId)
        assertEquals("test", updated.search?.query)
    }

    // ==================== Concurrency Tests ====================

    @Test
    fun `concurrent updates do not lose data`() {
        val tabId = "concurrent-tab"
        val threads = mutableListOf<Thread>()

        // Create multiple threads that update different parts of the state
        repeat(10) { i ->
            threads.add(
                Thread {
                    store.update(tabId) { current ->
                        current.copy(
                            bookContent =
                                current.bookContent.copy(
                                    contentScrollIndex = current.bookContent.contentScrollIndex + 1,
                                ),
                        )
                    }
                },
            )
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        val finalState = store.get(tabId)
        assertNotNull(finalState)
        assertEquals(10, finalState.bookContent.contentScrollIndex)
    }

    // ==================== Multiple Tab Integration Tests ====================

    @Test
    fun `multiple tabs maintain independent state`() {
        store.set(
            "tab-book",
            TabPersistedState(
                bookContent = BookContentPersistedState(selectedBookId = 100),
            ),
        )
        store.set(
            "tab-search",
            TabPersistedState(
                search = SearchPersistedState(query = "Torah"),
            ),
        )

        // Update one tab shouldn't affect the other
        store.update("tab-book") { current ->
            current.copy(bookContent = current.bookContent.copy(selectedBookId = 200))
        }

        assertEquals(200L, store.get("tab-book")?.bookContent?.selectedBookId)
        assertEquals("Torah", store.get("tab-search")?.search?.query)
    }

    @Test
    fun `complex session scenario with multiple operations`() {
        // Simulate a complex user session

        // 1. User opens first tab with a book
        store.set(
            "tab-1",
            TabPersistedState(
                bookContent =
                    BookContentPersistedState(
                        selectedBookId = 100,
                        isTocVisible = true,
                    ),
            ),
        )

        // 2. User scrolls in the book
        store.update("tab-1") { current ->
            current.copy(
                bookContent =
                    current.bookContent.copy(
                        contentScrollIndex = 50,
                        contentScrollOffset = 100,
                    ),
            )
        }

        // 3. User opens a search tab
        store.set(
            "tab-2",
            TabPersistedState(
                search = SearchPersistedState(query = "Torah"),
            ),
        )

        // 4. User selects a line in the book
        store.update("tab-1") { current ->
            current.copy(bookContent = current.bookContent.copy(selectedLineId = 500))
        }

        // 5. Take a snapshot (simulating session save)
        val snapshot = store.snapshot()

        // 6. Clear and restore (simulating app restart)
        store.clearAll()
        store.restore(snapshot)

        // Verify all state is preserved
        val tab1State = store.get("tab-1")
        assertNotNull(tab1State)
        assertEquals(100L, tab1State.bookContent.selectedBookId)
        assertEquals(50, tab1State.bookContent.contentScrollIndex)
        assertEquals(100, tab1State.bookContent.contentScrollOffset)
        assertEquals(500L, tab1State.bookContent.selectedLineId)
        assertTrue(tab1State.bookContent.isTocVisible)

        val tab2State = store.get("tab-2")
        assertNotNull(tab2State)
        assertEquals("Torah", tab2State.search?.query)
    }
}
