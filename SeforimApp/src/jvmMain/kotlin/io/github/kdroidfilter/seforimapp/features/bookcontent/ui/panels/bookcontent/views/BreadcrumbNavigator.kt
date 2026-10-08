package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A segment of the book path: a category, the book itself, or one of its TOC headings. */
@Immutable
sealed interface BreadcrumbNode {
    /** Identity across reloads (models are compared by id, not by their whole content). */
    val key: String
    val title: String

    data class CategoryNode(
        val category: Category,
    ) : BreadcrumbNode {
        override val key: String get() = "c${category.id}"
        override val title: String get() = category.title
    }

    data class BookNode(
        val book: Book,
    ) : BreadcrumbNode {
        override val key: String get() = "b${book.id}"
        override val title: String get() = book.title
    }

    data class TocNode(
        val entry: TocEntry,
    ) : BreadcrumbNode {
        override val key: String get() = "t${entry.id}"
        override val title: String get() = entry.text
    }
}

fun interface BreadcrumbChildrenProvider {
    suspend fun children(node: BreadcrumbNode): List<BreadcrumbNode>
}

/** The child popup of the segment at [anchorIndex]. */
@Immutable
data class BreadcrumbPopupModel(
    val anchorIndex: Int,
    val items: List<BreadcrumbNode>,
    val initialSelectedIndex: Int,
)

/**
 * Navigation bar logic, modeled on IntelliJ's NavBarVmImpl: clicking a segment opens a popup of
 * its children with the next segment preselected; choosing a folder in the popup extends the bar
 * and opens the next popup, choosing a leaf navigates to it.
 */
