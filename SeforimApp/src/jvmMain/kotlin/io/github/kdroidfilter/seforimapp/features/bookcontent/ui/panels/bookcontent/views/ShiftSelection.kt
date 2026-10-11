package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.foundation.gestures.awaitEachGesture
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
import androidx.compose.ui.text.TextLayoutResult
import io.github.kdroidfilter.seforim.htmlparser.SkiaHtmlImageBuilder
import io.github.kdroidfilter.seforimapp.core.presentation.text.DiacriticsMode
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/** A character of the book: [offset] in the displayed text of the line at [lineIndex]. */
data class TextPoint(
    val lineIndex: Int,
    val offset: Int,
) : Comparable<TextPoint> {
    override fun compareTo(other: TextPoint): Int = compareValuesBy(this, other, { it.lineIndex }, { it.offset })
}

/**
 * The book view's Shift+click selection (Zayit #48). Compose only selects the lines it has composed, and a lazy list
 * composes a screen of them: a Shift+click several screens away would leave every line in between out. So the
 * book view owns this selection: a plain press sets the anchor, a Shift+press extends from it to a range of the book
 * by line index and offset, which every line draws ([selectedRangeOf]) and the copies take from the database
 * ([textOf]), composed or not.
 */
@Stable
class ShiftSelection {
    private var anchor: TextPoint? = null

    /** From the first selected character to the end of the selection, in reading order; null without one. */
    var range: ClosedRange<TextPoint>? by mutableStateOf(null)
        private set

    /** The selected text and its first and last lines, once read from the database ([textOf]). */
    var copied: CopiedSelection? by mutableStateOf(null)

    /** A primary press at [point]; true when it extends the selection, the press then being the book view's. */
    fun press(
        point: TextPoint,
        shift: Boolean,
    ): Boolean {
        val from = anchor
        if (shift && from != null) {
            range = if (point < from) point..from else from..point
            copied = null
            return true
        }
        anchor = point
        clear()
        return false
    }

    fun clear() {
        range = null
        copied = null
    }

    /** The offsets of [lineIndex]'s displayed text ([length] chars) inside the selection, empty when outside. */
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

/** A selection's [text] over its [lines], in reading order. */
data class CopiedSelection(
    val text: String,
    val lines: List<Line>,
) {
    val first: Line get() = lines.first()
    val last: Line get() = lines.last()
}

/** The [ShiftSelection] of the book view around the current SelectionContainer; null elsewhere (commentaries…). */
val LocalShiftSelection = staticCompositionLocalOf<ShiftSelection?> { null }

/** A [SelectionContainer] whose lines extend [selection] on Shift+click and whose Ctrl/Cmd+C copies it. */
@Composable
fun ShiftSelectionContainer(
    selection: ShiftSelection?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalShiftSelection provides selection) {
        // On the container itself: its own Ctrl/Cmd+C would copy its (empty) selection
        SelectionContainer(
            modifier =
                modifier.onPreviewKeyEvent { event ->
                    val copied = selection?.copied
                    if (copied != null && event.isPlainCopy()) {
                        copyToClipboard(copied.text)
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
 * A line's primary presses for [selection], [layout] giving the character under the pointer. A Shift+press extending
 * the selection is consumed before Compose's own selection gesture, which would extend its selection only.
 */
fun Modifier.shiftSelectable(
    selection: ShiftSelection,
    lineIndex: Int,
    layout: () -> TextLayoutResult?,
): Modifier =
    pointerInput(selection, lineIndex) {
        awaitEachGesture {
            val down = awaitPointerEvent(PointerEventPass.Initial)
            val change = down.changes.firstOrNull() ?: return@awaitEachGesture
            if (!down.buttons.isPrimaryPressed || change.type != PointerType.Mouse) return@awaitEachGesture
            val offset = layout()?.getOffsetForPosition(change.position) ?: return@awaitEachGesture
            if (!selection.press(TextPoint(lineIndex, offset), down.keyboardModifiers.isShiftPressed)) return@awaitEachGesture
            // The whole gesture is the selection's
            down.changes.forEach { it.consume() }
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }

/** [range]'s text read from the database, each line cut at the range's offsets as it is displayed. */
suspend fun textOf(
    range: ClosedRange<TextPoint>,
    bookId: Long,
    repository: SeforimRepository,
    diacritics: DiacriticsMode,
): CopiedSelection? {
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
    return CopiedSelection(text, lines)
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
        imageContentBuilder = SkiaHtmlImageBuilder.build(),
    ).annotated.text

fun copyToClipboard(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

// Ctrl/Cmd+C alone: Shift and Alt make it the copies without nikud and with source, handled at the AWT level
private fun KeyEvent.isPlainCopy(): Boolean =
    type == KeyEventType.KeyDown && key == Key.C && (isCtrlPressed || isMetaPressed) && !isShiftPressed && !isAltPressed
