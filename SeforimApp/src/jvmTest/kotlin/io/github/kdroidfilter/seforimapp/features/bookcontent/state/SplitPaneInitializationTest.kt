@file:OptIn(ExperimentalSplitPaneApi::class)

package io.github.kdroidfilter.seforimapp.features.bookcontent.state

import io.github.kdroidfilter.seforimapp.framework.session.BookContentPersistedState
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests that [BookContentStateManager] initializes [SplitPaneState] with correct
 * position and moveEnabled values based on panel visibility flags.
 *
 * This prevents visible gaps on the first frame when hidden panels have non-collapsed
 * split positions.
 */
class SplitPaneInitializationTest {
    private lateinit var persistedStore: TabPersistedStateStore

    private val testTabId = "test-tab-split"

    @BeforeTest
    fun setup() {
        persistedStore = TabPersistedStateStore()
    }

    // ==================== Default (no persisted state) ====================

    @Test
    fun `new tab initializes mainSplitState with nav visible and default position`() {
        // Given: No persisted state (new tab) — isBookTreeVisible defaults to true
        val manager = BookContentStateManager(testTabId, persistedStore)
        val layout = manager.state.value.layout

        // Then: Nav is visible, position is the default, drag is enabled
        assertEquals(SplitDefaults.MAIN, layout.mainSplitState.positionPercentage)
        assertTrue(layout.mainSplitState.moveEnabled)
    }

    @Test
    fun `new tab initializes tocSplitState collapsed because TOC is hidden by default`() {
        // Given: No persisted state — isTocVisible defaults to false
        val manager = BookContentStateManager(testTabId, persistedStore)
        val layout = manager.state.value.layout

        // Then: TOC is hidden, position is 0 (first pane collapsed), drag is disabled
        assertEquals(0f, layout.tocSplitState.positionPercentage)
        assertFalse(layout.tocSplitState.moveEnabled)
    }

    @Test
    fun `new tab initializes contentSplitState expanded because commentaries are hidden by default`() {
        // Given: No persisted state — showCommentaries and showSources default to false
        val manager = BookContentStateManager(testTabId, persistedStore)
        val layout = manager.state.value.layout

        // Then: Bottom pane is hidden, position is 1 (first pane takes full space), drag is disabled
        assertEquals(1f, layout.contentSplitState.positionPercentage)
        assertFalse(layout.contentSplitState.moveEnabled)
    }

    @Test
    fun `new tab initializes targumSplitState expanded because targum is hidden by default`() {
        // Given: No persisted state — showTargum defaults to false
        val manager = BookContentStateManager(testTabId, persistedStore)
        val layout = manager.state.value.layout

        // Then: Targum pane is hidden, position is 1, drag is disabled
        assertEquals(1f, layout.targumSplitState.positionPercentage)
        assertFalse(layout.targumSplitState.moveEnabled)
    }

    // ==================== Restored state with panels visible ====================

    @Test
    fun `restored state with TOC visible uses persisted position`() {
        // Given: Persisted state with TOC visible
        val savedPosition = 0.15f
        persistedStore.update(testTabId) { current ->
            current.copy(
                bookContent =
                    current.bookContent.copy(
                        isTocVisible = true,
                        tocSplitPosition = savedPosition,
                    ),
            )
        }

        // When: Creating state manager
        val manager = BookContentStateManager(testTabId, persistedStore)
        val layout = manager.state.value.layout

        // Then: Uses saved position, drag enabled
        assertEquals(savedPosition, layout.tocSplitState.positionPercentage)
        assertTrue(layout.tocSplitState.moveEnabled)
    }

    @Test
    fun `restored state with commentaries visible uses persisted position`() {
        // Given: Persisted state with commentaries visible
        val savedPosition = 0.65f
        persistedStore.update(testTabId) { current ->
            current.copy(
                bookContent =
                    current.bookContent.copy(
                        showCommentaries = true,
                        contentSplitPosition = savedPosition,
                    ),
            )
        }

        // When: Creating state manager
        val manager = BookContentStateManager(testTabId, persistedStore)
        val layout = manager.state.value.layout

        // Then: Uses saved position, drag enabled
        assertEquals(savedPosition, layout.contentSplitState.positionPercentage)
        assertTrue(layout.contentSplitState.moveEnabled)
    }

    @Test
    fun `restored state with sources visible uses persisted content position`() {
        // Given: Sources visible (also uses contentSplitState)
        val savedPosition = 0.8f
        persistedStore.update(testTabId) { current ->
            current.copy(
                bookContent =
                    current.bookContent.copy(
                        showSources = true,
                        contentSplitPosition = savedPosition,
                    ),
            )
        }

        // When: Creating state manager
        val manager = BookContentStateManager(testTabId, persistedStore)
        val layout = manager.state.value.layout

        // Then: Content split is enabled because sources are visible
        assertEquals(savedPosition, layout.contentSplitState.positionPercentage)
        assertTrue(layout.contentSplitState.moveEnabled)
    }

    @Test
    fun `restored state with targum visible uses persisted position`() {
        // Given: Persisted state with targum visible
        val savedPosition = 0.75f
        persistedStore.update(testTabId) { current ->
            current.copy(
                bookContent =
                    current.bookContent.copy(
                        showTargum = true,
                        targumSplitPosition = savedPosition,
                    ),
            )
        }

        // When: Creating state manager
        val manager = BookContentStateManager(testTabId, persistedStore)
        val layout = manager.state.value.layout

        // Then: Uses saved position, drag enabled
        assertEquals(savedPosition, layout.targumSplitState.positionPercentage)
        assertTrue(layout.targumSplitState.moveEnabled)
    }

