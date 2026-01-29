package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import io.github.kdroidfilter.seforimapp.features.bookcontent.TestFactories
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentStateManager
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for commentator/link source selection state management.
 *
 * Note: Since SeforimRepository is a concrete class with database dependencies,
 * these tests focus on the state management aspects that can be tested without mocking.
 * The actual CommentariesUseCase reapply methods require repository access which is
 * tested through integration tests with a real database.
 */
class CommentariesUseCaseReapplyTest {
    private lateinit var persistedStore: TabPersistedStateStore
    private lateinit var stateManager: BookContentStateManager

    private val testTabId = "test-tab-reapply"
    private val testBookId = 100L

    @BeforeTest
    fun setup() {
        persistedStore = TabPersistedStateStore()
        stateManager = BookContentStateManager(testTabId, persistedStore)

        // Set up a selected book in state
        val book = TestFactories.createBook(id = testBookId)
        stateManager.updateNavigation {
            copy(selectedBook = book)
        }
    }

    // ==================== Commentator Selection State Tests ====================

    @Test
    fun `selectedCommentatorsByBook persists sticky commentators`() {
        // Given: Sticky commentators for a book
        val stickyIds = setOf(1L, 2L, 3L)

        // When: Setting sticky commentators
        stateManager.updateContent {
            copy(selectedCommentatorsByBook = mapOf(testBookId to stickyIds))
        }

        // Then: Sticky commentators are persisted
        val state = stateManager.state.value
        assertEquals(stickyIds, state.content.selectedCommentatorsByBook[testBookId])
    }

    @Test
    fun `selectedCommentatorsByLine tracks per-line selections`() {
        // Given: Per-line commentator selections
        val lineId1 = 10L
        val lineId2 = 20L
        val commentators1 = setOf(1L, 2L)
        val commentators2 = setOf(2L, 3L)

        // When: Setting per-line selections
        stateManager.updateContent {
            copy(
                selectedCommentatorsByLine =
                    mapOf(
                        lineId1 to commentators1,
                        lineId2 to commentators2,
                    ),
            )
        }

        // Then: Per-line selections are tracked
        val state = stateManager.state.value
        assertEquals(commentators1, state.content.selectedCommentatorsByLine[lineId1])
        assertEquals(commentators2, state.content.selectedCommentatorsByLine[lineId2])
    }

    @Test
    fun `empty selectedCommentatorsByBook returns empty set for book`() {
        // Given: No sticky commentators set

        // Then: Book has no sticky commentators
        val state = stateManager.state.value
        assertTrue(state.content.selectedCommentatorsByBook[testBookId].isNullOrEmpty())
    }

    @Test
    fun `MAX_COMMENTATORS limit can be enforced in state`() {
        // Given: More than 4 commentators
        val allCommentators = setOf(1L, 2L, 3L, 4L, 5L, 6L)
        val maxCommentators = 4

        // When: Limiting to MAX_COMMENTATORS
        val limited = allCommentators.take(maxCommentators).toSet()
        stateManager.updateContent {
            copy(selectedCommentatorsByLine = mapOf(10L to limited))
        }

        // Then: At most 4 commentators are stored
        val state = stateManager.state.value
        val lineCommentators = state.content.selectedCommentatorsByLine[10L] ?: emptySet()
        assertTrue(lineCommentators.size <= maxCommentators)
    }

    // ==================== Link Source Selection State Tests ====================

    @Test
    fun `selectedLinkSourcesByBook persists remembered sources`() {
        // Given: Remembered link sources for a book
        val rememberedIds = setOf(100L, 200L)

        // When: Setting remembered sources
        stateManager.updateContent {
            copy(selectedLinkSourcesByBook = mapOf(testBookId to rememberedIds))
        }

        // Then: Sources are persisted
        val state = stateManager.state.value
        assertEquals(rememberedIds, state.content.selectedLinkSourcesByBook[testBookId])
    }

    @Test
    fun `selectedLinkSourcesByLine tracks per-line sources`() {
        // Given: Per-line link source selections
        val lineId = 10L
        val sources = setOf(100L, 200L)

        // When: Setting per-line sources
        stateManager.updateContent {
            copy(selectedLinkSourcesByLine = mapOf(lineId to sources))
        }

        // Then: Per-line sources are tracked
        val state = stateManager.state.value
        assertEquals(sources, state.content.selectedLinkSourcesByLine[lineId])
    }

    // ==================== Source Selection State Tests ====================

    @Test
    fun `selectedSourcesByBook persists remembered sources`() {
        // Given: Remembered sources for a book
        val rememberedIds = setOf(500L, 600L)

        // When: Setting remembered sources
        stateManager.updateContent {
            copy(selectedSourcesByBook = mapOf(testBookId to rememberedIds))
        }

        // Then: Sources are persisted
        val state = stateManager.state.value
        assertEquals(rememberedIds, state.content.selectedSourcesByBook[testBookId])
    }

    @Test
    fun `selectedSourcesByLine tracks per-line sources`() {
        // Given: Per-line source selections
        val lineId = 10L
        val sources = setOf(500L)

        // When: Setting per-line sources
        stateManager.updateContent {
            copy(selectedSourcesByLine = mapOf(lineId to sources))
        }

        // Then: Per-line sources are tracked
        val state = stateManager.state.value
        assertEquals(sources, state.content.selectedSourcesByLine[lineId])
    }

    // ==================== Intersection Logic Tests ====================

    @Test
    fun `intersection of sticky and available commentators`() {
        // Given: Sticky commentators and available commentators
        val sticky = setOf(1L, 2L, 3L)
        val available = setOf(2L, 3L, 4L)

        // When: Computing intersection
        val intersection = sticky.intersect(available)

        // Then: Intersection contains common elements
        assertEquals(setOf(2L, 3L), intersection)
    }

    @Test
    fun `intersection with empty sticky returns empty`() {
        // Given: Empty sticky and some available
        val sticky = emptySet<Long>()
        val available = setOf(1L, 2L)

        // When: Computing intersection
        val intersection = sticky.intersect(available)

        // Then: Intersection is empty
        assertTrue(intersection.isEmpty())
    }

    @Test
    fun `intersection with no overlap returns empty`() {
        // Given: Disjoint sets
        val sticky = setOf(1L, 2L)
        val available = setOf(3L, 4L)

        // When: Computing intersection
        val intersection = sticky.intersect(available)

        // Then: Intersection is empty
        assertTrue(intersection.isEmpty())
    }

    // ==================== resetForNewBook Tests ====================

    @Test
    fun `resetForNewBook clears per-line selections`() {
        // Given: State with per-line selections
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
    fun `resetForNewBook clears current selection IDs`() {
        // Given: State with current selection IDs
        stateManager.updateContent {
            copy(
                selectedTargumSourceIds = setOf(100L),
                selectedSourceIds = setOf(500L),
            )
        }

        // When: Resetting for new book
        stateManager.resetForNewBook()

        // Then: Current selection IDs are cleared
        val state = stateManager.state.value
        assertTrue(state.content.selectedTargumSourceIds.isEmpty())
        assertTrue(state.content.selectedSourceIds.isEmpty())
    }
}
