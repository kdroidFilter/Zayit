package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.categoryAncestry
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * The path of the open book (categories, book, TOC headings), working as IntelliJ's navigation bar:
 * a click on a segment opens a popup of its children to browse the library, a double click
 * navigates to the segment itself.
 */
@Composable
fun BreadcrumbView(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val book = uiState.navigation.selectedBook ?: return
    val navigation = uiState.navigation
    val toc = uiState.toc

    val path =
        remember(book, toc.breadcrumbPath, navigation.categoriesById) {
            buildBreadcrumbPath(book, toc.breadcrumbPath, navigation.categoriesById)
        }

    // Grouped once per catalog, not on every TOC change
    val booksByCategory = remember(navigation.booksInCategory) { CatalogBreadcrumbChildrenProvider.groupBooks(navigation.booksInCategory) }
    val loadRootToc = uiState.providers?.loadRootToc ?: NoTocEntries
    val loadTocChildren = uiState.providers?.loadTocChildren ?: NoTocEntries
    val childrenProvider =
        remember(navigation.categoryChildren, booksByCategory, loadRootToc, loadTocChildren) {
            CatalogBreadcrumbChildrenProvider(navigation.categoryChildren, booksByCategory, loadRootToc, loadTocChildren)
        }
    val currentChildrenProvider by rememberUpdatedState(childrenProvider)
    val currentOnEvent by rememberUpdatedState(onEvent)
    val currentBookId by rememberUpdatedState(book.id)

    val scope = rememberCoroutineScope()
    val navigator =
        remember(scope) {
            BreadcrumbNavigator(
                scope = scope,
                childrenProvider = { currentChildrenProvider.children(it) },
                initialPath = path,
                // The popup took the keyboard focus: the text gets it back
                onReturnFocus = { currentOnEvent(BookContentEvent.FocusText) },
                onNavigate = { node ->
                    when (node) {
                        is BreadcrumbNode.CategoryNode -> currentOnEvent(BookContentEvent.RevealCategory(node.category))
                        is BreadcrumbNode.BookNode ->
                            if (node.book.id != currentBookId) currentOnEvent(BookContentEvent.BookSelected(node.book))
                        is BreadcrumbNode.TocNode ->
                            node.entry.lineId?.let { currentOnEvent(BookContentEvent.LoadAndSelectLine(it)) }
                    }
                },
            )
        }
    // Later paths (the initial one is passed at creation, to show it from the first frame)
    LaunchedEffect(navigator, path) { navigator.updatePath(path) }

    BreadcrumbBar(navigator = navigator, modifier = modifier)
}

@Composable
private fun BreadcrumbBar(
    navigator: BreadcrumbNavigator,
    modifier: Modifier = Modifier,
) {
    val items = navigator.items
    val selectedIndex = navigator.selectedIndex
    val popup = navigator.popup

    val scrollState = rememberScrollState()
    LaunchedEffect(items) { scrollState.scrollTo(Int.MAX_VALUE) }

    Row(
        modifier = modifier.horizontalScroll(scrollState),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        items.forEachIndexed { index, node ->
            if (index > 0) {
                Text(text = " > ", modifier = Modifier.padding(horizontal = 4.dp), fontSize = 12.sp)
            }
            key(node.key) {
                Box {
                    BreadcrumbSegment(
                        title = node.title,
                        isLast = index == items.lastIndex,
                        isSelected = index == selectedIndex,
                        // As in IntelliJ, segments past the selected one are dimmed
                        isInactive = selectedIndex != -1 && index > selectedIndex,
                        onClick = { navigator.onSegmentClick(node.key) },
                        onDoubleClick = { navigator.onSegmentDoubleClick(node.key) },
                    )
                    if (popup != null && popup.anchorIndex == index) {
                        BreadcrumbPopup(
                            model = popup,
                            onChoose = navigator::choose,
                            onShift = navigator::shiftPopup,
                            onDismiss = navigator::dismiss,
                            onCancel = navigator::cancel,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BreadcrumbSegment(
    title: String,
    isLast: Boolean,
    isSelected: Boolean,
    isInactive: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val background = highlightBackground(interactionSource, isSelected)
    // Double click detected by hand: combinedClickable would delay the single click by the double-tap timeout
    val doubleClickTimeout = LocalViewConfiguration.current.doubleTapTimeoutMillis
    var lastClickAt by remember { mutableLongStateOf(0L) }

    Text(
        text = title,
        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
        fontSize = 12.sp,
        color = if (isInactive) JewelTheme.globalColors.text.disabled else Color.Unspecified,
        modifier =
            Modifier
                .pointerHoverIcon(PointerIcon.Hand)
                .highlightClickable(interactionSource, background, SegmentShape) {
                    // Second click within the timeout: a double click, as IntelliJ's clickCount == 2
                    val now = System.currentTimeMillis()
                    if (now - lastClickAt <= doubleClickTimeout) {
                        lastClickAt = 0L
                        onDoubleClick()
                    } else {
                        lastClickAt = now
                        onClick()
                    }
                }.padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

private val SegmentShape = RoundedCornerShape(4.dp)

private val NoTocEntries: suspend (Long) -> List<TocEntry> = { emptyList() }

/** Categories from the root, then the book, then its TOC headings down to the selected line. */
private fun buildBreadcrumbPath(
    book: Book,
    tocPath: List<TocEntry>,
    categoriesById: Map<Long, Category>,
): List<BreadcrumbNode> =
    buildList {
        addAll(categoryAncestry(book.categoryId, categoriesById).map(BreadcrumbNode::CategoryNode))
        add(BreadcrumbNode.BookNode(book))
        // A first TOC heading repeating the book title is dropped to avoid duplicating the book item
        val adjustedToc = if (tocPath.firstOrNull()?.text == book.title) tocPath.drop(1) else tocPath
        addAll(adjustedToc.map(BreadcrumbNode::TocNode))
    }
