package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.panels.bookcontent.views

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.logger.debugln
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.Font
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.notoserifhebrew

@OptIn(FlowPreview::class)
@Composable
fun BookContentView(
    book: Book,
    linesPagingData: Flow<PagingData<Line>>,
    selectedLine: Line?,
    onLineSelected: (Line) -> Unit,
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

    // Optimize selected line ID lookup
    val selectedLineId = remember(selectedLine) { selectedLine?.id }

    // Handle scrolling to selected line when timestamp changes
    LaunchedEffect(scrollToLineTimestamp, selectedLineId) {
        if (scrollToLineTimestamp == 0L || selectedLineId == null) return@LaunchedEffect

        // Wait for initial loading to complete
        while (lazyPagingItems.loadState.refresh is LoadState.Loading) {
            delay(16)
        }

        // Find and scroll to the selected line using binary search if possible
        val snapshot = lazyPagingItems.itemSnapshotList
        val index = snapshot.indices.firstOrNull { snapshot[it]?.id == selectedLineId }

        if (index != null) {
            listState.scrollToItem(index)
        }
    }

    // Improved restoration algorithm for pagination
    LaunchedEffect(lazyPagingItems.loadState.refresh, anchorId) {
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
                val snapshot = lazyPagingItems.itemSnapshotList
                val anchorLineIndex = snapshot.indices.firstOrNull { snapshot[it]?.id == anchorId }

                if (anchorLineIndex != null) {
                    debugln { "Found anchor at index $anchorLineIndex, scrolling with offset $scrollOffset" }

                    // Scroll directly to the anchor with the saved offset
                    listState.scrollToItem(anchorLineIndex, scrollOffset)
                    hasRestored = true
                    restoredAnchorId = anchorId
                } else {
                    debugln { "Anchor line $anchorId not found in current page" }

                    // Fallback strategy when anchor is not in current page:
                    // 1) Try selected line if present in snapshot
                    // 2) Otherwise, fall back to saved anchorIndex/scrollIndex within bounds
                    val selectedIndex = if (selectedLineId != null) {
                        snapshot.indices.firstOrNull { snapshot[it]?.id == selectedLineId }
                    } else null

                    if (selectedIndex != null) {
                        debugln { "Fallback to selected line at index $selectedIndex" }
                        listState.scrollToItem(selectedIndex)
                        hasRestored = true
                    } else {
                        val itemCount = lazyPagingItems.itemCount
                        if (itemCount > 0) {
                            // Prefer anchorIndex if it’s in range; otherwise use scrollIndex; clamp to [0, itemCount-1]
                            val indexByAnchor = anchorIndex.takeIf { it in 0 until itemCount }
                            val indexByScroll = scrollIndex.takeIf { it in 0 until itemCount }
                            val targetIndex = (indexByAnchor ?: indexByScroll ?: (itemCount - 1).coerceAtLeast(0))
                            val targetOffset = if (targetIndex == scrollIndex) scrollOffset.coerceAtLeast(0) else 0
                            debugln { "Fallback to index-based restore: index=$targetIndex, offset=$targetOffset (anchor missing)" }
                            listState.scrollToItem(targetIndex, targetOffset)
                            hasRestored = true
                        }
                    }
                }
            }
        }
    }

    // Fallback restoration when no anchor is available: use saved scrollIndex/scrollOffset
    LaunchedEffect(lazyPagingItems.loadState.refresh, scrollIndex, scrollOffset, anchorId) {
        // Only attempt if we haven't restored yet, there is no anchor, and we actually have a non-zero saved position
        if (!hasRestored && anchorId == -1L && (scrollIndex > 0 || scrollOffset > 0)) {
            // Even if a preservedListState is provided, actively restore the position when we have saved scroll values

            // Wait for the initial page load to complete
            if (lazyPagingItems.loadState.refresh is LoadState.Loading) {
                while (lazyPagingItems.loadState.refresh is LoadState.Loading) {
                    delay(16)
                }
            }

            // Now, if we have items, try to scroll to the saved index/offset
            if (lazyPagingItems.itemCount > 0) {
                val itemCount = lazyPagingItems.itemCount
                val targetIndex = scrollIndex.coerceIn(0, maxOf(0, itemCount - 1))
                val targetOffset = scrollOffset.coerceAtLeast(0)

                if (targetIndex != 0 || targetOffset != 0) {
                    debugln { "Restoring scroll by index/offset: index=$targetIndex, offset=$targetOffset (no anchor)" }
                    listState.scrollToItem(targetIndex, targetOffset)
                    hasRestored = true
                }
            }
        }
    }

    // Save scroll position with anchor information - optimized with derivedStateOf
    val scrollData = remember(listState, lazyPagingItems) {
        derivedStateOf {
            val firstVisibleIndex = listState.firstVisibleItemIndex
            val itemCount = lazyPagingItems.itemCount
            val safeIndex = firstVisibleIndex.coerceAtMost(itemCount - 1)

            // Get the ID of the first visible line as the anchor
            val currentAnchorId = if (safeIndex in 0 until itemCount) {
                lazyPagingItems[safeIndex]?.id ?: -1L
            } else {
                -1L
            }

            val scrollOff = listState.firstVisibleItemScrollOffset

            AnchorData(
                anchorId = currentAnchorId,
                anchorIndex = safeIndex,
                scrollIndex = firstVisibleIndex,
                scrollOffset = scrollOff
            )
        }
    }

    LaunchedEffect(scrollData, hasRestored, scrollIndex, scrollOffset) {
        // Guard: avoid overwriting a previously saved non-zero position with (0,0) before restoration
        // Start collecting always, but filter out the initial top emission if we still need to restore
        snapshotFlow { scrollData.value }
            .distinctUntilChanged()
            .debounce(250)
            .collect { data ->
                if (!hasRestored) {
                    val hasSavedNonZero = (scrollIndex > 0 || scrollOffset > 0)
                    val isCurrentZero = (data.scrollIndex == 0 && data.scrollOffset == 0)
                    if (hasSavedNonZero && isCurrentZero) {
                        debugln { "Skipping early (0,0) scroll save to preserve saved position: saved=($scrollIndex,$scrollOffset)" }
                        return@collect
                    }
                }
                debugln { "Saving scroll: anchor=${data.anchorId}, index=${data.scrollIndex}, offset=${data.scrollOffset}" }
                onScroll(data.anchorId, data.anchorIndex, data.scrollIndex, data.scrollOffset)
            }
    }

    // Memoize key event handler to avoid recreation
    val keyEventHandler = remember(onEvent) {
        { keyEvent: androidx.compose.ui.input.key.KeyEvent ->
            debugln { "[BookContentView] Key event: key=${keyEvent.key}, type=${keyEvent.type}" }

            if (keyEvent.type != KeyEventType.KeyDown) {
                false
            } else {
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
        }
    }

    SelectionContainer(
        modifier = modifier
            .fillMaxSize()
            .onKeyEvent(keyEventHandler)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                count = lazyPagingItems.itemCount,
                key = lazyPagingItems.itemKey { it.id },
                contentType = { "line" }  // Optimization: specify content type
            ) { index ->
                val line = lazyPagingItems[index]

                if (line != null) {
                    LineItem(
                        line = line,
                        isSelected = selectedLineId == line.id,
                        baseTextSize = textSize,
                        lineHeight = lineHeight,
                        onLineSelected = onLineSelected
                    )
                } else {
                    // Placeholder while loading
                    LoadingPlaceholder()
                }
            }

            // Show loading indicators
            lazyPagingItems.apply {
                when {
                    loadState.refresh is LoadState.Loading -> {
                        item(contentType = "loading") {
                            LoadingIndicator()
                        }
                    }
                    loadState.append is LoadState.Loading -> {
                        item(contentType = "loading") {
                            LoadingIndicator(isSmall = true)
                        }
                    }
                    loadState.refresh is LoadState.Error -> {
                        val error = (loadState.refresh as LoadState.Error).error
                        item(contentType = "error") {
                            ErrorIndicator(message = "Error: ${error.message}")
                        }
                    }
                    loadState.append is LoadState.Error -> {
                        val error = (loadState.append as LoadState.Error).error
                        item(contentType = "error") {
                            ErrorIndicator(message = "Error loading more: ${error.message}")
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
    onLineSelected: (Line) -> Unit
) {
    // Memoize the annotated string with proper keys
    val annotated = remember(line.id, line.content, baseTextSize) {
        buildAnnotatedFromHtml(line.content, baseTextSize)
    }

    // Memoize click handler to avoid recreation
    val clickHandler = remember(line, onLineSelected) {
        { onLineSelected(line) }
    }

    // Get theme color in composable context
    val borderColor = if (isSelected) JewelTheme.globalColors.borders.disabled else Color.Transparent

    val textModifier = remember {
        Modifier.fillMaxWidth()
    }.pointerInput(line) {
        detectTapGestures(onTap = { clickHandler() })
    }

    val hebrewFontFamily =
        FontFamily(
            Font(
                resource = Res.font.notoserifhebrew,
                weight = FontWeight.Normal
            )
        )


    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(borderColor)
                    .zIndex(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = annotated,
                textAlign = TextAlign.Justify,
                fontFamily = hebrewFontFamily,
                lineHeight = (baseTextSize * lineHeight).sp,
                modifier = textModifier
            )
        }
    }
}

// Extract reusable components to avoid inline composition
@Composable
private fun LoadingPlaceholder() {
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

@Composable
private fun LoadingIndicator(isSmall: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = if (isSmall) Modifier.size(24.dp) else Modifier
        )
    }
}

@Composable
private fun ErrorIndicator(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = Color.Red
        )
    }
}
