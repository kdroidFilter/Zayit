package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextLayoutResult
import java.text.Bidi
import java.text.BreakIterator

/*
 * The moves of a selection's focus by Shift+arrow keys, as in a text editor: a character, a word, a visual line.
 * Points are characters of book lines ([TextPoint]); `textOf` gives a line's displayed text, null past the book's
 * ends or for a line not loaded, which the moves then stop before. Pure, the layouts behind [LineGeometry].
 */

/** The visual lines of a laid-out book line, as the vertical moves read them. */
internal interface LineGeometry {
    val length: Int
    val lineCount: Int

    fun lineOf(offset: Int): Int

    fun xOf(offset: Int): Float

    fun offsetAt(
        visualLine: Int,
        x: Float,
    ): Int
}

internal fun TextLayoutResult.geometry(): LineGeometry =
    object : LineGeometry {
        override val length get() = layoutInput.text.length
        override val lineCount get() = this@geometry.lineCount

        override fun lineOf(offset: Int) = getLineForOffset(offset.coerceIn(0, length))

        override fun xOf(offset: Int) = getHorizontalPosition(offset.coerceIn(0, length), usePrimaryDirection = true)

        override fun offsetAt(
            visualLine: Int,
            x: Float,
        ): Int {
            val y = (getLineTop(visualLine) + getLineBottom(visualLine)) / 2
            return getOffsetForPosition(Offset(x, y))
        }
    }

/**
 * Whether Left or Right ([left]) moves toward the end of the text. The arrows follow the screen, as in a text editor:
 * Hebrew runs right to left, so in a right-to-left line Left moves toward the end (the next letter, the one on its
 * left) and Right toward the start; in a left-to-right line the reverse. The direction is the focus's line's, from
 * its text (its first strong character), so a book mixing Hebrew and Latin lines feels natural in both. Moves are
 * logical within the line (by offset), as a text editor's caret is across a short embedded run of the other direction.
 */
internal fun towardsEnd(
    left: Boolean,
    rtl: Boolean,
): Boolean = left == rtl

/** Whether [text]'s paragraph runs right to left; a line without a strong character takes the book's direction. */
internal fun isRtlText(text: String): Boolean = text.isEmpty() || !Bidi(text, Bidi.DIRECTION_DEFAULT_RIGHT_TO_LEFT).baseIsLeftToRight()

/**
 * [focus] moved by one character toward the end ([forward]) or the start of the text: a letter with its nikud and
 * teamim (a grapheme), the end of a line and the start of the next being one step apart, as the copied text's line
 * break is.
 */
internal fun moveByCharacter(
    focus: TextPoint,
    forward: Boolean,
    textOf: (Int) -> String?,
): TextPoint {
    val text = textOf(focus.lineIndex) ?: return focus
    val offset = focus.offset.coerceIn(0, text.length)
    val characters = BreakIterator.getCharacterInstance().apply { setText(text) }
    return when {
        forward && offset < text.length -> TextPoint(focus.lineIndex, characters.following(offset))
        forward -> if (textOf(focus.lineIndex + 1) != null) TextPoint(focus.lineIndex + 1, 0) else TextPoint(focus.lineIndex, offset)
        offset > 0 -> TextPoint(focus.lineIndex, characters.preceding(offset))
        else -> textOf(focus.lineIndex - 1)?.let { TextPoint(focus.lineIndex - 1, it.length) } ?: TextPoint(focus.lineIndex, 0)
    }
}

/**
 * [focus] moved by one word: toward the end ([forward]) to the end of the word it is in or the next one, toward the
 * start to the start of the word it is in or the previous one; from a line's edge, to the adjacent line's.
 */
internal fun moveByWord(
    focus: TextPoint,
    forward: Boolean,
    textOf: (Int) -> String?,
): TextPoint {
    val text = textOf(focus.lineIndex) ?: return focus
    val offset = focus.offset.coerceIn(0, text.length)
    if (forward && offset == text.length) return moveByCharacter(focus, forward = true, textOf)
    if (!forward && offset == 0) return moveByCharacter(focus, forward = false, textOf)
    val words = wordsOf(text)
    val moved =
        if (forward) {
            words.firstOrNull { it.last + 1 > offset }?.let { it.last + 1 } ?: text.length
        } else {
            words.lastOrNull { it.first < offset }?.first ?: 0
        }
    return TextPoint(focus.lineIndex, moved)
}

/**
 * The words of [text] (segments holding a letter or a digit), by their offsets. A geresh or gershayim between
 * letters (רש״י, ר׳) is inside its word, which the JDK's word breaks are not aware of.
 */
