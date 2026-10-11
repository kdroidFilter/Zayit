package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforim.htmlparser.HtmlImageContentBuilder
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforimapp.core.annotations.LineHighlightRange
import io.github.kdroidfilter.seforimapp.core.presentation.text.DiacriticsMode
import io.github.kdroidfilter.seforimapp.core.presentation.text.stripNikudTeamimWithMap
import io.github.kdroidfilter.seforimapp.core.presentation.utils.rememberCopyToClipboard
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data

/** A character of the book: [offset] in the displayed text of the line at [lineIndex]. */
data class TextPoint(
    val lineIndex: Int,
    val offset: Int,
) : Comparable<TextPoint> {
    override fun compareTo(other: TextPoint): Int = compareValuesBy(this, other, { it.lineIndex }, { it.offset })
}

/**
 * Where the book view's text selection is, exactly, by line index and offset in the book.
 *
 * Compose's selection gives its text only, and only over the lines it has composed (a screen of a lazy list). So the
 * book view records each primary press on a line ([press]): the start of any Compose selection (a drag, a double
 * click), which [locateSelection] places in the book from its text, and the anchor a Shift+press extends from to
 * this view's own selection ([range], Zayit #48), drawn by every line ([selectedRangeOf]) and read from the
 * database ([resolveRange]), composed or not; Compose's located by [resolveComposeSelection]. Highlights and sources take the located range, never a text search.
 */
@Stable
class BookSelection {
    /** The character of the last plain primary press on a line. */
    var pressedAt: TextPoint? = null
        private set

    /** The Shift+click selection, from its first character to its end in reading order; null without one. */
    var range: ClosedRange<TextPoint>? by mutableStateOf(null)
        private set

    /** The current selection (Compose's or [range]) located in the book, once read from the database. */
    var resolved: ResolvedSelection? by mutableStateOf(null)

    /** A primary press at [point]; true when it extends the selection, the press then being the book view's. */
    fun press(
        point: TextPoint,
        shift: Boolean,
    ): Boolean {
        val from = pressedAt
        if (shift && from != null) {
            range = if (point < from) point..from else from..point
            resolved = null
            return true
        }
        pressedAt = point
        clear()
        return false
    }

    fun clear() {
        range = null
        resolved = null
    }

    /** The offsets of [lineIndex]'s displayed text ([length] chars) inside the Shift+click selection. */
    fun selectedRangeOf(
        lineIndex: Int,
        length: Int,
    ): IntRange = range?.offsetsIn(lineIndex, length) ?: IntRange.EMPTY
}

/** The offsets of [lineIndex]'s displayed text ([length] chars) inside this range, empty when outside. */
internal fun ClosedRange<TextPoint>.offsetsIn(
    lineIndex: Int,
    length: Int,
): IntRange {
    if (lineIndex !in start.lineIndex..endInclusive.lineIndex) return IntRange.EMPTY
    val from = if (lineIndex == start.lineIndex) start.offset.coerceIn(0, length) else 0
    val to = if (lineIndex == endInclusive.lineIndex) endInclusive.offset.coerceIn(0, length) else length
    return from until to
}

/**
 * A selection located in the book: its [text], its [range] and the [lines] it spans, in reading order. [owned] when it
 * is the book view's Shift+click selection, which the copies then take instead of Compose's.
 */
data class ResolvedSelection(
    val text: String,
    val range: ClosedRange<TextPoint>,
    val lines: List<Line>,
    val owned: Boolean,
) {
    val first: Line get() = lines.first()
    val last: Line get() = lines.last()
}

/** The [BookSelection] of the book view around the current SelectionContainer; null elsewhere (commentaries…). */
val LocalBookSelection = staticCompositionLocalOf<BookSelection?> { null }

