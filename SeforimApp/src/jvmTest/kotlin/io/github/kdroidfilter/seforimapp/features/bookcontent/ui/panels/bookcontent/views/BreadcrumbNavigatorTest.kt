package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.BreadcrumbNode.BookNode
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.BreadcrumbNode.CategoryNode
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.BreadcrumbNode.TocNode
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BreadcrumbNavigatorTest {
    // משנה > { סדר זרעים > { ברכות, פאה }, סדר קדשים > { מסכת קטנה > { תמיד } } }
    private val mishna = CategoryNode(Category(id = 1, title = "משנה"))
    private val zeraim = CategoryNode(Category(id = 2, parentId = 1, title = "סדר זרעים"))
    private val kodashim = CategoryNode(Category(id = 3, parentId = 1, title = "סדר קדשים"))
    private val single = CategoryNode(Category(id = 4, parentId = 3, title = "מסכת קטנה"))
    private val brachot = BookNode(Book(id = 10, categoryId = 2, sourceId = 1, title = "משנה ברכות"))
    private val peah = BookNode(Book(id = 11, categoryId = 2, sourceId = 1, title = "משנה פאה"))
    private val tamid = BookNode(Book(id = 12, categoryId = 4, sourceId = 1, title = "משנה תמיד"))
    private val perek1 = TocNode(TocEntry(id = 100, bookId = 10, text = "פרק א", level = 1, lineId = 1000))
    private val perek2 = TocNode(TocEntry(id = 101, bookId = 10, text = "פרק ב", level = 1, lineId = 1001))

    private val tree: Map<String, List<BreadcrumbNode>> =
        mapOf(
            mishna.key to listOf(zeraim, kodashim),
            zeraim.key to listOf(brachot, peah),
            kodashim.key to listOf(single),
            single.key to listOf(tamid),
            brachot.key to listOf(perek1, perek2),
            tamid.key to listOf(TocNode(TocEntry(id = 200, bookId = 12, text = "פרק א", level = 1))),
        )
    private val provider = BreadcrumbChildrenProvider { tree[it.key].orEmpty() }

    private val path = listOf(mishna, zeraim, brachot, perek1)

    private var now = 0L

    private fun TestScope.navigator(
        navigated: MutableList<BreadcrumbNode>,
        children: BreadcrumbChildrenProvider = provider,
    ) = BreadcrumbNavigator(backgroundScope, children, clock = { now }) { navigated += it }.apply { updatePath(path) }

    @Test
    fun `clicking a segment shows its children with the next segment preselected`() =
        runTest {
            val nav = navigator(mutableListOf())
            nav.showPopup(1)
            runCurrent()

            val popup = nav.popup!!
            assertEquals(1, popup.anchorIndex)
            assertEquals(listOf(brachot, peah), popup.items)
            assertEquals(0, popup.initialSelectedIndex)
            assertEquals(1, nav.selectedIndex)
        }

    @Test
    fun `clicking a leaf segment opens its parent popup with the leaf preselected`() =
        runTest {
            val navigated = mutableListOf<BreadcrumbNode>()
            val nav = navigator(navigated)
            nav.showPopup(3)
            runCurrent()

            val popup = nav.popup!!
            assertEquals(2, popup.anchorIndex)
            assertEquals(listOf<BreadcrumbNode>(perek1, perek2), popup.items)
            assertEquals(0, popup.initialSelectedIndex)
            assertEquals(2, nav.selectedIndex)
            assertEquals(emptyList(), navigated)
        }

    @Test
    fun `choosing a book navigates to it`() =
        runTest {
            val navigated = mutableListOf<BreadcrumbNode>()
            val nav = navigator(navigated)
            nav.showPopup(1)
            runCurrent()
            nav.choose(peah)
            runCurrent()

            assertNull(nav.popup)
            assertEquals(listOf<BreadcrumbNode>(peah), navigated)
            assertEquals(path, nav.items)
        }

    @Test
    fun `choosing a folder extends the bar and skips single-child levels`() =
        runTest {
            val navigated = mutableListOf<BreadcrumbNode>()
            val nav = navigator(navigated)
            nav.showPopup(0)
            runCurrent()
            nav.choose(kodashim)
            runCurrent()

            // סדר קדשים has a single child, itself holding a single book: the book is offered alone
            assertEquals(listOf(mishna, kodashim, single), nav.items)
            assertEquals(2, nav.popup!!.anchorIndex)
            assertEquals(listOf<BreadcrumbNode>(tamid), nav.popup!!.items)
            assertEquals(emptyList(), navigated)
        }

    @Test
    fun `outside click keeps the drilled-down bar, escape restores the path`() =
        runTest {
            val nav = navigator(mutableListOf())
            nav.showPopup(0)
            runCurrent()
            nav.choose(kodashim)
            runCurrent()

            nav.dismiss()
            assertNull(nav.popup)
            assertEquals(listOf(mishna, kodashim, single), nav.items)

            nav.showPopup(2)
            runCurrent()
            nav.cancel()
            assertNull(nav.popup)
            assertEquals(path, nav.items)
        }

    @Test
    fun `shifting moves the popup to the neighboring segment`() =
        runTest {
            val nav = navigator(mutableListOf())
            nav.showPopup(1)
            runCurrent()
            nav.shiftPopup(-1)
            runCurrent()

            assertEquals(0, nav.popup!!.anchorIndex)
            assertEquals(listOf(zeraim, kodashim), nav.popup!!.items)
            assertEquals(0, nav.popup!!.initialSelectedIndex)
        }

    @Test
    fun `a new path resets a drilled-down bar once no popup is open`() =
        runTest {
            val nav = navigator(mutableListOf())
            nav.showPopup(0)
            runCurrent()
            nav.choose(kodashim)
            runCurrent()
            nav.dismiss()
            val newPath = listOf(mishna, zeraim, brachot, perek2)
            nav.updatePath(newPath)

            assertNull(nav.popup)
            assertEquals(newPath, nav.items)
        }

    @Test
    fun `shifting skips a leaf segment and stops at both ends`() =
        runTest {
            val nav = navigator(mutableListOf())
            nav.showPopup(2)
            runCurrent()

            // Past בְּרָכוֹת only the leaf פרק א: the popup stays
            nav.shiftPopup(1)
            runCurrent()
            assertEquals(2, nav.popup!!.anchorIndex)

            nav.shiftPopup(-1)
            runCurrent()
            nav.shiftPopup(-1)
            runCurrent()
            nav.shiftPopup(-1)
            runCurrent()
            assertEquals(0, nav.popup!!.anchorIndex)
        }

    @Test
    fun `a segment without any popup leaves no selection`() =
        runTest {
            val nav = navigator(mutableListOf(), children = { emptyList() })
            nav.showPopup(0)
            runCurrent()

            assertNull(nav.popup)
            assertEquals(-1, nav.selectedIndex)
        }

    @Test
    fun `clicking the segment of the open popup closes it`() =
        runTest {
            val nav = navigator(mutableListOf())
            nav.onSegmentClick(zeraim.key)
            runCurrent()

            // The outside click dismisses on press, the segment receives the click on release
            nav.dismiss()
            now += 100
            nav.onSegmentClick(zeraim.key)
            runCurrent()
            assertNull(nav.popup)

            // A later click opens it again
            now += 1_000
            nav.onSegmentClick(zeraim.key)
            runCurrent()
            assertEquals(1, nav.popup!!.anchorIndex)
        }

    @Test
    fun `clicking another segment moves the popup to it`() =
        runTest {
            val nav = navigator(mutableListOf())
            nav.onSegmentClick(zeraim.key)
            runCurrent()

            nav.dismiss()
            nav.onSegmentClick(mishna.key)
            runCurrent()
            assertEquals(0, nav.popup!!.anchorIndex)
        }

    @Test
    fun `after drilling down, clicking the new anchor closes the popup`() =
        runTest {
            val nav = navigator(mutableListOf())
            nav.onSegmentClick(mishna.key)
            runCurrent()
            nav.choose(kodashim)
            runCurrent()

            nav.dismiss()
            nav.onSegmentClick(single.key)
            runCurrent()
            assertNull(nav.popup)
        }

    @Test
    fun `an outside click brings the current path back once its click had the chance to land`() =
        runTest {
            val nav = navigator(mutableListOf())
            nav.showPopup(0)
            runCurrent()
            nav.choose(kodashim)
            runCurrent()

            nav.dismiss()
            assertEquals(listOf(mishna, kodashim, single), nav.items)
            advanceTimeBy(600)
            runCurrent()
            assertEquals(path, nav.items)

            // A click resolves its segment by key: one gone from the bar opens nothing
            nav.onSegmentClick(single.key)
            runCurrent()
            assertNull(nav.popup)
        }

    @Test
    fun `a click on a drilled-down segment right after an outside click still opens it`() =
        runTest {
            val nav = navigator(mutableListOf())
            nav.showPopup(0)
            runCurrent()
            nav.choose(kodashim)
            runCurrent()

            nav.dismiss()
            nav.onSegmentClick(kodashim.key)
            advanceTimeBy(600)
            runCurrent()
            assertEquals(1, nav.popup!!.anchorIndex)
            assertEquals(listOf(mishna, kodashim, single), nav.items)
        }

    @Test
    fun `a double click navigates to the segment`() =
        runTest {
            val navigated = mutableListOf<BreadcrumbNode>()
            val nav = navigator(navigated)
            nav.onSegmentDoubleClick(brachot.key)

            assertEquals(listOf<BreadcrumbNode>(brachot), navigated)
        }

    @Test
    fun `a path change waits for the open popup to close`() =
        runTest {
            val nav = navigator(mutableListOf())
            nav.showPopup(0)
            runCurrent()
            nav.choose(kodashim)
            runCurrent()

            val newPath = listOf(mishna, zeraim, brachot, perek2)
            nav.updatePath(newPath)
            assertEquals(listOf(mishna, kodashim, single), nav.items)

            nav.cancel()
            assertEquals(newPath, nav.items)
        }

    @Test
    fun `a heading without a line leads nowhere and keeps the bar`() =
        runTest {
            val navigated = mutableListOf<BreadcrumbNode>()
            val heading = TocNode(TocEntry(id = 300, bookId = 10, text = "הקדמה", level = 1))
            val nav = navigator(navigated, children = { if (it.key == brachot.key) listOf(heading) else tree[it.key].orEmpty() })
            nav.showPopup(2)
            runCurrent()
            nav.choose(heading)
            runCurrent()

            assertEquals(emptyList(), navigated)
            assertEquals(2, nav.popup!!.anchorIndex)
        }

    @Test
    fun `book popup replaces a title root by its children, as the path does`() =
        runTest {
            val titleRoot = TocEntry(id = 1, bookId = 10, text = "משנה ברכות", level = 0, hasChildren = true)
            val intro = TocEntry(id = 2, bookId = 10, text = "מבוא", level = 0)
            val provider =
                CatalogBreadcrumbChildrenProvider(
                    categoryChildren = emptyMap(),
                    booksByCategory = emptyMap(),
                    loadRootToc = { listOf(titleRoot, intro) },
                    loadTocChildren = { if (it == 1L) listOf(perek1.entry, perek2.entry) else emptyList() },
                )

            assertEquals(listOf<BreadcrumbNode>(perek1, perek2, TocNode(intro)), provider.children(brachot))
        }

    @Test
    fun `book popup keeps a title root without children, a line of its own`() =
        runTest {
            val titleRoot = TocEntry(id = 1, bookId = 10, text = "משנה ברכות", level = 0, lineId = 1)
            val provider =
                CatalogBreadcrumbChildrenProvider(
                    categoryChildren = emptyMap(),
                    booksByCategory = emptyMap(),
                    loadRootToc = { listOf(titleRoot, perek1.entry) },
                    loadTocChildren = { error("a leaf has no children to load") },
                )

            assertEquals(listOf<BreadcrumbNode>(TocNode(titleRoot), perek1), provider.children(brachot))
        }

    @Test
    fun `escape and choices hand the focus back, an outside click doesn't`() =
        runTest {
            var returns = 0
            val nav = BreadcrumbNavigator(backgroundScope, provider, initialPath = path, onReturnFocus = { returns++ }) {}
            assertEquals(path, nav.items)

            nav.showPopup(1)
            runCurrent()
            nav.dismiss()
            assertEquals(0, returns)

            nav.showPopup(1)
            runCurrent()
            nav.cancel()
            nav.showPopup(1)
            runCurrent()
            nav.choose(peah)
            runCurrent()
            // Closing nothing doesn't count
            nav.cancel()

            assertEquals(2, returns)
        }

    @Test
    fun `a popup still loading survives a path change and anchors by key`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val slow =
                BreadcrumbChildrenProvider { node ->
                    if (node.key ==
                        brachot.key
                    ) {
                        gate.await().let { tree[node.key].orEmpty() }
                    } else {
                        tree[node.key].orEmpty()
                    }
                }
            val nav = navigator(mutableListOf(), children = slow)
            nav.showPopup(2)
            runCurrent()

            // The selected line moved meanwhile: the book segment now sits elsewhere in the bar
            nav.updatePath(listOf(zeraim, brachot, perek2))
            gate.complete(Unit)
            runCurrent()

            assertEquals(1, nav.popup!!.anchorIndex)
            assertEquals(listOf<BreadcrumbNode>(perek1, perek2), nav.popup!!.items)
        }

    @Test
    fun `the parent anchoring a leaf's popup closes it on a click too`() =
        runTest {
            var returns = 0
            val nav = BreadcrumbNavigator(backgroundScope, provider, initialPath = path, onReturnFocus = { returns++ }, clock = { now }) {}
            nav.onSegmentClick(perek1.key)
            runCurrent()
            assertEquals(2, nav.popup!!.anchorIndex)

            nav.dismiss()
            nav.onSegmentClick(brachot.key)
            runCurrent()
            assertNull(nav.popup)
            // Closed from its segment: the focus goes back to the text, as with Escape
            assertEquals(1, returns)
        }

    @Test
    fun `a segment without any popup brings the current path back`() =
        runTest {
            var catalogEmptied = false
            val nav = navigator(mutableListOf(), children = { if (catalogEmptied) emptyList() else tree[it.key].orEmpty() })
            nav.showPopup(0)
            runCurrent()
            nav.choose(kodashim)
            runCurrent()
            nav.dismiss()

            catalogEmptied = true
            nav.onSegmentClick(mishna.key)
            runCurrent()
            assertNull(nav.popup)
            assertEquals(path, nav.items)
        }
}
