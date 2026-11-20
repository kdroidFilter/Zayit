package io.github.kdroidfilter.seforimapp.features.bookcontent

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.text.TextLayoutResult
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.BookSelection
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.BookSelectionContainer
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.TextPoint
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.moveFocusByKey
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.selectableLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Shift+arrows adjust the book view's selection, as in a text editor: lines recording their presses and
 * layouts as the book view's do, and the container's key handler moving the focus first, as BookContentView's does.
 */
@OptIn(ExperimentalTestApi::class)
class ShiftArrowSelectionUiTest {
    private val lines = listOf("בראשית ברא אלהים", "את השמים ואת הארץ", "והארץ היתה תהו ובהו")

    private fun ComposeUiTest.setBook(
        selection: BookSelection,
        plainKeys: MutableList<Key>,
    ) {
        val focusRequester = FocusRequester()
        setContent {
            BookSelectionContainer(
                selection = selection,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { event ->
                            when {
                                selection.moveFocusByKey(event) { lines.getOrNull(it) } -> true
                                event.type == KeyEventType.KeyDown -> {
                                    plainKeys += event.key
                                    false
                                }
                                else -> false
                            }
                        }.focusable(),
            ) {
                Column {
                    lines.forEachIndexed { i, text ->
                        val owner = remember { Any() }
                        val layout = remember { arrayOfNulls<TextLayoutResult>(1) }
                        BasicText(
                            text,
                            Modifier
                                .fillMaxWidth()
                                .testTag("l$i")
                                .selectableLine(selection, i) { layout[0] }
                                .onPlaced { selection.publishCoordinates(i, owner, it) },
                            onTextLayout = {
                                layout[0] = it
                                selection.publishLayout(i, owner, it)
                            },
                        )
                    }
                }
            }
        }
        waitForIdle()
        focusRequester.requestFocus()
        waitForIdle()
    }

    /**
     * A click in the middle of line 0 then a one-character extension past it, as a Shift+click makes (that one's
     * pointer path is ShiftClickSelectionUiTest's): the anchor at the click, the focus one character later.
     */
    private fun ComposeUiTest.selectInFirstLine(selection: BookSelection): Pair<TextPoint, TextPoint> {
        // On the 8th character's box (the line is narrower than its node, and right to left)
        val box = runOnIdle { assertNotNull(selection.layoutOf(0)).getBoundingBox(8) }
        onNodeWithTag("l0").performMouseInput { click(box.center) }
        waitForIdle()
        val pressed = assertNotNull(selection.pressedAt)
        assertEquals(0, pressed.lineIndex)
        assertTrue(pressed.offset in 3..lines[0].length - 4, "a click inside the line: $pressed")
        runOnIdle { assertTrue(selection.press(TextPoint(0, pressed.offset + 1), shift = true)) }
        return assertNotNull(selection.anchor) to assertNotNull(selection.focus)
    }

    @Test
    fun `shift arrows adjust a selection's focus, left toward the end of hebrew text`() =
        runComposeUiTest {
            val selection = BookSelection()
            val plainKeys = mutableStateListOf<Key>()
            setBook(selection, plainKeys)

            val (anchor, start) = selectInFirstLine(selection)
            assertEquals(0, anchor.lineIndex)
            assertEquals(0, start.lineIndex)
            assertEquals(TextPoint(0, anchor.offset + 1), start)

            // Right to left: Shift+Left selects the next characters
            onNodeWithTag("l0").performKeyInput {
                withKeyDown(Key.ShiftLeft) {
                    pressKey(Key.DirectionLeft)
                    pressKey(Key.DirectionLeft)
                }
            }
            waitForIdle()
            assertEquals(anchor, selection.anchor)
            assertEquals(anchor..TextPoint(0, start.offset + 2), selection.range)

            // Shift+Right goes back, then past the anchor, which stays
            onNodeWithTag("l0").performKeyInput {
                withKeyDown(Key.ShiftLeft) { repeat(5) { pressKey(Key.DirectionRight) } }
            }
            waitForIdle()
            assertEquals(anchor, selection.anchor)
            assertEquals(TextPoint(0, anchor.offset - 2)..anchor, selection.range)

            // Shift+Down into the next line, the anchor staying
            onNodeWithTag("l0").performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionDown) } }
            waitForIdle()
            assertEquals(anchor, selection.anchor)
            assertEquals(1, selection.focus?.lineIndex)

            // Ctrl+Shift+Left: to the end of a word
            val before = assertNotNull(selection.focus)
            onNodeWithTag("l0").performKeyInput {
                withKeyDown(Key.CtrlLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionLeft) } }
            }
            waitForIdle()
            val after = assertNotNull(selection.focus)
            assertTrue(after > before, "$after after $before")
            val text = lines[after.lineIndex]
            assertTrue(after.offset == text.length || text[after.offset] == ' ', "a word's end: $after")
            assertTrue(plainKeys.none { it == Key.DirectionLeft || it == Key.DirectionRight || it == Key.DirectionDown })
        }

    @Test
    fun `without a selection shift arrows and plain arrows are left to the view`() =
        runComposeUiTest {
            val selection = BookSelection()
            val plainKeys = mutableStateListOf<Key>()
            setBook(selection, plainKeys)

            onNodeWithTag("l0").performKeyInput {
                withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionDown) }
                pressKey(Key.DirectionDown)
            }
            waitForIdle()
            assertNull(selection.range)
            assertEquals(2, plainKeys.count { it == Key.DirectionDown })

            // A click is no selection: plain and Shift arrows still are the view's (its line moves)
            onNodeWithTag("l1").performMouseInput { click(center) }
            waitForIdle()
            onNodeWithTag("l1").performKeyInput {
                pressKey(Key.DirectionUp)
                withKeyDown(Key.ShiftLeft) {
                    pressKey(Key.DirectionUp)
                    pressKey(Key.DirectionLeft)
                }
            }
            waitForIdle()
            assertNull(selection.range)
            assertEquals(2, plainKeys.count { it == Key.DirectionUp })
            assertEquals(1, plainKeys.count { it == Key.DirectionLeft })
        }

    @Test
    fun `a plain click drops the keys' selection`() =
        runComposeUiTest {
            val selection = BookSelection()
            setBook(selection, mutableListOf())

            selectInFirstLine(selection)
            onNodeWithTag("l0").performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionLeft) } }
            waitForIdle()
            assertNotNull(selection.range)
            onNodeWithTag("l2").performMouseInput { click(center) }
            waitForIdle()
            assertNull(selection.range)
        }
}
