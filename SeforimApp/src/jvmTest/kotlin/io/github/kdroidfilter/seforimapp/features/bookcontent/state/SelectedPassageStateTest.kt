package io.github.kdroidfilter.seforimapp.features.bookcontent.state

import io.github.kdroidfilter.seforim.htmlparser.CommentAnchor
import io.github.kdroidfilter.seforimapp.features.bookcontent.segments.SelectedPassage
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SelectedPassageStateTest {
    private val line7 = Line(id = 7, bookId = 380, lineIndex = 25, content = "")
    private val line8 = Line(id = 8, bookId = 380, lineIndex = 27, content = "")
    private val passage = SelectedPassage(7, setOf(CommentAnchor("Prisha", 2)), setOf(113L), setOf(5063L))

    private fun state(
        line: Line,
        passage: SelectedPassage? = null,
    ) = BookContentState(
        content = ContentState(selectedLines = setOf(line), primarySelectedLineId = line.id, selectedPassage = passage),
    )

    @Test
    fun aPassageSetWithItsLineStays() {
        val before = state(line8)
        val after = state(line7, passage)
        assertEquals(passage, after.withPassageOfItsSelection(before).content.selectedPassage)
    }

    @Test
    fun anotherLineSelectedByAnyPathLetsThePassageGo() {
        val before = state(line7, passage)
        // E.g. the arrows, the TOC or a search moving the selection, without a word about the passage
        val after = before.copy(content = before.content.copy(selectedLines = setOf(line8), primarySelectedLineId = 8))
        assertNull(after.withPassageOfItsSelection(before).content.selectedPassage)
    }

    @Test
    fun anUnrelatedChangeKeepsThePassage() {
        val before = state(line7, passage)
        val after = before.copy(content = before.content.copy(scrollIndex = 40))
        assertEquals(passage, after.withPassageOfItsSelection(before).content.selectedPassage)
    }

    @Test
    fun aPassageOfAnotherLineThanThePrimaryNeverStands() {
        val before = state(line8)
        val after = state(line8, passage)
        assertNull(after.withPassageOfItsSelection(before).content.selectedPassage)
    }

    @Test
    fun aPassageClickReselectsItsLineOnlyWhenItIsNotTheSelection() {
        // The line alone, or its TOC section, already selected: no need to select it again
        assertFalse(state(line7).content.needsLineSelectionFor(7))
        assertFalse(
            ContentState(
                selectedLines = setOf(line7, line8),
                primarySelectedLineId = 7,
                isTocEntrySelection = true,
            ).needsLineSelectionFor(7),
        )
        // Another line, or a manual multi-selection the click collapses
        assertTrue(state(line8).content.needsLineSelectionFor(7))
        assertTrue(ContentState(selectedLines = setOf(line7, line8), primarySelectedLineId = 7).needsLineSelectionFor(7))
    }
}
