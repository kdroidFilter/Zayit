package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentStateManager
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import io.mockk.mockk
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Commentaries and sources share the bottom dock: both can be open, with one shared height. */
@OptIn(ExperimentalSplitPaneApi::class)
class ContentUseCaseBottomPanesTest {
    private lateinit var stateManager: BookContentStateManager
    private lateinit var useCase: ContentUseCase

    private val split get() = stateManager.state.value.layout.contentSplitState

    @BeforeTest
    fun setup() {
        stateManager = BookContentStateManager("test-tab-bottom-panes", TabPersistedStateStore())
        useCase = ContentUseCase(mockk(), stateManager)
    }

    @Test
    fun `commentaries and sources can be open together`() {
        useCase.toggleCommentaries()
        useCase.toggleSources()

        val content = stateManager.state.value.content
        assertTrue(content.showCommentaries)
        assertTrue(content.showSources)
    }

    @Test
    fun `opening the second pane keeps the bottom height`() {
        useCase.toggleCommentaries()
        split.positionPercentage = 0.6f

        useCase.toggleSources()

        assertEquals(0.6f, split.positionPercentage)
    }

    @Test
    fun `the bottom dock collapses only when its last pane closes`() {
        useCase.toggleCommentaries()
        useCase.toggleSources()
        split.positionPercentage = 0.6f

        useCase.toggleCommentaries()
        assertEquals(0.6f, split.positionPercentage)

        useCase.toggleSources()
        assertEquals(1f, split.positionPercentage)
        assertFalse(stateManager.state.value.content.showSources)
    }

    @Test
    fun `reopening an empty bottom dock restores the pane's last height`() {
        useCase.toggleSources()
        split.positionPercentage = 0.75f
        useCase.toggleSources()

        useCase.toggleSources()

        assertEquals(0.75f, split.positionPercentage)
    }
}
