package io.github.kdroidfilter.seforimapp.core.e2e

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.PlatformRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import dev.nucleusframework.window.ExperimentalNucleusApi
import dev.nucleusframework.window.tao.TaoRootsForTest
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.presentation.text.DiacriticsMode
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.displayedLineText
import io.github.kdroidfilter.seforimlibrary.core.text.HebrewTextUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.util.UUID

/**
 * Shift+click selection across lines never composed (Zayit #48), `ZAYIT_E2E_SCENARIO=shiftselect`: a real click on a
 * line of Bereshit, a jump [JUMP] lines down, a real Shift+click, then checks that the selection (and Ctrl+C) holds
 * every line in between, in order. Mouse and key events go through Compose's input pipeline ([TaoRootsForTest]).
 */
@OptIn(ExperimentalNucleusApi::class, InternalComposeUiApi::class)
object E2eShiftSelectScenario {
    private const val JUMP = 400
    private const val LIMIT_MS = 20_000L

    suspend fun run(sc: E2eScenario) {
        if (E2e.scenario != "shiftselect") return
        val graph = sc.graph()
        val book = graph.repository.getBookByTitle("בראשית") ?: error("book not found")
        val tabs =
            graph.desktopManager.windows.value
                .first()
                .tabsViewModel
        tabs.openTab(TabsDestination.BookContent(bookId = book.id, tabId = UUID.randomUUID().toString()))
        check(waitFor { E2eReader.book?.let { it.first == book.id && it.third } == true }) { "book not shown" }
        delay(2000)
        val list = E2eReader.book!!.second
        val root = TaoRootsForTest.roots.maxBy { it.visibleBounds.width * it.visibleBounds.height }

        // A plain drag selects as before
        var wordOk = false
        run {
            val items = list.layoutInfo.visibleItemsInfo
            val coords = E2eReader.bookList ?: error("no book list coordinates")
            val o = coords.positionInRoot()

            fun at(i: LazyListItemInfo) = Offset(o.x + coords.size.width * 0.85f, o.y + i.offset + i.size - 24f)
            root.sendPointerEvent(PointerEventType.Move, at(items[2]))
            delay(50)
            root.sendPointerEvent(
                PointerEventType.Press,
                at(items[2]),
                buttons = PointerButtons(isPrimaryPressed = true),
                button = PointerButton.Primary,
            )
            for (k in 1..10) {
                delay(30)
                root.sendPointerEvent(
                    PointerEventType.Move,
                    at(items[2]) + (at(items[5]) - at(items[2])) * (k / 10f),
                    buttons = PointerButtons(isPrimaryPressed = true),
                )
            }
            root.sendPointerEvent(PointerEventType.Release, at(items[5]), buttons = PointerButtons(), button = PointerButton.Primary)
            sc.step("ss-00-drag", 800)
            sc.note("SHIFTSELECT drag selected=${graph.selectionContext.selectedText.value.length} chars")

            // A short word, double-clicked, then highlighted: on its own line only, though it occurs earlier
            val wordItem = items[5]
            val wordLine = graph.repository.getLine(wordItem.key as Long) ?: error("no word line")
            val earlier =
                graph.repository
                    .getLines(book.id, 0, wordLine.lineIndex - 1)
                    .map { displayedLineText(it.content, DiacriticsMode.All) }
            var word = at(wordItem)
            var selectedWord = ""
            // Double-clicks words along the line until one that also occurs on an earlier line
            for (dx in listOf(-60f, -140f, -220f, -300f, -380f, -460f, -540f)) {
                word = at(wordItem) + Offset(dx, 0f)
                repeat(2) {
                    root.sendPointerEvent(
                        PointerEventType.Press,
                        word,
                        buttons = PointerButtons(isPrimaryPressed = true),
                        button = PointerButton.Primary,
                    )
                    delay(40)
                    root.sendPointerEvent(PointerEventType.Release, word, buttons = PointerButtons(), button = PointerButton.Primary)
                    delay(80)
                }
                delay(800)
                selectedWord =
                    graph.selectionContext.selectedText.value
                        .trim()
                if (selectedWord.isNotEmpty() && earlier.any { plain(it).contains(plain(selectedWord)) }) break
                delay(600)
            }
            graph.highlightStore.loadBook(book.id)
            val wordHighlighted =
                rightClick(root, word) &&
                    pickColor() &&
                    waitFor { graph.highlightStore.highlightsForLine(book.id, wordItem.key as Long).isNotEmpty() }
            delay(500)
            val elsewhere =
                graph.highlightStore.highlightsByBook.value[book.id]
                    .orEmpty()
                    .filter { it.lineId != wordItem.key as Long }
            wordOk = wordHighlighted && elsewhere.isEmpty() && selectedWord.isNotBlank()
            val occursEarlier = earlier.any { plain(it).contains(plain(selectedWord)) }
            wordOk = wordOk && occursEarlier
            sc.note(
                "SHIFTSELECT word '$selectedWord' (also earlier=$occursEarlier) highlighted on its line=$wordHighlighted " +
                    "elsewhere=${elsewhere.size}",
            )
            sc.step("ss-00-word", 300)
            // A plain click clears the word selection before the Shift+click part
            delay(700)
        }

        // A plain click inside a line, then a Shift+click [JUMP] lines down
        val anchorItem = list.layoutInfo.visibleItemsInfo[2]
        click(root, anchorItem, shift = false)
        sc.step("ss-01-anchor", 800)
        list.scrollToItem(anchorItem.index + JUMP)
        delay(1500)
        val targetItem = list.layoutInfo.visibleItemsInfo[3]
        click(root, targetItem, shift = true)
        val completed = waitFor { graph.selectionContext.selectedLineRange.value != null }
        sc.step("ss-02-shift-click", 800)

        val anchor = graph.repository.getLine(anchorItem.key as Long) ?: error("no anchor line")
        val target = graph.repository.getLine(targetItem.key as Long) ?: error("no target line")
        val text = graph.selectionContext.selectedText.value
        val range = graph.selectionContext.selectedLineRange.value
        sc.note(
            "SHIFTSELECT anchor=${anchor.lineIndex} target=${target.lineIndex} completed=$completed " +
                "range=${range?.let { "${it.first.lineIndex}..${it.second.lineIndex}" }} " +
                "pieces=${text.split('\n').size}",
        )

        // Every line in between, in order, as displayed (compared without diacritics, whatever the reading mode)
        val interior = graph.repository.getLines(book.id, anchor.lineIndex + 1, target.lineIndex - 1)
        val expected = interior.map { plain(displayedLineText(it.content, DiacriticsMode.All)) }.filter { it.isNotEmpty() }
        val pieces = text.split('\n').map(::plain)
        val middle = pieces.drop(1).dropLast(1)
        val linesOk = middle == expected
        val edgesOk =
            plain(displayedLineText(anchor.content, DiacriticsMode.All)).endsWith(pieces.first()) &&
                plain(displayedLineText(target.content, DiacriticsMode.All)).startsWith(pieces.last())
        sc.note("SHIFTSELECT interior lines expected=${expected.size} got=${middle.size} match=$linesOk edges=$edgesOk")

        // Ctrl+C copies the whole selection
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection("e2e-sentinel"), null)
        val handled = root.sendKeyEvent(KeyEvent(Key.C, KeyEventType.KeyDown, isCtrlPressed = true))
        root.sendKeyEvent(KeyEvent(Key.C, KeyEventType.KeyUp, isCtrlPressed = true))
        sc.note("SHIFTSELECT ctrl+c handled=$handled")
        delay(500)
        val clip = Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as String
        sc.note("SHIFTSELECT ctrl+c copied=${clip.length} chars equal=${clip == text}")

