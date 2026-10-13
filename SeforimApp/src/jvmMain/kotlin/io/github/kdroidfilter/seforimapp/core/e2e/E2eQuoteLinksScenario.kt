package io.github.kdroidfilter.seforimapp.core.e2e

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.PlatformRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import dev.nucleusframework.window.ExperimentalNucleusApi
import dev.nucleusframework.window.tao.TaoRootsForTest
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.features.bookcontent.quotelinks.installedQuoteLinks
import io.github.kdroidfilter.seforimapp.core.presentation.theme.IntUiThemes
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * The verse quote links of the Talmud in the reader (`ZAYIT_E2E_SCENARIO=quotelinks`, a build with
 * `-Pquotelinks.local=true`): Berakhot opened on 5a:3, a real click on its quote of Exodus 24:12, which must open that
 * verse in a new tab. Mouse events go through Compose's input pipeline ([TaoRootsForTest]).
 */
@OptIn(ExperimentalNucleusApi::class, InternalComposeUiApi::class)
object E2eQuoteLinksScenario {
    private const val LIMIT_MS = 20_000L
    private const val SOURCE = "ברכות ה., ג"
    private const val TARGET = "שמות כד, יב"

    suspend fun run(sc: E2eScenario) {
        if (E2e.scenario != "quotelinks") return
        val graph = sc.graph()
        val quoteLinks = installedQuoteLinks ?: error("built without the quote links (-Pquotelinks.local=true)")
        val berakhot = graph.repository.getBookByTitle("ברכות") ?: error("ברכות not found")
        val exodus = graph.repository.getBookByTitle("שמות") ?: error("שמות not found")
        val source = graph.repository.getLines(berakhot.id, 0, berakhot.totalLines).first { it.heRef == SOURCE }
        val target = graph.repository.getLines(exodus.id, 0, exodus.totalLines).first { it.heRef == TARGET }
        sc.note("QUOTELINKS covers ברכות=${quoteLinks.covers(berakhot.id)} שמות=${quoteLinks.covers(exodus.id)}")

        val tabs =
            graph.desktopManager.windows.value
                .first()
                .tabsViewModel
        E2eReader.readyLines.clear()
        tabs.openTab(TabsDestination.BookContent(bookId = berakhot.id, tabId = UUID.randomUUID().toString(), lineId = source.id))
        check(waitFor { E2eReader.book?.let { it.first == berakhot.id && it.third } == true && source.id in E2eReader.readyLines }) {
            "$SOURCE not shown"
        }
        sc.step("ql-01-berakhot", 1500)

        val list = E2eReader.book!!.second
        val item = list.layoutInfo.visibleItemsInfo.first { it.key == source.id }
        val coords = E2eReader.bookList ?: error("no book list coordinates")
        val origin = coords.positionInRoot()
        val itemBounds = Rect(origin.x, origin.y + item.offset, origin.x + coords.size.width, origin.y + item.offset + item.size)
        val root = TaoRootsForTest.roots.maxBy { it.visibleBounds.width * it.visibleBounds.height }

        // The links of the line, as the text exposes them to accessibility; a sweep of clicks over the line otherwise
        val links =
            root.semanticsOwner.rootSemanticsNode
                .all()
                .filter { SemanticsActions.OnClick in it.config && itemBounds.contains(it.boundsInRoot.center) }
                .filter { it.boundsInRoot.width < itemBounds.width * 0.9f }
        sc.note("QUOTELINKS clickable nodes in the line: ${links.size} ${links.map { it.boundsInRoot }}")
        // The link's look: its color, its tint under the pointer, in both themes
        links.firstOrNull()?.let { root.sendPointerEvent(PointerEventType.Move, it.boundsInRoot.center) }
        sc.step("ql-01b-hover", 600)
        graph.mainAppState.setTheme(IntUiThemes.Dark)
        sc.step("ql-01c-dark", 1200)
        graph.mainAppState.setTheme(IntUiThemes.Light)
        delay(1200)
        val before = tabs.state.value.tabs.size
        val points =
            links.map { it.boundsInRoot.center } +
                (0 until (item.size / ROW_STEP).toInt()).flatMap { row ->
                    (1 until (coords.size.width / COLUMN_STEP).toInt()).map { col ->
                        Offset(origin.x + coords.size.width - col * COLUMN_STEP, itemBounds.top + ROW_STEP / 2 + row * ROW_STEP)
                    }
                }
        var clicks = 0
        val opened =
            points.firstNotNullOfOrNull { at ->
                clicks++
                click(root, at)
                if (waitFor(
                        400,
                    ) { tabs.state.value.tabs.size > before }
                ) {
                    tabs.state.value.let { it.tabs[it.selectedTabIndex].destination }
                } else {
                    null
                }
            }
        sc.step("ql-02-clicked", 1500)
        val ok = opened is TabsDestination.BookContent && opened.bookId == exodus.id && opened.lineId == target.id
        sc.note(
            "QUOTELINKS click #$clicks opened $opened (expected book ${exodus.id} line ${target.id}): " +
                if (ok) "OK" else "FAILED",
        )
    }

    private const val ROW_STEP = 18f
    private const val COLUMN_STEP = 28f

    private fun SemanticsNode.all(): List<SemanticsNode> = listOf(this) + children.flatMap { it.all() }

    private suspend fun click(
        root: PlatformRootForTest,
        at: Offset,
    ) {
        root.sendPointerEvent(PointerEventType.Move, at)
        delay(30)
        root.sendPointerEvent(PointerEventType.Press, at, buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary)
        delay(60)
        root.sendPointerEvent(PointerEventType.Release, at, buttons = PointerButtons(), button = PointerButton.Primary)
    }

    private suspend fun waitFor(
        limit: Long = LIMIT_MS,
        condition: () -> Boolean,
    ): Boolean =
        withTimeoutOrNull(limit) {
            while (!condition()) delay(10)
            true
        } ?: false
}