@Stable
class BreadcrumbNavigator(
    @StructuredScope private val scope: CoroutineScope,
    private val childrenProvider: BreadcrumbChildrenProvider,
    initialPath: List<BreadcrumbNode> = emptyList(),
    // The popup closed from the inside (Escape, a choice): the keyboard focus goes back to the text.
    // Not on an outside click, which gives the focus to whatever it clicked.
    private val onReturnFocus: () -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
    private val onNavigate: (BreadcrumbNode) -> Unit,
) {
    private var contextItems: List<BreadcrumbNode> = initialPath
    private var job: Job? = null

    // The segment whose popup is open, and the one an outside click just closed: the press closes
    // the popup, then the release lands on the segment, which must not reopen it
    private var openedByKeys: Set<String> = emptySet()
    private var dismissedKeys: Set<String> = emptySet()
    private var dismissedAt = 0L

    /** The displayed segments: the current path, extended while drilling down through popups. */
    var items: List<BreadcrumbNode> by mutableStateOf(initialPath)
        private set

    var popup: BreadcrumbPopupModel? by mutableStateOf(null)
        private set

    /** The segment of the open popup, -1 without one. */
    val selectedIndex: Int get() = popup?.anchorIndex ?: -1

    /** A new path applies at once, or when the popup the user is browsing closes. */
    fun updatePath(path: List<BreadcrumbNode>) {
        if (path.map { it.key } == contextItems.map { it.key }) return
        contextItems = path
        // A popup still loading is left alone: it finds its segment by key once loaded
        if (popup == null) items = contextItems
    }

    /**
     * A click on a segment, identified by key (the outside click closing a popup may have swapped
     * the path in just before): opens its popup, or closes it when it was the open one.
     */
    fun onSegmentClick(key: String) {
        val closedJustNow = key in dismissedKeys && clock() - dismissedAt <= RECLICK_WINDOW_MS
        dismissedKeys = emptySet()
        if (closedJustNow) {
            // Closed from its segment as with Escape: the text gets the focus back
            reset(returnFocus = false)
            onReturnFocus()
            return
        }
        val index = items.indexOfFirst { it.key == key }
        if (index >= 0) showPopup(index)
    }

    /** A double click on a segment navigates to it. */
    fun onSegmentDoubleClick(key: String) {
        items.firstOrNull { it.key == key }?.let(::navigate)
    }

    /**
     * Opens the popup of the segment at [index], its next segment preselected. A leaf segment has no
     * popup of its own: as IntelliJ's selectTail, its parent's popup opens with the leaf preselected.
     */
    fun showPopup(index: Int) {
        val node = items.getOrNull(index) ?: return
        launch {
            val children = childrenProvider.children(node)
            val anchor = if (children.isEmpty()) items.getOrNull(index - 1) else node
            val popupItems = if (anchor == node) children else anchor?.let { childrenProvider.children(it) }.orEmpty()
            // The bar may have changed while loading: the anchor is found again by key
            val anchorIndex = items.indexOfFirst { it.key == anchor?.key }
            if (popupItems.isEmpty() || anchorIndex < 0) {
                reset(returnFocus = false)
            } else {
                openPopup(setOfNotNull(node.key, anchor?.key), anchorIndex, popupItems)
            }
        }
    }

    /** Moves the open popup to the neighboring segment, skipping leaves; stops at both ends of the bar. */
    fun shiftPopup(delta: Int) {
        val from = popup?.anchorIndex ?: return
        launch {
            var target = from + delta
            while (target in items.indices) {
                val children = childrenProvider.children(items[target])
                if (children.isNotEmpty()) {
                    openPopup(setOf(items[target].key), target, children)
                    return@launch
                }
                target += delta
            }
        }
    }

    /** Handles a choice in the popup. */
    fun choose(node: BreadcrumbNode) {
        val anchorIndex = popup?.anchorIndex ?: return
        launch {
            when (val result = autoExpand(node, childrenProvider)) {
                is ExpandResult.NavigateTo -> navigate(result.target)
                is ExpandResult.NextPopup -> {
                    val newItems = items.take(anchorIndex + 1) + result.expanded
                    items = newItems
                    openedByKeys = setOf(newItems.last().key)
                    popup = BreadcrumbPopupModel(newItems.lastIndex, result.children, initialSelectedIndex = 0)
                }
            }
        }
    }

    /**
     * Closes the popup on an outside click. The drilled-down segments stay a moment, for a click
     * landing on one of them to still reach it; then the bar goes back to the current path.
     */
    fun dismiss() {
        dismissedKeys = openedByKeys
        dismissedAt = clock()
        closePopup(returnFocus = false)
        launch {
            delay(RECLICK_WINDOW_MS)
            reset(returnFocus = false)
        }
    }

    /** Escape: closes the popup and restores the current path. */
    fun cancel() = reset(returnFocus = true)

    /** [openedBy]: the clicked segment and the anchor (its parent for a leaf), both closing it on a click. */
    private fun openPopup(
        openedBy: Set<String>,
        anchorIndex: Int,
        children: List<BreadcrumbNode>,
    ) {
        val next = items.getOrNull(anchorIndex + 1)
        openedByKeys = openedBy
        popup =
            BreadcrumbPopupModel(
                anchorIndex = anchorIndex,
                items = children,
                initialSelectedIndex = children.indexOfFirst { it.key == next?.key }.coerceAtLeast(0),
            )
    }

    private fun closePopup(returnFocus: Boolean) {
        job?.cancel()
        openedByKeys = emptySet()
        if (popup != null) {
            popup = null
            if (returnFocus) onReturnFocus()
        }
    }

    /** Headings without a line of their own lead nowhere: the bar stays as it is. */
    private fun navigate(node: BreadcrumbNode) {
        if (node is BreadcrumbNode.TocNode && node.entry.lineId == null) return
        reset(returnFocus = true)
        onNavigate(node)
    }

    private fun reset(returnFocus: Boolean) {
        closePopup(returnFocus)
        items = contextItems
    }

    private fun launch(block: suspend CoroutineScope.() -> Unit) {
        job?.cancel()
        job = scope.launch(block = block)
    }

    private companion object {
        // How long an outside click keeps the drilled-down bar, and the longest press-to-release of a
        // click on the segment whose popup the press just closed
        const val RECLICK_WINDOW_MS = 500L
    }
}

