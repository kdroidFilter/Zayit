package io.github.kdroidfilter.seforimapp.features.bookcontent

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Zayit #48: Shift+click after the anchor of a collapsed selection was scrolled out of a lazy list crashed
 * in SelectionManager. Fixed by the build's SelectionPinPatch (buildSrc), which pins collapsed selections.
 */
@OptIn(ExperimentalTestApi::class)
class ShiftClickSelectionUiTest {
    @Test
    fun `shift click after the anchor scrolled out does not crash`() =
        runComposeUiTest {
            val listState = LazyListState()
            setContent {
                SelectionContainer(Modifier.fillMaxSize()) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(500, key = { it }) { i ->
                            BasicText("line $i", Modifier.fillMaxWidth().testTag("l$i"))
                        }
                    }
                }
            }
            onNodeWithTag("l0").performMouseInput { click(centerLeft) }
            runBlocking { listState.scrollToItem(200) }
            waitForIdle()
            onNodeWithTag("l200").performKeyInput {
                withKeyDown(Key.ShiftLeft) { onNodeWithTag("l200").performMouseInput { click(centerRight) } }
            }
            waitForIdle()
        }
}
