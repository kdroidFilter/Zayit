package io.github.kdroidfilter.seforimapp.features.bookcontent.segments

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import com.kdroid.gematria.converter.toGematria
import io.github.kdroidfilter.seforim.htmlparser.COMMENT_SEGMENT_TAG
import io.github.kdroidfilter.seforim.htmlparser.commentAnchorsOf

/** A commentary as its anchors name it: its book (by title) and its number in the section. */
data class CommentKey(
    val bookTitle: String,
    val order: Int,
)

/** A passage of a line that commentaries explain, [start] to [end] (exclusive) in the displayed text. */
@Immutable
data class CommentSegment(
    val start: Int,
    val end: Int,
    val keys: Set<CommentKey>,
)

// The anchors name the commentaries by their Sefaria title. Only those whose numbering was checked against the
// anchors (the Tur's): another book's anchors stay inert until its own numbering is checked.
private val COMMENTATOR_TITLES =
    mapOf(
        "Beit Yosef" to "בית יוסף",
        "Beit_Yosef" to "בית יוסף",
        "Bach" to "ב\"ח",
        "Prisha" to "פרישה",
        "Drisha" to "דרישה",
        "Darkhei Moshe" to "דרכי משה",
        "Darchei Moshe" to "דרכי משה",
    )

/** The commentators (as the anchors name them) whose anchors the reader asks the parser for. */
val ANCHORED_COMMENTATORS: Set<String> = COMMENTATOR_TITLES.keys

/** The passages of this line that commentaries we know explain, in text order. */
fun AnnotatedString.commentSegments(): List<CommentSegment> =
    getStringAnnotations(COMMENT_SEGMENT_TAG, 0, length).mapNotNull { annotation ->
        val keys =
            commentAnchorsOf(annotation.item)
                .mapNotNullTo(mutableSetOf()) { anchor ->
                    COMMENTATOR_TITLES[anchor.commentator]?.let { CommentKey(it, anchor.order) }
                }
        // The passage starts on its first word, not on the space an anchor may leave before it
        val start = (annotation.start until annotation.end).firstOrNull { !text[it].isWhitespace() }
        if (keys.isEmpty() || start == null) null else CommentSegment(start, annotation.end, keys)
    }

fun List<CommentSegment>.at(offset: Int): CommentSegment? = firstOrNull { offset >= it.start && offset < it.end }

/**
 * The key of the comment at [commentHeRef] (`בית יוסף, אורח חיים, ז, ב, א`) on the line at [baseHeRef]
 * (`טור, אורח חיים, ז, א`): the comment shares the line's section and its number follows it (`ב`).
 */
fun commentKeyOf(
    baseHeRef: String?,
    commentHeRef: String?,
    commentBookTitle: String,
): CommentKey? {
    val section = baseHeRef?.refParts()?.dropLast(1) ?: return null
    // Numbered by the Shulchan Aruch's סעיף there, not by their order in the siman: no key rather than a wrong one
    if (commentBookTitle in BY_SEIF[section.firstOrNull()].orEmpty()) return null
    val comment = commentHeRef?.refParts() ?: return null
    if (comment.size <= section.size || comment.subList(0, section.size) != section) return null
    val order = comment[section.size].toGematria().takeIf { it > 0 } ?: return null
    return CommentKey(commentBookTitle, order)
}

// The Tur's parts whose commentaries are referenced by סעיף, not by their order in the siman (checked against the DB)
private val BY_SEIF = mapOf("חושן משפט" to setOf("בית יוסף", "ב\"ח"))

// The parts of a reference after the book's title
private fun String.refParts(): List<String> = split(',').drop(1).map { it.trim() }

/**
 * A passage selected in the text: its [lineId] and the commentaries it opens. Part of the tab's line selection
 * ([ContentState.selectedPassage][io.github.kdroidfilter.seforimapp.features.bookcontent.state.ContentState]), never
 * held apart from it.
 */
@Immutable
data class SelectedPassage(
    val lineId: Long,
    val keys: Set<CommentKey>,
)
