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

        // The lines between, scrolled back to: drawn selected as well?
        list.scrollToItem((anchorItem.index + targetItem.index) / 2)
        sc.step("ss-03-middle", 1500)
        sc.note(
            "SHIFTSELECT RESULT " +
                if (completed && linesOk && edgesOk && clip == text) "PASS" else "FAIL",
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
}