        // A right-click in the selection keeps it, and its highlight color reaches the lines never composed
        val rightClickAt =
            run {
                val coords = E2eReader.bookList ?: error("no book list coordinates")
                val o = coords.positionInRoot()
                Offset(o.x + coords.size.width * 0.97f, o.y + targetItem.offset + targetItem.size - 24f)
            }
        val menuOpened = rightClick(root, rightClickAt)
        val kept = graph.selectionContext.selectedText.value == text && graph.selectionContext.selectedLineRange.value != null
        val mark = menuOpened && pickColor()
        val middleLine = interior[interior.size / 2]
        val highlighted = mark && waitFor { graph.highlightStore.highlightsForLine(book.id, middleLine.id).isNotEmpty() }
        sc.note("SHIFTSELECT right-click kept=$kept menu=$mark highlight reaches line never composed=$highlighted")

        // The lines between, scrolled back to: drawn selected as well?
        list.scrollToItem((anchorItem.index + targetItem.index) / 2)
        sc.step("ss-03-middle", 1500)
        // A Shift+click while a drag selection stays extends from the drag's start
        list.scrollToItem(0)
        delay(1500)
        val top = list.layoutInfo.visibleItemsInfo
        val coords = E2eReader.bookList ?: error("no book list coordinates")
        val o = coords.positionInRoot()