/** A [SelectionContainer] whose lines record their presses in [selection], extend it on Shift+click, and copy it. */
@Composable
fun BookSelectionContainer(
    selection: BookSelection?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val copyToClipboard = rememberCopyToClipboard()
    CompositionLocalProvider(LocalBookSelection provides selection) {
        // On the container itself: its own Ctrl/Cmd+C would copy its (empty) selection
        SelectionContainer(
            modifier =
                modifier
                    .then(
                        if (selection != null) {
                            // A plain press nobody took (blank space, a margin) deselects, as it does Compose's selection
                            Modifier.pointerInput(selection) {
                                awaitEachGesture {
                                    val down = awaitPointerEvent(PointerEventPass.Final)
                                    val shift = down.keyboardModifiers.isShiftPressed
                                    if (down.buttons.isPrimaryPressed && !shift && down.changes.none { it.isConsumed }) selection.clear()
                                }
                            }
                        } else {
                            Modifier
                        },
                    ).onPreviewKeyEvent { event ->
                        val owned = selection?.resolved?.takeIf { it.owned }
                        if (owned != null && event.isPlainCopy()) {
                            copyToClipboard(owned.text)
                            true
                        } else {
                            false
                        }
                    },
            content = content,
        )
    }
}

/**
 * A line's primary presses for [selection], [layout] giving the character under the pointer (null while the line is a
 * placeholder). A Shift+press extending
 * the selection is consumed before Compose's own selection gesture, which would extend its selection only.
 */
