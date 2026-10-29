package io.github.kdroidfilter.seforimapp.features.bookcontent.segments

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import io.github.kdroidfilter.seforim.htmlparser.COMMENT_SEGMENT_TAG
import io.github.kdroidfilter.seforim.htmlparser.CommentAnchor
import io.github.kdroidfilter.seforim.htmlparser.commentAnchorsOf

/** A passage of a line that commentaries explain, [start] to [end] (exclusive) in the displayed text. */
@Immutable
data class CommentSegment(
    val start: Int,
    val end: Int,
    val anchors: Set<CommentAnchor>,
)

/** The passages of this line, from its commentary anchors, in text order. */
fun AnnotatedString.commentSegments(): List<CommentSegment> =
    getStringAnnotations(COMMENT_SEGMENT_TAG, 0, length).mapNotNull { annotation ->
        val anchors = commentAnchorsOf(annotation.item).toSet()
        // The passage starts on its first word, not on the space an anchor may leave before it
        val start = (annotation.start until annotation.end).firstOrNull { !text[it].isWhitespace() }
        if (anchors.isEmpty() || start == null) null else CommentSegment(start, annotation.end, anchors)
    }

fun List<CommentSegment>.at(offset: Int): CommentSegment? = firstOrNull { offset >= it.start && offset < it.end }

/** The commentary lines [segment]'s anchors lead to, among [targets] (its line's), in the books [openBooks]. */
fun passageOf(
    lineId: Long,
    segment: CommentSegment,
    targets: Map<CommentAnchor, AnchorTarget>,
    openBooks: Set<Long>,
): SelectedPassage? {
    val reached = segment.anchors.mapNotNull { targets[it] }.filter { it.bookId in openBooks }
    if (reached.isEmpty()) return null
    return SelectedPassage(
        lineId = lineId,
        anchors = segment.anchors,
        targetLineIds = reached.flatMapTo(mutableSetOf()) { it.lineIds },
        targetBookIds = reached.mapTo(mutableSetOf()) { it.bookId },
    )
}

/**
 * A passage selected in the text: its [lineId], its [anchors] (which tell it among the line's passages) and the
 * commentary lines they lead to. Part of the tab's line selection
 * ([ContentState.selectedPassage][io.github.kdroidfilter.seforimapp.features.bookcontent.state.ContentState]), never
 * held apart from it.
 */
@Immutable
data class SelectedPassage(
    val lineId: Long,
    val anchors: Set<CommentAnchor>,
    val targetLineIds: Set<Long>,
    val targetBookIds: Set<Long>,
)