        fun at(i: LazyListItemInfo) = Offset(o.x + coords.size.width * 0.85f, o.y + i.offset + i.size - 24f)
        root.sendPointerEvent(
            PointerEventType.Press,
            at(top[2]),
            buttons = PointerButtons(isPrimaryPressed = true),
            button = PointerButton.Primary,
        )
        for (k in 1..5) {
            delay(30)
            root.sendPointerEvent(
                PointerEventType.Move,
                at(top[2]) + (at(top[3]) - at(top[2])) * (k / 5f),
                buttons = PointerButtons(isPrimaryPressed = true),
            )
        }
        root.sendPointerEvent(PointerEventType.Release, at(top[3]), buttons = PointerButtons(), button = PointerButton.Primary)
        delay(800)
        click(root, top[7], shift = true)
        val afterDrag =
            waitFor {
                graph.selectionContext.selectedLineRange.value
                    ?.second
                    ?.id == top[7].key as Long
            }
        sc.note("SHIFTSELECT shift+click after a drag extends=$afterDrag")

        // A press nobody takes (the gap between two lines) deselects
        val margin = Offset(o.x + coords.size.width * 0.85f, o.y + top[6].offset + 1f)
        root.sendPointerEvent(
            PointerEventType.Press,
            margin,
            buttons = PointerButtons(isPrimaryPressed = true),
            button = PointerButton.Primary,
        )
        delay(60)
        root.sendPointerEvent(PointerEventType.Release, margin, buttons = PointerButtons(), button = PointerButton.Primary)
        val deselected = waitFor { graph.selectionContext.selectedLineRange.value == null }
        sc.note("SHIFTSELECT press between lines deselects=$deselected")
        sc.step("ss-04-deselected", 500)

        sc.note(
            "SHIFTSELECT RESULT " +
                if (wordOk &&
                    completed &&
                    linesOk &&
                    edgesOk &&
                    clip == text &&
                    kept &&
                    highlighted &&
                    afterDrag &&
                    deselected
                ) {
                    "PASS"
                } else {
                    "FAIL"
                },
        )
    }

    private suspend fun click(
        root: PlatformRootForTest,
        item: LazyListItemInfo,
        shift: Boolean,
    ) {
        val coords = E2eReader.bookList ?: error("no book list coordinates")
        val origin = coords.positionInRoot()
        val at = Offset(origin.x + coords.size.width * 0.85f, origin.y + item.offset + item.size - 24f)
        val keys = PointerKeyboardModifiers(isShiftPressed = shift)
        root.sendPointerEvent(PointerEventType.Move, at, keyboardModifiers = keys)
        delay(50)
        root.sendPointerEvent(
            PointerEventType.Press,
            at,
            buttons = PointerButtons(isPrimaryPressed = true),
            keyboardModifiers = keys,
            button = PointerButton.Primary,
        )
        delay(80)
        root.sendPointerEvent(
            PointerEventType.Release,
            at,
            buttons = PointerButtons(),
            keyboardModifiers = keys,
            button = PointerButton.Primary,
        )
    }

    private fun plain(text: String) = HebrewTextUtils.removeAllDiacritics(text).trim()

    private suspend fun waitFor(condition: () -> Boolean): Boolean =
        withTimeoutOrNull(LIMIT_MS) {
            while (!condition()) delay(10)
            true
        } ?: false

    private fun SemanticsNode.find(predicate: (SemanticsNode) -> Boolean): SemanticsNode? =
        if (predicate(this)) this else children.firstNotNullOfOrNull { it.find(predicate) }

    /** Right-clicks at [at]; true once the text menu is open. */
    private suspend fun rightClick(
        root: PlatformRootForTest,
        at: Offset,
    ): Boolean {
        root.sendPointerEvent(PointerEventType.Move, at)
        root.sendPointerEvent(
            PointerEventType.Press,
            at,
            buttons = PointerButtons(isSecondaryPressed = true),
            button = PointerButton.Secondary,
        )
        delay(60)
        root.sendPointerEvent(PointerEventType.Release, at, buttons = PointerButtons(), button = PointerButton.Secondary)
        return waitFor { TaoRootsForTest.roots.size > 1 }
    }

    /** Clicks the first highlight color of the open text menu. */
    private suspend fun pickColor(): Boolean {
        delay(500)
        val popup = TaoRootsForTest.roots.last()
        val mark =
            popup.semanticsOwner.unmergedRootSemanticsNode.find {
                it.config.getOrNull(SemanticsProperties.Text)?.joinToString() == "סמן"
            } ?: return false
        val swatch = Offset(mark.boundsInRoot.left - 23f, mark.boundsInRoot.center.y)
        popup.sendPointerEvent(PointerEventType.Move, swatch)
        delay(50)
        popup.sendPointerEvent(
            PointerEventType.Press,
            swatch,
            buttons = PointerButtons(isPrimaryPressed = true),
            button = PointerButton.Primary,
        )
        delay(60)
        popup.sendPointerEvent(PointerEventType.Release, swatch, buttons = PointerButtons(), button = PointerButton.Primary)
        return true
    }
}
