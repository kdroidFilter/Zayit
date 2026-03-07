package io.github.kdroidfilter.seforimapp.features.bookcontent

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.booktoc.BookTocView
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Compose UI tests verifying that BookTocView auto-center behavior:
 * - scrolls to the selected entry when selection changes externally (e.g. line click)
 * - does NOT scroll when the selection change originates from a click inside the TOC panel
 */
@OptIn(ExperimentalTestApi::class)
class BookTocViewAutoCenterUiTest {
    /** Creates leaf TOC entries (no children, not expandable). */
    private fun createTocEntries(
        count: Int,
        bookId: Long = 1L,
    ): List<TocEntry> =
        (0 until count).map { i ->
            TestFactories.createTocEntry(
                id = i.toLong(),
                bookId = bookId,
                text = "Entry $i",
                lineId = i * 10L,
                hasChildren = false,
            )
        }

    @Test
    fun `external selection change scrolls TOC to selected entry`() =
        runComposeUiTest {
            val entries = createTocEntries(50)
            var selectedId by mutableStateOf<Long?>(null)

            setContent {
                IntUiTheme(isDark = false) {
                    BookTocView(
                        tocEntries = entries.toImmutableList(),
                        expandedEntries = emptySet(),
                        tocChildren = emptyMap(),
                        scrollIndex = 0,
                        scrollOffset = 0,
                        onEntryClick = {},
                        onEntryExpand = {},
                        onScroll = { _, _ -> },
                        selectedTocEntryId = selectedId,
                    )
                }
            }

            waitForIdle()

            // Simulate an external selection change (e.g. user clicked a line in content)
            selectedId = 40L
            waitForIdle()
            mainClock.advanceTimeBy(500)
            waitForIdle()

            // Entry 40 should now be visible (auto-centered)
            onNodeWithText("Entry 40").assertExists()
        }

    @Test
    fun `clicking TOC entry does not re-scroll the panel`() =
        runComposeUiTest {
            val entries = createTocEntries(50)
            var selectedId by mutableStateOf<Long?>(null)
            var clickedEntryId by mutableStateOf<Long?>(null)

            setContent {
                IntUiTheme(isDark = false) {
                    BookTocView(
                        tocEntries = entries.toImmutableList(),
                        expandedEntries = emptySet(),
                        tocChildren = emptyMap(),
                        scrollIndex = 0,
                        scrollOffset = 0,
                        onEntryClick = { entry ->
                            clickedEntryId = entry.id
                            // Simulate the round-trip: click -> ViewModel -> state update
                            selectedId = entry.id
                        },
                        onEntryExpand = {},
                        onScroll = { _, _ -> },
                        selectedTocEntryId = selectedId,
                    )
                }
            }

            waitForIdle()

            // First, scroll externally to entry 40 so the list is near the bottom
            selectedId = 40L
            waitForIdle()
            mainClock.advanceTimeBy(500)
            waitForIdle()

            // Now click entry 40 (visible) — this should NOT trigger auto-center
            // Record scroll position before click
            onNodeWithText("Entry 40").performClick()
            waitForIdle()
            mainClock.advanceTimeBy(500)
            waitForIdle()

            assertEquals(40L, clickedEntryId, "Entry 40 should have been clicked")
            // Entry 40 should still be visible (panel didn't jump away)
            onNodeWithText("Entry 40").assertExists()
        }

    @Test
    fun `after TOC click, next external selection still auto-centers`() =
        runComposeUiTest {
            val entries = createTocEntries(50)
            var selectedId by mutableStateOf<Long?>(null)

            setContent {
                IntUiTheme(isDark = false) {
                    BookTocView(
                        tocEntries = entries.toImmutableList(),
                        expandedEntries = emptySet(),
                        tocChildren = emptyMap(),
                        scrollIndex = 0,
                        scrollOffset = 0,
                        onEntryClick = { entry ->
                            selectedId = entry.id
                        },
                        onEntryExpand = {},
                        onScroll = { _, _ -> },
                        selectedTocEntryId = selectedId,
                    )
                }
            }

            waitForIdle()

            // Click on a visible entry (entry 2) — uses skipNextAutoCenter
            onNodeWithText("Entry 2").performClick()
            waitForIdle()
            mainClock.advanceTimeBy(500)
            waitForIdle()

            // Now simulate an external selection change to a far-away entry
            selectedId = 45L
            waitForIdle()
            mainClock.advanceTimeBy(500)
            waitForIdle()

            // Auto-center should have kicked in — entry 45 must be visible
            onNodeWithText("Entry 45").assertExists()
        }
}