internal fun wordsOf(text: String): List<IntRange> {
    val boundaries = BreakIterator.getWordInstance().apply { setText(text) }
    val words = mutableListOf<IntRange>()
    var start = boundaries.first()
    var end = boundaries.next()
    while (end != BreakIterator.DONE) {
        if ((start until end).any { text[it].isLetterOrDigit() }) {
            val previous = words.lastOrNull()
            words +=
                if (previous != null && previous.last + 2 == start && text[start - 1] in IN_WORD_MARKS) {
                    words.removeAt(words.lastIndex)
                    previous.first until end
                } else {
                    start until end
                }
        }
        start = end
        end = boundaries.next()
    }
    return words
}

// Geresh and gershayim, Hebrew and as typed (an apostrophe, a double quote): part of an abbreviation's word
private const val IN_WORD_MARKS = "׳״'\""

/**
 * [focus] moved by one visual line, [down] or up, at the x [goalX] (the focus's own when null) — returned with the
 * x it kept to. On the first or last visual line of a book line it moves into the adjacent book line, at the same x
 * when that line is laid out, else at the same offset; without a layout for the focus's line, at the same offset.
 * Past the book's first or last line it goes to that line's start or end.
 */
internal fun moveByVisualLine(
    focus: TextPoint,
    down: Boolean,
    goalX: Float?,
    geometryOf: (Int) -> LineGeometry?,
    textOf: (Int) -> String?,
): Pair<TextPoint, Float?> {
    val target = focus.lineIndex + if (down) 1 else -1
    val geometry = geometryOf(focus.lineIndex)

    fun sameOffset(): Pair<TextPoint, Float?> {
        val targetText = textOf(target)
        if (targetText == null) {
            val length = geometry?.length ?: textOf(focus.lineIndex)?.length ?: focus.offset
            return TextPoint(focus.lineIndex, if (down) length else 0) to goalX
        }
        return TextPoint(target, focus.offset.coerceIn(0, targetText.length)) to goalX
    }

    if (geometry == null) return sameOffset()
    val offset = focus.offset.coerceIn(0, geometry.length)
    val x = goalX ?: geometry.xOf(offset)
    val visualLine = geometry.lineOf(offset) + if (down) 1 else -1
    if (visualLine in 0 until geometry.lineCount) return TextPoint(focus.lineIndex, geometry.offsetAt(visualLine, x)) to x
    val next = geometryOf(target) ?: return sameOffset().let { (point, _) -> point to x }
    return TextPoint(target, next.offsetAt(if (down) 0 else next.lineCount - 1, x)) to x
}

/**
 * Shift+Left/Right (a character; a word with Ctrl) and Shift+Up/Down (a visual line) move the selection's focus, as
 * in a text editor, the first move seeding it from the current selection ([BookSelection.seedForKeys]). [textOf]
 * gives a line's displayed text, null past the book's ends or for a line not loaded. True when the key is taken:
 * moved, or held while Compose's selection is still being located. False, the key left to the other handlers, for
 * other keys and without a selection to move from. Ctrl+Shift+Up/Down and any Alt or Meta combination
 * (Ctrl+Alt+Left/Right is the window's) are not moves.
 */
fun BookSelection.moveFocusByKey(
    event: KeyEvent,
    textOf: (Int) -> String?,
): Boolean {
    if (event.type != KeyEventType.KeyDown || !event.isShiftPressed || event.isAltPressed || event.isMetaPressed) return false
    val horizontal = event.key == Key.DirectionLeft || event.key == Key.DirectionRight
    val vertical = event.key == Key.DirectionUp || event.key == Key.DirectionDown
    if (!horizontal && !(vertical && !event.isCtrlPressed)) return false
    if (!seedForKeys()) return composeSelectionActive
    val from = focus ?: return false
    if (horizontal) {
        // From the text itself (its first strong character), not the layout's paragraph direction: Hebrew runs right
        // to left on screen even in a paragraph laid out left to right
        val rtl = isRtlText(layoutOf(from.lineIndex)?.layoutInput?.text?.text ?: textOf(from.lineIndex).orEmpty())
        val forward = towardsEnd(left = event.key == Key.DirectionLeft, rtl = rtl)
        extend(if (event.isCtrlPressed) moveByWord(from, forward, textOf) else moveByCharacter(from, forward, textOf))
    } else {
        val (moved, goalX) =
            moveByVisualLine(
                focus = from,
                down = event.key == Key.DirectionDown,
                goalX = goalX,
                geometryOf = { layoutOf(it)?.geometry() },
                textOf = textOf,
            )
        extend(moved, goalX)
    }
    return true
}
