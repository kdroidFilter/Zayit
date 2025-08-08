package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.paging.LoadState
import app.cash.paging.PagingData
import app.cash.paging.compose.LazyPagingItems
import app.cash.paging.compose.collectAsLazyPagingItems
import app.cash.paging.compose.itemKey
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.core.utils.debugln
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.utils.HtmlParser
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.Font
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Text
import seforimapp.composeapp.generated.resources.Res
import seforimapp.composeapp.generated.resources.notoserifhebrew

@OptIn(FlowPreview::class)
@Composable
fun BookContentView(
    book: Book,
    linesPagingData: Flow<PagingData<Line>>,
    selectedLine: Line?,
    shouldScrollToLine: Boolean = false,
    tocEntries: List<TocEntry>,
    tocChildren: Map<Long, List<TocEntry>>,
    rootCategories: List<Category>,
    categoryChildren: Map<Long, List<Category>>,
    onLineSelected: (Line) -> Unit,
    onTocEntryClick: (TocEntry) -> Unit,
    onCategoryClick: (Category) -> Unit,
    onEvent: (BookContentEvent) -> Unit,
    modifier: Modifier = Modifier,
    preservedListState: LazyListState? = null,
    scrollIndex: Int = 0,
    scrollOffset: Int = 0,
    scrollToLineTimestamp: Long = 0,
    anchorId: Long = -1L,
    anchorIndex: Int = 0,
    onScroll: (Long, Int, Int, Int) -> Unit = { _, _, _, _ -> }
) {
    // Collect paging data
    val lazyPagingItems: LazyPagingItems<Line> = linesPagingData.collectAsLazyPagingItems()

    // Don't use the saved scroll position initially if we have an anchor
    // The restoration will be handled after pagination loads
    val listState = preservedListState ?: rememberLazyListState(
        initialFirstVisibleItemIndex = if (anchorId != -1L) 0 else scrollIndex,
        initialFirstVisibleItemScrollOffset = if (anchorId != -1L) 0 else scrollOffset
    )

    // Collect text size from settings
    val rawTextSize by AppSettings.textSizeFlow.collectAsState()

    // Animate text size changes for smoother transitions
    val textSize by animateFloatAsState(
        targetValue = rawTextSize,
        animationSpec = tween(durationMillis = 300),
        label = "textSizeAnimation"
    )

    // Collect line height from settings
    val rawLineHeight by AppSettings.lineHeightFlow.collectAsState()

    // Animate line height changes for smoother transitions
    val lineHeight by animateFloatAsState(
        targetValue = rawLineHeight,
        animationSpec = tween(durationMillis = 300),
        label = "lineHeightAnimation"
    )

    // Track restoration state per book
    var hasRestored by remember(book.id) { mutableStateOf(false) }

    // Track the restored anchor to avoid re-restoration
    var restoredAnchorId by remember(book.id) { mutableStateOf(-1L) }

    // Handle scrolling to selected line when timestamp changes
    LaunchedEffect(scrollToLineTimestamp) {
        val target = selectedLine ?: return@LaunchedEffect
        if (scrollToLineTimestamp == 0L) return@LaunchedEffect

        // Wait for initial loading to complete
        while (lazyPagingItems.loadState.refresh is LoadState.Loading) {
            delay(16)
        }

        // Find and scroll to the selected line
        val index = lazyPagingItems.itemSnapshotList.indexOfFirst { it?.id == target.id }
        if (index >= 0) {
            listState.scrollToItem(index)
        }
    }

    // Improved restoration algorithm for pagination
    LaunchedEffect(lazyPagingItems.loadState, anchorId) {
        // Only restore if we haven't already and we have items and an anchor
        if (!hasRestored && anchorId != -1L && anchorId != restoredAnchorId) {

            // Wait for the initial page load to complete
            if (lazyPagingItems.loadState.refresh is LoadState.Loading) {
                // Wait for loading to finish
                while (lazyPagingItems.loadState.refresh is LoadState.Loading) {
                    delay(16)
                }
            }

            // Now check if we have items
            if (lazyPagingItems.itemCount > 0) {
                // Find the anchor line in the current page
                val anchorLineIndex = lazyPagingItems.itemSnapshotList
                    .indexOfFirst { it?.id == anchorId }

                if (anchorLineIndex >= 0) {
                    debugln { "Found anchor at index $anchorLineIndex, scrolling with offset $scrollOffset" }

                    // Scroll directly to the anchor with the saved offset
                    listState.scrollToItem(anchorLineIndex, scrollOffset)
                    hasRestored = true
                    restoredAnchorId = anchorId
                } else {
                    debugln { "Anchor line $anchorId not found in current page" }

                    // The anchor is not in the current page
                    // This shouldn't happen if the ViewModel correctly sets initialLineId
                    // As a fallback, check if we have a selected line
                    if (selectedLine != null) {
                        val selectedIndex = lazyPagingItems.itemSnapshotList
                            .indexOfFirst { it?.id == selectedLine.id }
                        if (selectedIndex >= 0) {
                            listState.scrollToItem(selectedIndex)
                            hasRestored = true
                        }
                    }
                }
            }
        }
    }

    // Save scroll position with anchor information
    LaunchedEffect(listState, hasRestored) {
        if (hasRestored || lazyPagingItems.itemCount > 0) {
            snapshotFlow {
                val firstVisibleIndex = listState.firstVisibleItemIndex
                val safeIndex = firstVisibleIndex.coerceAtMost(lazyPagingItems.itemCount - 1)

                // Get the ID of the first visible line as the anchor
                val currentAnchorId = if (safeIndex >= 0 && safeIndex < lazyPagingItems.itemCount) {
                    lazyPagingItems[safeIndex]?.id ?: -1L
                } else {
                    -1L
                }

                val scrollOff = listState.firstVisibleItemScrollOffset

                // Return anchor info and scroll position
                // The anchorIndex is the same as scrollIndex in this context
                AnchorData(
                    anchorId = currentAnchorId,
                    anchorIndex = safeIndex,
                    scrollIndex = firstVisibleIndex,
                    scrollOffset = scrollOff
                )
            }
                .distinctUntilChanged()
                .debounce(250)
                .collect { data ->
                    debugln { "Saving scroll: anchor=${data.anchorId}, index=${data.scrollIndex}, offset=${data.scrollOffset}" }
                    onScroll(data.anchorId, data.anchorIndex, data.scrollIndex, data.scrollOffset)
                }
        }
    }

    SelectionContainer(
        modifier = modifier
            .fillMaxSize()
            .onKeyEvent { keyEvent ->
                debugln { "[BookContentView] Key event: key=${keyEvent.key}, type=${keyEvent.type}" }

                if (keyEvent.type != KeyEventType.KeyDown) {
                    return@onKeyEvent false
                }

                when (keyEvent.key) {
                    Key.DirectionUp -> {
                        debugln { "[BookContentView] Up arrow key pressed, navigating to previous line" }
                        onEvent(BookContentEvent.NavigateToPreviousLine)
                        true
                    }
                    Key.DirectionDown -> {
                        debugln { "[BookContentView] Down arrow key pressed, navigating to next line" }
                        onEvent(BookContentEvent.NavigateToNextLine)
                        true
                    }
                    else -> false
                }
            }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                count = lazyPagingItems.itemCount,
                key = lazyPagingItems.itemKey { it.id }
            ) { index ->
                val line = lazyPagingItems[index]

                if (line != null) {
                    LineItem(
                        line = line,
                        isSelected = selectedLine?.id == line.id,
                        baseTextSize = textSize,
                        lineHeight = lineHeight
                    ) {
                        onLineSelected(line)
                    }
                } else {
                    // Placeholder while loading
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .padding(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Show loading indicators
            lazyPagingItems.apply {
                when {
                    loadState.refresh is LoadState.Loading -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    loadState.append is LoadState.Loading -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                    loadState.refresh is LoadState.Error -> {
                        val error = (loadState.refresh as LoadState.Error).error
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) {
                                Text(
                                    text = "Error: ${error.message}",
                                    color = Color.Red
                                )
                            }
                        }
                    }
                    loadState.append is LoadState.Error -> {
                        val error = (loadState.append as LoadState.Error).error
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) {
                                Text(
                                    text = "Error loading more: ${error.message}",
                                    color = Color.Red
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Data class for anchor information
private data class AnchorData(
    val anchorId: Long,
    val anchorIndex: Int,
    val scrollIndex: Int,
    val scrollOffset: Int
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LineItem(
    line: Line,
    isSelected: Boolean,
    baseTextSize: Float = 16f,
    lineHeight: Float = 1.5f,
    onClick: () -> Unit
) {
    val parsedElements = remember(line.id, line.content) {
        HtmlParser().parse(line.content)
    }

    val annotated = remember(parsedElements, baseTextSize) {
        buildAnnotatedString {
            parsedElements.forEach { e ->
                if (e.text.isBlank()) return@forEach

                val start = length
                append(e.text)
                val end = length

                if (e.isBold) {
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                }
                if (e.isItalic) {
                    addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                }
                if (e.isHeader || e.headerLevel != null) {
                    val size = when (e.headerLevel) {
                        1 -> (baseTextSize * 1.5f).sp
                        2 -> (baseTextSize * 1.25f).sp
                        3 -> (baseTextSize * 1.125f).sp
                        4 -> baseTextSize.sp
                        else -> baseTextSize.sp
                    }
                    addStyle(SpanStyle(fontSize = size), start, end)
                } else {
                    addStyle(SpanStyle(fontSize = baseTextSize.sp), start, end)
                }
            }
        }
    }

    val textModifier = Modifier
        .fillMaxWidth()
        .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }

    Box(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(if (isSelected) JewelTheme.globalColors.borders.disabled else Color.Transparent)
                    .zIndex(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = annotated,
                textAlign = TextAlign.Justify,
                fontFamily = FontFamily(
                    Font(
                        resource = Res.font.notoserifhebrew,
                        weight = FontWeight.Normal
                    )
                ),
                lineHeight = (baseTextSize * lineHeight).sp,
                modifier = textModifier
            )
        }
    }
}