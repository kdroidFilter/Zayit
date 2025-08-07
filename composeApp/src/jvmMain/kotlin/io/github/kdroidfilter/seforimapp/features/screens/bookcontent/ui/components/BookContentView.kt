package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.animateScrollBy
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
    linesPagingData: Flow<PagingData<Line>>, // NEW: Use paging data flow
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
    onScroll: (Long, Int, Int, Int) -> Unit = { _, _, _, _ -> }
) {
    // Collect paging data
    val lazyPagingItems: LazyPagingItems<Line> = linesPagingData.collectAsLazyPagingItems()

    val listState = preservedListState ?: rememberLazyListState(
        initialFirstVisibleItemIndex = scrollIndex,
        initialFirstVisibleItemScrollOffset = scrollOffset
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

    var hasRestored by remember(book.id) { mutableStateOf(false) }

    LaunchedEffect(scrollToLineTimestamp) {
        val target = selectedLine ?: return@LaunchedEffect
        if (scrollToLineTimestamp == 0L) return@LaunchedEffect

        // 1. attendre la fin du premier chargement Paging
        while (lazyPagingItems.loadState.refresh is LoadState.Loading) {
            delay(16)          // ≈ 1 frame
        }

        // 2. Chercher l’index de la ligne voulue dans le snapshot courant
        val index = lazyPagingItems.itemSnapshotList.indexOfFirst { it?.id == target.id }
        if (index >= 0) {
            listState.scrollToItem(index)
        }
    }

    // Pixel-perfect scroll restoration algorithm
    LaunchedEffect(lazyPagingItems.itemCount, selectedLine?.id) {
        if (lazyPagingItems.itemCount > 0 && !hasRestored) {
            // 1. Wait for the refresh to complete (the page is loaded)
            while (lazyPagingItems.loadState.refresh is LoadState.Loading) delay(16)
            
            // Try to find and scroll to selected line first (priority)
            if (selectedLine != null) {
                val index = (0 until lazyPagingItems.itemCount).firstOrNull { idx ->
                    lazyPagingItems[idx]?.id == selectedLine.id
                }

                if (index != null && index >= 0) {
                    listState.scrollToItem(index)
                    hasRestored = true
                    return@LaunchedEffect
                }
            }
            
            // 2. Find the current index of the anchor line
            val anchorId = book.id // Use book ID as fallback
            val newAnchorIndex = lazyPagingItems.itemSnapshotList.indexOfFirst { it?.id == anchorId }
            
            if (newAnchorIndex >= 0) {
                // 3. Calculate the offset between the anchor index and the scroll index
                val delta = scrollIndex - 0 // Use 0 as default anchorIndex if not available
                val targetIndex = (newAnchorIndex + delta).coerceAtLeast(0)
                
                // 4. Scroll to the target index with the saved offset
                listState.scrollToItem(targetIndex, scrollOffset)
            } else {
                // Fallback: If anchor line not found, use the saved scroll position directly
                val safeIndex = scrollIndex.coerceIn(0, lazyPagingItems.itemCount - 1)
                if (safeIndex >= 0) {
                    listState.scrollToItem(safeIndex, scrollOffset)
                }
            }
            
            hasRestored = true
        }
    }


    // Save scroll position with anchor information for pixel-perfect restoration
    LaunchedEffect(listState, hasRestored) {
        if (hasRestored) {
            snapshotFlow {
                val anchorIdx = listState.firstVisibleItemIndex
                val safeIdx = anchorIdx.coerceAtMost(lazyPagingItems.itemCount - 1)
                val anchorId = if (safeIdx >= 0) lazyPagingItems[safeIdx]?.id ?: -1L else -1L
                val scrollIdx = anchorIdx
                val scrollOff = listState.firstVisibleItemScrollOffset
                
                // Return the four values needed for pixel-perfect restoration
                // Quadruple(anchorId, safeIdx, anchorIdx, scrollOff)
                anchorId to (safeIdx to (scrollIdx to scrollOff))
            }
                .distinctUntilChanged()
                .debounce(250)
                .collect { (anchorId, rest) ->
                    val (anchorIdx, scrollData) = rest
                    val (scrollIdx, scrollOff) = scrollData
                    onScroll(anchorId, anchorIdx, scrollIdx, scrollOff)
                }
        }
    }

    SelectionContainer(
        modifier = modifier
            .fillMaxSize()
            .onKeyEvent { keyEvent ->
                debugln { "[BookContentView] Key event: key=${keyEvent.key}, type=${keyEvent.type}" }
                
                // Only process key down events to prevent multiple events for a single key press
                // This fixes the issue where navigation was skipping 2 lines instead of moving by 1 line
                // The issue was that both KeyDown and KeyUp events were being processed for a single key press
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

            // Show loading indicator at the end
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

    // Builds a single annotated string => a single Text, no more "line after"
    // Include baseTextSize in remember dependencies to ensure recomposition when text size changes
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
                    // Apply base text size to non-header text
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