fun Modifier.selectableLine(
    selection: BookSelection,
    lineIndex: Int,
    layout: () -> TextLayoutResult?,
): Modifier =
    pointerInput(selection, lineIndex) {
        awaitEachGesture {
            val down = awaitPointerEvent(PointerEventPass.Initial)
            val change = down.changes.firstOrNull() ?: return@awaitEachGesture
            if (!down.buttons.isPrimaryPressed || change.type != PointerType.Mouse) return@awaitEachGesture
            // A line still drawn as a placeholder (text parsing) anchors at its start
            val offset = layout()?.getOffsetForPosition(change.position) ?: 0
            if (!selection.press(TextPoint(lineIndex, offset), down.keyboardModifiers.isShiftPressed)) return@awaitEachGesture
            // The whole gesture is the selection's
            down.changes.forEach { it.consume() }
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }

/** The Shift+click [range] read from the database, each line cut at the range's offsets as it is displayed. */
suspend fun resolveRange(
    range: ClosedRange<TextPoint>,
    bookId: Long,
    repository: SeforimRepository,
    diacritics: DiacriticsMode,
): ResolvedSelection? {
    val lines = repository.getLines(bookId, range.start.lineIndex, range.endInclusive.lineIndex)
    if (lines.isEmpty()) return null
    val text =
        withContext(Dispatchers.Default) {
            lines
                .map { line ->
                    val displayed = displayedLineText(line.content, diacritics)
                    val selected = range.offsetsIn(line.lineIndex, displayed.length)
                    if (selected.isEmpty()) "" else displayed.substring(selected.first, selected.last + 1)
                }.filter { it.isNotEmpty() }
                .joinToString("\n")
        }
    return ResolvedSelection(text, range, lines, owned = true)
}

/** Compose's [selected] text located in the book from the press it started at ([BookSelection.pressedAt]). */
suspend fun resolveComposeSelection(
    selected: String,
    pressedAt: TextPoint,
    bookId: Long,
    repository: SeforimRepository,
    diacritics: DiacriticsMode,
): ResolvedSelection? {
    val span = selected.count { it == '\n' }
    val lines = repository.getLines(bookId, (pressedAt.lineIndex - span).coerceAtLeast(0), pressedAt.lineIndex + span)
    val displayed = withContext(Dispatchers.Default) { lines.associate { it.lineIndex to displayedLineText(it.content, diacritics) } }
    val range = locateSelection(selected, pressedAt, displayed) ?: return null
    return ResolvedSelection(
        text = selected,
        range = range,
        lines = lines.filter { it.lineIndex in range.start.lineIndex..range.endInclusive.lineIndex },
        owned = false,
    )
}

/**
 * Where [selected] (Compose's text: one piece per line, joined by newlines) sits in the book, given the character
 * [pressedAt] it started from and the displayed text of the lines around it. Selected from there downward, its first
 * piece ends the pressed line and the next ones follow; upward, its last piece starts it and the previous ones come
 * before; on the pressed line alone, it is the occurrence there that holds the press (a double click selects around
 * it). Null when the text is not found that way (a selection started elsewhere).
 */
internal fun locateSelection(
    selected: String,
    pressedAt: TextPoint,
    displayed: Map<Int, String>,
): ClosedRange<TextPoint>? {
    val pieces = selected.split('\n')
    val at = pressedAt.lineIndex
    val line = displayed[at] ?: return null
    if (pieces.size == 1) {
        val piece = pieces.single()
        if (piece.isEmpty()) return null
        var start = line.indexOf(piece)
        while (start >= 0) {
            if (pressedAt.offset in start..start + piece.length) return TextPoint(at, start)..TextPoint(at, start + piece.length)
            start = line.indexOf(piece, start + 1)
        }
        return null
    }
    val last = pieces.lastIndex

    fun follows(
        from: Int,
        step: Int,
    ): Boolean =
        (1 until last).all { i ->
            val index = if (step > 0) from + i else from - last + i
            displayed[index] == pieces[i]
        }

    // Downward: the pressed line ends with the first piece, from the press or before it (word by word)
    val downStart = line.length - pieces.first().length
    if (line.endsWith(pieces.first()) && downStart <= pressedAt.offset && follows(at, 1)) {
        val end = displayed[at + last] ?: return null
        if (end.startsWith(pieces.last())) return TextPoint(at, downStart)..TextPoint(at + last, pieces.last().length)
    }
    // Upward: the pressed line starts with the last piece, up to the press or past it
    if (line.startsWith(pieces.last()) && pieces.last().length >= pressedAt.offset && follows(at, -1)) {
        val top = displayed[at - last] ?: return null
        if (top.endsWith(pieces.first())) {
            return TextPoint(at - last, top.length - pieces.first().length)..TextPoint(at, pieces.last().length)
        }
    }
    return null
}

/**
 * The highlight ranges of [range] over its [lines], in the coordinates highlights have always been stored in: each
 * line's plain text with diacritics (`buildAnnotatedFromHtml(content)`), the displayed offsets mapped back through
 * the diacritics the reading mode hides. Null when a line is displayed otherwise than that text (inline images, or
 * diacritics stripped differently), for the caller to fall back on the text resolver.
 */
fun highlightRangesOf(
    range: ClosedRange<TextPoint>,
    lines: List<Line>,
    diacritics: DiacriticsMode,
): List<LineHighlightRange>? =
    lines.mapNotNull { line ->
        val original = buildAnnotatedFromHtml(line.content, baseTextSize = 16f, boldScale = 1f).text
        if (displayedLineText(line.content, DiacriticsMode.All) != original) return null
        val displayed = displayedLineText(line.content, diacritics)
        val selected = range.offsetsIn(line.lineIndex, displayed.length)
        if (selected.isEmpty()) return@mapNotNull null
        if (diacritics == DiacriticsMode.All) return@mapNotNull LineHighlightRange(line.id, selected)
        val (stripped, toOriginal) = stripNikudTeamimWithMap(original, diacritics)
        // The map only holds when stripping the plain text gives the displayed text
        if (stripped != displayed) return null
        val from = toOriginal.getOrElse(selected.first) { original.length }
        val to = toOriginal.getOrElse(selected.last + 1) { original.length }
        if (to > from) LineHighlightRange(line.id, from until to) else null
    }

/** The text of a line as the book view displays it, which a selection's offsets index. */
internal fun displayedLineText(
    content: String,
    diacritics: DiacriticsMode,
): String =
    buildLineAnnotation(
        html = diacritics.apply(content),
        baseTextSize = 16f,
        boldScale = 1f,
        footnoteMarkerColor = Color.Unspecified,
        imageContentBuilder = ImagePlaceholders,
    ).annotated.text

// The text only: an image stands as its placeholder character, as displayed, without decoding it
private val ImagePlaceholder = InlineTextContent(Placeholder(1.sp, 1.sp, PlaceholderVerticalAlign.TextCenter)) {}
private val ImagePlaceholders: HtmlImageContentBuilder = { element, _ ->
    // Displayed only when its format decodes: the header says so, without decoding the pixels
    ImagePlaceholder.takeIf {
        element.imageBytes?.let { runCatching { Codec.makeFromData(Data.makeFromBytes(it)).close() }.isSuccess } ==
            true
    }
}

// Ctrl/Cmd+C alone: Shift and Alt make it the copies without nikud and with source, handled at the AWT level
private fun KeyEvent.isPlainCopy(): Boolean =
    type == KeyEventType.KeyDown && key == Key.C && (isCtrlPressed || isMetaPressed) && !isShiftPressed && !isAltPressed