    // ==================== Restored state with panels hidden ====================

    @Test
    fun `restored state with nav hidden collapses main split to 0`() {
        // Given: Nav hidden with a non-zero persisted position
        persistedStore.update(testTabId) { current ->
            current.copy(
                bookContent =
                    current.bookContent.copy(
                        isBookTreeVisible = false,
                        mainSplitPosition = 0.2f,
                    ),
            )
        }

        // When: Creating state manager
        val manager = BookContentStateManager(testTabId, persistedStore)
        val layout = manager.state.value.layout

        // Then: Position is overridden to 0, drag disabled
        assertEquals(0f, layout.mainSplitState.positionPercentage)
        assertFalse(layout.mainSplitState.moveEnabled)
    }

    @Test
    fun `restored state with TOC hidden collapses toc split to 0`() {
        // Given: TOC hidden with a non-zero persisted position
        persistedStore.update(testTabId) { current ->
            current.copy(
                bookContent =
                    current.bookContent.copy(
                        isTocVisible = false,
                        tocSplitPosition = 0.15f,
                    ),
            )
        }

        // When: Creating state manager
        val manager = BookContentStateManager(testTabId, persistedStore)
        val layout = manager.state.value.layout

        // Then: Position is overridden to 0, drag disabled
        assertEquals(0f, layout.tocSplitState.positionPercentage)
        assertFalse(layout.tocSplitState.moveEnabled)
    }

    @Test
    fun `restored state with commentaries hidden expands content split to 1`() {
        // Given: Both commentaries and sources hidden, but position was saved at 0.7
        persistedStore.update(testTabId) { current ->
            current.copy(
                bookContent =
                    current.bookContent.copy(
                        showCommentaries = false,
                        showSources = false,
                        contentSplitPosition = 0.7f,
                    ),
            )
        }

        // When: Creating state manager
        val manager = BookContentStateManager(testTabId, persistedStore)
        val layout = manager.state.value.layout

        // Then: Position is overridden to 1 (first pane takes all space), drag disabled
        assertEquals(1f, layout.contentSplitState.positionPercentage)
        assertFalse(layout.contentSplitState.moveEnabled)
    }

    @Test
    fun `restored state with targum hidden expands targum split to 1`() {
        // Given: Targum hidden, but position was saved at 0.8
        persistedStore.update(testTabId) { current ->
            current.copy(
                bookContent =
                    current.bookContent.copy(
                        showTargum = false,
                        targumSplitPosition = 0.8f,
                    ),
            )
        }

        // When: Creating state manager
        val manager = BookContentStateManager(testTabId, persistedStore)
        val layout = manager.state.value.layout

        // Then: Position is overridden to 1, drag disabled
        assertEquals(1f, layout.targumSplitState.positionPercentage)
        assertFalse(layout.targumSplitState.moveEnabled)
    }

    // ==================== previousPositions are always preserved ====================

    @Test
    fun `previousPositions are restored regardless of visibility`() {
        // Given: Hidden panels with saved previous positions
        val prevMain = 0.12f
        val prevToc = 0.08f
        val prevContent = 0.65f
        val prevSources = 0.9f
        val prevTargum = 0.85f

        persistedStore.update(testTabId) { current ->
            current.copy(
                bookContent =
                    current.bookContent.copy(
                        isBookTreeVisible = false,
                        isTocVisible = false,
                        showCommentaries = false,
                        showTargum = false,
                        previousMainSplitPosition = prevMain,
                        previousTocSplitPosition = prevToc,
                        previousContentSplitPosition = prevContent,
                        previousSourcesSplitPosition = prevSources,
                        previousTargumSplitPosition = prevTargum,
                    ),
            )
        }

        // When: Creating state manager
        val manager = BookContentStateManager(testTabId, persistedStore)
        val positions = manager.state.value.layout.previousPositions

        // Then: Previous positions are always preserved (used to restore when toggling back)
        assertEquals(prevMain, positions.main)
        assertEquals(prevToc, positions.toc)
        assertEquals(prevContent, positions.content)
        assertEquals(prevSources, positions.sources)
        assertEquals(prevTargum, positions.links)
    }

    // ==================== Save-restore round trip ====================

    @Test
    fun `save then restore preserves split positions for visible panels`() {
        // Given: A manager with all panels visible and custom positions
        persistedStore.update(testTabId) { current ->
            current.copy(
                bookContent =
                    BookContentPersistedState(
                        isBookTreeVisible = true,
                        isTocVisible = true,
                        showCommentaries = true,
                        showTargum = true,
                        mainSplitPosition = 0.1f,
                        tocSplitPosition = 0.12f,
                        contentSplitPosition = 0.6f,
                        targumSplitPosition = 0.75f,
                    ),
            )
        }
        val manager = BookContentStateManager(testTabId, persistedStore)

        // When: Adjusting positions and saving
        manager.state.value.layout.mainSplitState.positionPercentage = 0.15f
        manager.state.value.layout.tocSplitState.positionPercentage = 0.18f
        manager.saveAllStates()

        // Then: New manager loads the adjusted positions
        val restored = BookContentStateManager(testTabId, persistedStore)
        val layout = restored.state.value.layout
        assertEquals(0.15f, layout.mainSplitState.positionPercentage)
        assertEquals(0.18f, layout.tocSplitState.positionPercentage)
        assertTrue(layout.mainSplitState.moveEnabled)
        assertTrue(layout.tocSplitState.moveEnabled)
    }
}
