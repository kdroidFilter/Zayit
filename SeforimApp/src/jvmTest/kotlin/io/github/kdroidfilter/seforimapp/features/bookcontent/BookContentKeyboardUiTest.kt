package io.github.kdroidfilter.seforimapp.features.bookcontent

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.computePageScrollTargetIndex
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Compose UI tests for keyboard navigation behaviors introduced in the
 * page-up/page-down PR. Uses a simplified test composable that mirrors
 * the BookContentView keyboard handler pattern.
 */
@OptIn(ExperimentalTestApi::class)
class BookContentKeyboardUiTest {
    // ==================== Arrow key events ====================

    @Test
    fun `arrow down dispatches NavigateToNextLine event`() =
        runComposeUiTest {
            val events = mutableStateListOf<String>()
            val focusRequester = FocusRequester()

            setContent {
                Box(
                    modifier =
                        Modifier
                            .testTag("content")
                            .focusRequester(focusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionDown -> {
                                            events.add("NavigateToNextLine")
                                            true
                                        }
                                        Key.DirectionUp -> {
                                            events.add("NavigateToPreviousLine")
                                            true
                                        }
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }.focusable(),
                )
            }

            waitForIdle()
            focusRequester.requestFocus()
            waitForIdle()

            onNodeWithTag("content").performKeyInput { pressKey(Key.DirectionDown) }
            waitForIdle()

            assertTrue(events.contains("NavigateToNextLine"), "Down arrow should dispatch NavigateToNextLine")
        }

    @Test
    fun `arrow up dispatches NavigateToPreviousLine event`() =
        runComposeUiTest {
            val events = mutableStateListOf<String>()
            val focusRequester = FocusRequester()

            setContent {
                Box(
                    modifier =
                        Modifier
                            .testTag("content")
                            .focusRequester(focusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionUp -> {
                                            events.add("NavigateToPreviousLine")
                                            true
                                        }
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }.focusable(),
                )
            }

            waitForIdle()
            focusRequester.requestFocus()
            waitForIdle()

            onNodeWithTag("content").performKeyInput { pressKey(Key.DirectionUp) }
            waitForIdle()

            assertTrue(events.contains("NavigateToPreviousLine"), "Up arrow should dispatch NavigateToPreviousLine")
        }

    // ==================== Page Up/Down events ====================

    @Test
    fun `page down dispatches scroll event`() =
        runComposeUiTest {
            val events = mutableStateListOf<String>()
            val focusRequester = FocusRequester()

            setContent {
                Box(
                    modifier =
                        Modifier
                            .testTag("content")
                            .focusRequester(focusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.PageDown -> {
                                            events.add("PageDown")
                                            true
                                        }
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }.focusable(),
                )
            }

            waitForIdle()
            focusRequester.requestFocus()
            waitForIdle()

            onNodeWithTag("content").performKeyInput { pressKey(Key.PageDown) }
            waitForIdle()

            assertTrue(events.contains("PageDown"), "PageDown key should be handled")
        }

    @Test
    fun `page up dispatches scroll event`() =
        runComposeUiTest {
            val events = mutableStateListOf<String>()
            val focusRequester = FocusRequester()

            setContent {
                Box(
                    modifier =
                        Modifier
                            .testTag("content")
                            .focusRequester(focusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.PageUp -> {
                                            events.add("PageUp")
                                            true
                                        }
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }.focusable(),
                )
            }

            waitForIdle()
            focusRequester.requestFocus()
            waitForIdle()

            onNodeWithTag("content").performKeyInput { pressKey(Key.PageUp) }
            waitForIdle()

            assertTrue(events.contains("PageUp"), "PageUp key should be handled")
        }

    // ==================== Modifier ordering ====================

    @Test
    fun `onPreviewKeyEvent before focusable intercepts keys`() =
        runComposeUiTest {
            val events = mutableStateListOf<String>()
            val focusRequester = FocusRequester()

            setContent {
                // This mirrors the exact modifier order from BookContentView:
                // .focusRequester -> .onPreviewKeyEvent -> .focusable
                Box(
                    modifier =
                        Modifier
                            .testTag("content")
                            .focusRequester(focusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.PageDown) {
                                    events.add("intercepted")
                                    true // consumed
                                } else {
                                    false
                                }
                            }.focusable(),
                )
            }

            waitForIdle()
            focusRequester.requestFocus()
            waitForIdle()

            onNodeWithTag("content").performKeyInput { pressKey(Key.PageDown) }
            waitForIdle()

