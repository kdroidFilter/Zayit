package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import app.cash.paging.PagingData
import app.cash.paging.compose.LazyPagingItems
import app.cash.paging.compose.collectAsLazyPagingItems
import io.github.kdroidfilter.seforimapp.core.presentation.components.WarningBanner
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.rememberSplitPaneState
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Text
import seforimapp.composeapp.generated.resources.Res
import seforimapp.composeapp.generated.resources.commentaries
import seforimapp.composeapp.generated.resources.max_commentators_limit
import seforimapp.composeapp.generated.resources.notorashihebrew
import seforimapp.composeapp.generated.resources.no_commentaries_for_line
import seforimapp.composeapp.generated.resources.select_at_least_one_commentator
import seforimapp.composeapp.generated.resources.select_line_for_commentaries

@OptIn(ExperimentalSplitPaneApi::class)
@Composable
fun LineCommentsView(
    selectedLine: Line?,
    buildCommentariesPagerFor: (Long, Long?) -> Flow<PagingData<CommentaryWithText>>,
    getAvailableCommentatorsForLine: suspend (Long) -> Map<String, Long>,
    commentariesScrollIndex: Int = 0,
    commentariesScrollOffset: Int = 0,
    initiallySelectedCommentatorIds: Set<Long> = emptySet(),
    onSelectedCommentatorsChange: (Set<Long>) -> Unit = {},
    onCommentClick: (CommentaryWithText) -> Unit = {},
    onScroll: (Int, Int) -> Unit = { _, _ -> }
) {

    // Animated settings for consistency with old UI
    val rawTextSize by AppSettings.textSizeFlow.collectAsState()
    val commentTextSize by animateFloatAsState(
        targetValue = rawTextSize * 0.875f,
        animationSpec = tween(durationMillis = 300),
        label = "commentTextSizeAnim"
    )
    val rawLineHeight by AppSettings.lineHeightFlow.collectAsState()
    val lineHeight by animateFloatAsState(
        targetValue = rawLineHeight,
        animationSpec = tween(durationMillis = 300),
        label = "commentLineHeightAnim"
    )

    var showMaxCommentatorsWarning by remember { mutableStateOf(false) }
    LaunchedEffect(showMaxCommentatorsWarning) {
        if (showMaxCommentatorsWarning) {
            delay(5000)
            showMaxCommentatorsWarning = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Header row with title and optional warning
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(Res.string.commentaries), fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(end = 16.dp))
            if (showMaxCommentatorsWarning) {
                WarningBanner(
                    message = stringResource(Res.string.max_commentators_limit),
                    onClose = { showMaxCommentatorsWarning = false },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        when (selectedLine) {
            null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = stringResource(Res.string.select_line_for_commentaries))
                }
            }
            else -> {
                // Ask ViewModel for available commentators for the selected line (title -> bookId)
                var titleToIdMap by remember(selectedLine.id) { mutableStateOf<Map<String, Long>>(emptyMap()) }
                LaunchedEffect(selectedLine.id) {
                    runCatching { getAvailableCommentatorsForLine(selectedLine.id) }
                        .onSuccess { map -> titleToIdMap = map }
                        .onFailure { titleToIdMap = emptyMap() }
                }

                if (titleToIdMap.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = stringResource(Res.string.no_commentaries_for_line))
                    }
                } else {
                    val availableCommentators = remember(titleToIdMap) {
                        titleToIdMap.keys.sorted().toList()
                    }

                    var selectedCommentators by remember(availableCommentators) { mutableStateOf<Set<String>>(emptySet()) }
                    // Initialize selection from ViewModel-provided IDs when available
                    LaunchedEffect(initiallySelectedCommentatorIds, titleToIdMap) {
                        if (initiallySelectedCommentatorIds.isNotEmpty() && titleToIdMap.isNotEmpty()) {
                            val desiredNames = titleToIdMap
                                .filterValues { it in initiallySelectedCommentatorIds }
                                .keys
                                .toSet()
                            if (desiredNames != selectedCommentators) {
                                selectedCommentators = desiredNames
                            }
                        }
                    }
                    // Emit selection changes upward as IDs
                    LaunchedEffect(selectedCommentators, titleToIdMap) {
                        val ids = selectedCommentators.mapNotNull { titleToIdMap[it] }.toSet()
                        onSelectedCommentatorsChange(ids)
                    }
                    // Ensure selection remains within available only when list changes
                    LaunchedEffect(availableCommentators) {
                        val filtered = selectedCommentators.filter { it in availableCommentators.toSet() }.toSet()
                        if (filtered != selectedCommentators) {
                            selectedCommentators = filtered
                        }
                    }

                    val splitState = rememberSplitPaneState(0.10f)

                    EnhancedHorizontalSplitPane(
                        splitPaneState = splitState,
                        firstMinSize = 150f,
                        firstContent = {
                            PagedCommentatorsListView(
                                commentators = availableCommentators,
                                selectedCommentators = selectedCommentators,
                                onCommentatorSelected = { name, checked ->
                                    if (checked) {
                                        if (selectedCommentators.size < 4) {
                                            selectedCommentators = selectedCommentators + name
                                        } else {
                                            showMaxCommentatorsWarning = true
                                        }
                                    } else {
                                        selectedCommentators = selectedCommentators - name
                                    }
                                },
                                onShowWarning = { showMaxCommentatorsWarning = it }
                            )
                        },
                        secondContent = {
                            if (selectedCommentators.isEmpty()) {
                                CenteredMessage(message = stringResource(Res.string.select_at_least_one_commentator), fontSize = commentTextSize)
                            } else {
                                // title -> id map provided by ViewModel
                                val titleToId: Map<String, Long> = titleToIdMap

                                val selectedList = selectedCommentators.toList()

                                when (selectedList.size) {
                                    1 -> {
                                        val name = selectedList[0]
                                        val id = titleToId[name]
                                        if (id != null) {
                                            Column(modifier = Modifier.fillMaxSize()) {
                                                CommentatorHeader(commentator = name, commentTextSize = commentTextSize)
                                                PagedCommentariesList(
                                                    buildCommentariesPagerFor = buildCommentariesPagerFor,
                                                    lineId = selectedLine.id,
                                                    commentatorId = id,
                                                    isPrimary = true,
                                                    initialIndex = commentariesScrollIndex,
                                                    initialOffset = commentariesScrollOffset,
                                                    onScroll = onScroll,
                                                    onCommentClick = onCommentClick,
                                                    commentTextSize = commentTextSize,
                                                    lineHeight = lineHeight
                                                )
                                            }
                                        }
                                    }
                                    2 -> {
                                        PagedCommentatorsRow(
                                            names = selectedList,
                                            titleToId = titleToId,
                                            lineId = selectedLine.id,
                                            buildCommentariesPagerFor = buildCommentariesPagerFor,
                                            primaryIndex = 0,
                                            commentariesScrollIndex = commentariesScrollIndex,
                                            commentariesScrollOffset = commentariesScrollOffset,
                                            onScroll = onScroll,
                                            onCommentClick = onCommentClick,
                                            commentTextSize = commentTextSize,
                                            lineHeight = lineHeight,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    3 -> {
                                        val firstRow = selectedList.take(2)
                                        val secondRow = listOf(selectedList[2])
                                        Column(modifier = Modifier.fillMaxSize()) {
                                            PagedCommentatorsRow(
                                                names = firstRow,
                                                titleToId = titleToId,
                                                lineId = selectedLine.id,
                                                buildCommentariesPagerFor = buildCommentariesPagerFor,
                                                primaryIndex = 0,
                                                commentariesScrollIndex = commentariesScrollIndex,
                                                commentariesScrollOffset = commentariesScrollOffset,
                                                onScroll = onScroll,
                                                onCommentClick = onCommentClick,
                                                commentTextSize = commentTextSize,
                                                lineHeight = lineHeight,
                                                modifier = Modifier.weight(1f).fillMaxWidth()
                                            )
                                            PagedCommentatorsRow(
                                                names = secondRow,
                                                titleToId = titleToId,
                                                lineId = selectedLine.id,
                                                buildCommentariesPagerFor = buildCommentariesPagerFor,
                                                primaryIndex = -1,
                                                commentariesScrollIndex = commentariesScrollIndex,
                                                commentariesScrollOffset = commentariesScrollOffset,
                                                onScroll = onScroll,
                                                onCommentClick = onCommentClick,
                                                commentTextSize = commentTextSize,
                                                lineHeight = lineHeight,
                                                modifier = Modifier.weight(1f).fillMaxWidth()
                                            )
                                        }
                                    }
                                    4 -> {
                                        val firstRow = selectedList.take(2)
                                        val secondRow = selectedList.drop(2).take(2)
                                        Column(modifier = Modifier.fillMaxSize()) {
                                            PagedCommentatorsRow(
                                                names = firstRow,
                                                titleToId = titleToId,
                                                lineId = selectedLine.id,
                                                buildCommentariesPagerFor = buildCommentariesPagerFor,
                                                primaryIndex = 0,
                                                commentariesScrollIndex = commentariesScrollIndex,
                                                commentariesScrollOffset = commentariesScrollOffset,
                                                onScroll = onScroll,
                                                onCommentClick = onCommentClick,
                                                commentTextSize = commentTextSize,
                                                lineHeight = lineHeight,
                                                modifier = Modifier.weight(1f).fillMaxWidth()
                                            )
                                            PagedCommentatorsRow(
                                                names = secondRow,
                                                titleToId = titleToId,
                                                lineId = selectedLine.id,
                                                buildCommentariesPagerFor = buildCommentariesPagerFor,
                                                primaryIndex = -1,
                                                commentariesScrollIndex = commentariesScrollIndex,
                                                commentariesScrollOffset = commentariesScrollOffset,
                                                onScroll = onScroll,
                                                onCommentClick = onCommentClick,
                                                commentTextSize = commentTextSize,
                                                lineHeight = lineHeight,
                                                modifier = Modifier.weight(1f).fillMaxWidth()
                                            )
                                        }
                                    }
                                    else -> {
                                        CenteredMessage(
                                            message = stringResource(Res.string.select_at_least_one_commentator),
                                            fontSize = commentTextSize
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(message: String, fontSize: Float = 14f) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = message, fontSize = fontSize.sp)
    }
}

@Composable
private fun CommentatorHeader(commentator: String, commentTextSize: Float, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(
                text = commentator,
                fontWeight = FontWeight.Bold,
                fontSize = (commentTextSize * 1.1f).sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PagedCommentatorsListView(
    commentators: List<String>,
    selectedCommentators: Set<String>,
    onCommentatorSelected: (String, Boolean) -> Unit,
    onShowWarning: (Boolean) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val listState = rememberLazyListState()
        Row(modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxHeight()) {
                items(count = commentators.size) { idx ->
                    val commentator = commentators[idx]
                    val isSelected = commentator in selectedCommentators
                    org.jetbrains.jewel.ui.component.CheckboxRow(
                        text = commentator,
                        checked = isSelected,
                        onCheckedChange = { checked ->
                            if (checked && selectedCommentators.size >= 4) onShowWarning(true)
                            onCommentatorSelected(commentator, checked)
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 8.dp)
                    )
                }
            }
            VerticalScrollbar(modifier = Modifier.fillMaxHeight(), adapter = rememberScrollbarAdapter(listState))
        }
    }
}

@Composable
private fun PagedCommentatorsRow(
    names: List<String>,
    titleToId: Map<String, Long>,
    lineId: Long,
    buildCommentariesPagerFor: (Long, Long?) -> Flow<PagingData<CommentaryWithText>>,
    primaryIndex: Int, // index in names that should drive onScroll; -1 = none
    commentariesScrollIndex: Int,
    commentariesScrollOffset: Int,
    onScroll: (Int, Int) -> Unit,
    onCommentClick: (CommentaryWithText) -> Unit,
    commentTextSize: Float,
    lineHeight: Float,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        names.forEachIndexed { index, name ->
            val commentatorId = titleToId[name]
            if (commentatorId != null) {
                Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 4.dp)) {
                    CommentatorHeader(commentator = name, commentTextSize = commentTextSize)
                    PagedCommentariesList(
                        buildCommentariesPagerFor = buildCommentariesPagerFor,
                        lineId = lineId,
                        commentatorId = commentatorId,
                        isPrimary = (index == primaryIndex),
                        initialIndex = commentariesScrollIndex,
                        initialOffset = commentariesScrollOffset,
                        onScroll = onScroll,
                        onCommentClick = onCommentClick,
                        commentTextSize = commentTextSize,
                        lineHeight = lineHeight
                    )
                }
            }
        }
    }
}

@Composable
private fun PagedCommentariesList(
    buildCommentariesPagerFor: (Long, Long?) -> Flow<PagingData<CommentaryWithText>>,
    lineId: Long,
    commentatorId: Long,
    isPrimary: Boolean,
    initialIndex: Int,
    initialOffset: Int,
    onScroll: (Int, Int) -> Unit,
    onCommentClick: (CommentaryWithText) -> Unit,
    commentTextSize: Float,
    lineHeight: Float
) {
    // Build a pager per commentator via ViewModel
    val pagerFlow: Flow<PagingData<CommentaryWithText>> = remember(lineId, commentatorId) {
        buildCommentariesPagerFor(lineId, commentatorId)
    }

    val lazyPagingItems: LazyPagingItems<CommentaryWithText> = pagerFlow.collectAsLazyPagingItems()

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex, initialFirstVisibleItemScrollOffset = initialOffset)

    if (isPrimary) {
        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect { (i, o) -> onScroll(i, o) }
        }
    }

    SelectionContainer {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(lazyPagingItems.itemCount) { index ->
                val commentary = lazyPagingItems[index]
                if (commentary != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 16.dp)
                            .pointerInput(Unit) { detectTapGestures(onTap = { onCommentClick(commentary) }) }
                    ) {
                        Text(
                            text = commentary.targetText,
                            textAlign = TextAlign.Justify,
                            fontFamily = FontFamily(Font(resource = Res.font.notorashihebrew)),
                            fontSize = commentTextSize.sp,
                            lineHeight = (commentTextSize * lineHeight).sp
                        )
                    }
                }
            }

            // Loading and error states
            lazyPagingItems.apply {
                when {
                    loadState.refresh is LoadState.Loading -> {
                        item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                    }
                    loadState.append is LoadState.Loading -> {
                        item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                    }
                    loadState.refresh is LoadState.Error -> {
                        val e = loadState.refresh as LoadState.Error
                        item { Text(text = e.error.message ?: "Error loading commentaries") }
                    }
                    loadState.append is LoadState.Error -> {
                        val e = loadState.append as LoadState.Error
                        item { Text(text = e.error.message ?: "Error loading more commentaries") }
                    }
                }
            }
        }
    }
}