internal sealed interface ExpandResult {
    data class NavigateTo(
        val target: BreadcrumbNode,
    ) : ExpandResult

    data class NextPopup(
        val expanded: List<BreadcrumbNode>,
        val children: List<BreadcrumbNode>,
    ) : ExpandResult
}

/** Books open on click; folders (categories, TOC headings) drill down. */
private fun BreadcrumbNode.navigateOnClick(): Boolean = this is BreadcrumbNode.BookNode

/**
 * Same algorithm as IntelliJ's NavBar autoExpand: a chosen leaf (or a book) is navigated to;
 * otherwise single-child levels are skipped until one with several children is reached. Automatic
 * navigation never happens inside the loop, only as a direct reaction to the user's choice, so a
 * leaf or a book ending a single-child chain is offered alone in the next popup.
 */
internal suspend fun autoExpand(
    chosen: BreadcrumbNode,
    childrenProvider: BreadcrumbChildrenProvider,
): ExpandResult {
    if (chosen.navigateOnClick()) return ExpandResult.NavigateTo(chosen)
    var children = childrenProvider.children(chosen)
    if (children.isEmpty()) return ExpandResult.NavigateTo(chosen)

    var expanded = emptyList<BreadcrumbNode>()
    var current = chosen
    while (true) {
        when {
            current.navigateOnClick() || children.isEmpty() -> return ExpandResult.NextPopup(expanded, listOf(current))
            children.size == 1 -> {
                expanded = expanded + current
                current = children.single()
                // A book stops the chain: its TOC belongs to another book than the open one
                children = if (current.navigateOnClick()) emptyList() else childrenProvider.children(current)
            }
            else -> return ExpandResult.NextPopup(expanded + current, children)
        }
    }
}

/**
 * Children from the in-memory catalog (categories and books) and the open book's TOC, loaded
 * through [loadRootToc] and [loadTocChildren]. Books come before subcategories, as in the tree.
 */
class CatalogBreadcrumbChildrenProvider(
    private val categoryChildren: Map<Long, List<Category>>,
    private val booksByCategory: Map<Long, List<Book>>,
    private val loadRootToc: suspend (bookId: Long) -> List<TocEntry>,
    private val loadTocChildren: suspend (entryId: Long) -> List<TocEntry>,
) : BreadcrumbChildrenProvider {
    override suspend fun children(node: BreadcrumbNode): List<BreadcrumbNode> =
        when (node) {
            is BreadcrumbNode.CategoryNode -> {
                val id = node.category.id
                booksByCategory[id].orEmpty().map(BreadcrumbNode::BookNode) +
                    categoryChildren[id].orEmpty().map(BreadcrumbNode::CategoryNode)
            }
            is BreadcrumbNode.BookNode -> {
                val roots = loadRootToc(node.book.id)
                // A first root repeating the book title is replaced by its children, as the path drops
                // it; kept when it has none, as it is then a line of its own to go to
                val titleRoot = roots.firstOrNull()?.takeIf { it.text == node.book.title && it.hasChildren }
                val entries = if (titleRoot != null) loadTocChildren(titleRoot.id) + roots.drop(1) else roots
                entries.map(BreadcrumbNode::TocNode)
            }
            is BreadcrumbNode.TocNode ->
                if (node.entry.hasChildren) loadTocChildren(node.entry.id).map(BreadcrumbNode::TocNode) else emptyList()
        }

    companion object {
        /** Groups the catalog books by category, deduplicated, keeping their catalog order. */
        fun groupBooks(books: Set<Book>): Map<Long, List<Book>> = books.distinctBy { it.id }.groupBy { it.categoryId }
    }
}