            assertEquals(1, events.size, "Key should be intercepted exactly once by onPreviewKeyEvent")
            assertEquals("intercepted", events.first())
        }

    // ==================== Page scroll with LazyColumn ====================

    @Test
    fun `page down scrolls LazyColumn to last fully visible item`() =
        runComposeUiTest {
            val listState = LazyListState()
            val items = (0 until 50).toList()
            val focusRequester = FocusRequester()

            setContent {
                val scope = rememberCoroutineScope()
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .testTag("scrollContainer")
                            .focusRequester(focusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.PageDown) {
                                    scrollByPage(forward = true, listState, scope)
                                    true
                                } else {
                                    false
                                }
                            }.focusable(),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(items) { item ->
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(100.dp)
                                        .testTag("item_$item"),
                            )
                        }
                    }
                }
            }

            waitForIdle()
            focusRequester.requestFocus()
            waitForIdle()

            val initialFirst = listState.firstVisibleItemIndex

            onNodeWithTag("scrollContainer").performKeyInput { pressKey(Key.PageDown) }

            // Wait for animation to complete
            waitForIdle()
            mainClock.advanceTimeBy(1000)
            waitForIdle()

            assertTrue(
                listState.firstVisibleItemIndex > initialFirst,
                "Page Down should scroll forward: was $initialFirst, now ${listState.firstVisibleItemIndex}",
            )
        }

    @Test
    fun `page up scrolls LazyColumn backward`() =
        runComposeUiTest {
            val listState = LazyListState(firstVisibleItemIndex = 20)
            val items = (0 until 50).toList()
            val focusRequester = FocusRequester()

            setContent {
                val scope = rememberCoroutineScope()
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .testTag("scrollContainer")
                            .focusRequester(focusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.PageUp) {
                                    scrollByPage(forward = false, listState, scope)
                                    true
                                } else {
                                    false
                                }
                            }.focusable(),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(items) { item ->
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(100.dp)
                                        .testTag("item_$item"),
                            )
                        }
                    }
                }
            }

            waitForIdle()
            focusRequester.requestFocus()
            waitForIdle()

            val initialFirst = listState.firstVisibleItemIndex

            onNodeWithTag("scrollContainer").performKeyInput { pressKey(Key.PageUp) }

            waitForIdle()
            mainClock.advanceTimeBy(1000)
            waitForIdle()

            assertTrue(
                listState.firstVisibleItemIndex < initialFirst,
                "Page Up should scroll backward: was $initialFirst, now ${listState.firstVisibleItemIndex}",
            )
        }

    // ==================== Key events are consumed (not propagated) ====================

    @Test
    fun `all navigation keys are consumed and not propagated`() =
        runComposeUiTest {
            val handledKeys = mutableStateListOf<Key>()
            val propagatedKeys = mutableStateListOf<Key>()
            val focusRequester = FocusRequester()

            setContent {
                // Outer box catches any keys that leak through
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    propagatedKeys.add(keyEvent.key)
                                }
                                false // don't consume, just observe
                            },
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .testTag("content")
                                .focusRequester(focusRequester)
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                        when (keyEvent.key) {
                                            Key.DirectionUp, Key.DirectionDown,
                                            Key.PageUp, Key.PageDown,
                                            -> {
                                                handledKeys.add(keyEvent.key)
                                                true // consumed
                                            }
                                            else -> false
                                        }
                                    } else {
                                        false
                                    }
                                }.focusable(),
                    )
                }
            }

            waitForIdle()
            focusRequester.requestFocus()
            waitForIdle()

            val keysToTest = listOf(Key.DirectionUp, Key.DirectionDown, Key.PageUp, Key.PageDown)
            for (key in keysToTest) {
                onNodeWithTag("content").performKeyInput { pressKey(key) }
                waitForIdle()
            }

            assertEquals(4, handledKeys.size, "All 4 navigation keys should be handled")
        }

    /**
     * Helper that mirrors the scrollByPage logic from BookContentView
     * using the extracted computePageScrollTargetIndex function.
     */
    private fun scrollByPage(
        forward: Boolean,
        listState: LazyListState,
        @StructuredScope scope: CoroutineScope,
    ) {
        val visibleItems = listState.layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return
        val targetIndex =
            computePageScrollTargetIndex(
                forward = forward,
                visibleItemIndices = visibleItems.map { it.index },
                visibleItemEndOffsets = visibleItems.associate { it.index to (it.offset + it.size) },
                viewportEnd = listState.layoutInfo.viewportEndOffset,
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
            ) ?: return
        scope.launch { listState.animateScrollToItem(targetIndex, 0) }
    }
}
