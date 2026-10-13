package io.github.kdroidfilter.seforimapp.features.bookcontent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A tab shown again selected nothing until a zoom or a resize: TabsContent unplaces hidden tabs, and since Compose
 * 1.12 a Text unsubscribes from its SelectionContainer when unplaced but was never told it is placed again. Fixed
 * by the build's PlacedSubtreePatch (buildSrc).
 */
@OptIn(ExperimentalTestApi::class)
class UnplacedSelectionUiTest {
    @Test
    fun `selection works after the subtree is unplaced and placed again`() =
        runComposeUiTest {
            val state = SelectionState()
            var shown by mutableStateOf(true)
            setContent {
                // Same trick as TabsContent: hidden = composed but neither measured nor placed
                Box(
                    Modifier.fillMaxSize().layout { measurable, constraints ->
                        if (shown) {
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                        } else {
                            layout(0, 0) {}
                        }
                    },
                ) {
                    SelectionContainer(state, Modifier.fillMaxSize()) {
                        BasicText("hello selection world", Modifier.fillMaxWidth().testTag("text"))
                    }
                }
            }

            val text = onNodeWithTag("text")
            assertEquals(1, runOnIdle { state.getSelectableTexts().size }, "registered before hiding")
            text.selectWord()
            assertTrue(runOnIdle { state.selectedTexts.joinToString() }.isNotEmpty(), "selection before hiding")

            runOnIdle { state.clear() }
            shown = false
            waitForIdle()
            shown = true
            waitForIdle()

            assertEquals(1, runOnIdle { state.getSelectableTexts().size }, "registered when shown again")
            text.selectWord()
            assertTrue(runOnIdle { state.selectedTexts.joinToString() }.isNotEmpty(), "selection when shown again")
        }

    private fun SemanticsNodeInteraction.selectWord() {
        performMouseInput { doubleClick(Offset(width * 0.45f, centerY)) }
    }
}
