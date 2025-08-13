package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.panels.bookcontent.views

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import app.cash.paging.compose.collectAsLazyPagingItems
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.state.BookContentUiState
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components.EnhancedHorizontalSplitPane
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components.PaneHeader
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.rememberSplitPaneState
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import seforimapp.seforimapp.generated.resources.*

private const val MAX_COMMENTATORS = 4
private const val WARNING_DISPLAY_TIME = 5000L

@OptIn(ExperimentalSplitPaneApi::class)
@Composable
fun LineCommentsView(
    uiState: BookContentUiState,
    onEvent: (BookContentEvent) -> Unit,
) {
    val contentState = uiState.content
    val selectedLine = contentState.selectedLine

    // Animation settings
    val textSizes = rememberAnimatedTextSettings()

    // Warning state
    var showMaxCommentatorsWarning by remember { mutableStateOf(false) }
    LaunchedEffect(showMaxCommentatorsWarning) {
        if (showMaxCommentatorsWarning) {
            delay(WARNING_DISPLAY_TIME)
            showMaxCommentatorsWarning = false
        }
    }

    val paneInteractionSource = remember { MutableInteractionSource() }

    Column(modifier = Modifier.fillMaxSize().hoverable(paneInteractionSource)) {
        // Header
        PaneHeader(
            label = stringResource(Res.string.commentaries),
            warningMsg = stringResource(Res.string.max_commentators_limit),
            showWarning = showMaxCommentatorsWarning,
            onCloseWarning = {
                showMaxCommentatorsWarning = false
            },
            interactionSource = paneInteractionSource,
            onHide = { onEvent(BookContentEvent.ToggleCommentaries) }
        )
        Column(modifier = Modifier.padding(horizontal = 8.dp)) {
            when (selectedLine) {
                null -> CenteredMessage(stringResource(Res.string.select_line_for_commentaries))
                else -> CommentariesContent(
                    selectedLine = selectedLine,
                    uiState = uiState,
                    onEvent = onEvent,
                    textSizes = textSizes,
                    onShowWarning = { showMaxCommentatorsWarning = true }
                )
            }
        }
    }
}


@OptIn(ExperimentalSplitPaneApi::class)
@Composable
private fun CommentariesContent(
    selectedLine: Line,
    uiState: BookContentUiState,
    onEvent: (BookContentEvent) -> Unit,
    textSizes: AnimatedTextSizes,
    onShowWarning: () -> Unit
) {
    val providers = uiState.providers ?: return
    val contentState = uiState.content

    // Load available commentators
    val titleToIdMap = rememberCommentators(
        lineId = selectedLine.id,
        getAvailableCommentatorsForLine = providers.getAvailableCommentatorsForLine
    )

    if (titleToIdMap.isEmpty()) {
        CenteredMessage(stringResource(Res.string.no_commentaries_for_line))
        return
    }

    // Manage selected commentators
    val selectedCommentators = rememberSelectedCommentators(
        availableCommentators = titleToIdMap.keys.sorted(),
        initiallySelectedIds = contentState.selectedCommentatorIds,
        titleToIdMap = titleToIdMap,
        onSelectionChange = { ids ->
            onEvent(BookContentEvent.SelectedCommentatorsChanged(selectedLine.id, ids))
        }
    )

    val splitState = rememberSplitPaneState(0.10f)

    EnhancedHorizontalSplitPane(
        splitPaneState = splitState,
        firstMinSize = 150f,
        firstContent = {
            CommentatorsList(
                commentators = titleToIdMap.keys.sorted(),
                selectedCommentators = selectedCommentators.value,
                initialScrollIndex = uiState.content.commentatorsListScrollIndex,
                initialScrollOffset = uiState.content.commentatorsListScrollOffset,
                onScroll = { index, offset ->
                    onEvent(BookContentEvent.CommentatorsListScrolled(index, offset))
                },
                onSelectionChange = { name, checked ->
                    if (checked && selectedCommentators.value.size >= MAX_COMMENTATORS) {
                        onShowWarning()
                    } else {
                        selectedCommentators.value = if (checked) {
                            selectedCommentators.value + name
                        } else {
                            selectedCommentators.value - name
                        }
                    }
                }
            )
        },
        secondContent = {
            CommentariesDisplay(
                selectedCommentators = selectedCommentators.value.toList(),
                titleToIdMap = titleToIdMap,
                selectedLine = selectedLine,
                uiState = uiState,
                onEvent = onEvent,
                textSizes = textSizes
            )
        }
    )
}

@OptIn(FlowPreview::class)
@Composable
private fun CommentatorsList(
    commentators: List<String>,
    selectedCommentators: Set<String>,
    initialScrollIndex: Int,
    initialScrollOffset: Int,
    onScroll: (Int, Int) -> Unit,
    onSelectionChange: (String, Boolean) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val listState = rememberLazyListState(
            initialFirstVisibleItemIndex = initialScrollIndex,
            initialFirstVisibleItemScrollOffset = initialScrollOffset
        )
        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .distinctUntilChanged()
                .debounce(250)
                .collect { (i, o) -> onScroll(i, o) }
        }
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
            VerticallyScrollableContainer(
                scrollState = listState,
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    items(count = commentators.size) { idx ->
                        val commentator = commentators[idx]
                        CheckboxRow(
                            text = commentator,
                            checked = commentator in selectedCommentators,
                            onCheckedChange = { checked ->
                                onSelectionChange(commentator, checked)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentariesDisplay(
    selectedCommentators: List<String>,
    titleToIdMap: Map<String, Long>,
    selectedLine: Line,
    uiState: BookContentUiState,
    onEvent: (BookContentEvent) -> Unit,
    textSizes: AnimatedTextSizes
) {
    val contentState = uiState.content

    if (selectedCommentators.isEmpty()) {
        CenteredMessage(
            message = stringResource(Res.string.select_at_least_one_commentator),
            fontSize = textSizes.commentTextSize
        )
        return
    }

    val windowInfo = LocalWindowInfo.current

    val layoutConfig = CommentariesLayoutConfig(
        selectedCommentators = selectedCommentators,
        titleToIdMap = titleToIdMap,
        lineId = selectedLine.id,
        scrollIndex = contentState.commentariesScrollIndex,
        scrollOffset = contentState.commentariesScrollOffset,
        onScroll = { index, offset ->
            onEvent(BookContentEvent.CommentariesScrolled(index, offset))
        },
        onCommentClick = { commentary ->
            val mods = windowInfo.keyboardModifiers
            if (mods.isCtrlPressed || mods.isMetaPressed) {
                onEvent(
                    BookContentEvent.OpenCommentaryTarget(
                        bookId = commentary.link.targetBookId,
                        lineId = commentary.link.targetLineId
                    )
                )
            }
        },
        textSizes = textSizes
    )

    CommentariesLayout(
        layoutConfig = layoutConfig,
        uiState = uiState,
        onEvent = onEvent
    )
}

@Composable
private fun CommentariesLayout(
    layoutConfig: CommentariesLayoutConfig,
    uiState: BookContentUiState,
    onEvent: (BookContentEvent) -> Unit
) {
    layoutConfig.selectedCommentators.size

    CommentatorsGridView(layoutConfig, uiState, onEvent)
}

@Composable
private fun CommentatorsGridView(
    config: CommentariesLayoutConfig,
    uiState: BookContentUiState,
    onEvent: (BookContentEvent) -> Unit,
) {
    val rows = remember(config.selectedCommentators) {
        buildCommentatorRows(config.selectedCommentators)
    }
    var primaryAssigned = false
    Column(modifier = Modifier.fillMaxSize()) {
        rows.forEachIndexed { rowIndex, rowCommentators ->
            val rowModifier = if (rows.size > 1) Modifier.weight(1f) else Modifier.fillMaxHeight()
            Row(modifier = rowModifier.fillMaxWidth()) {
                rowCommentators.forEachIndexed { colIndex, name ->
                    val id = config.titleToIdMap[name] ?: return@forEachIndexed
                    val isPrimary = if (!primaryAssigned) {
                        primaryAssigned = true
                        true
                    } else false
                    val singleRowSingleCol = rows.size == 1 && rowCommentators.size == 1
                    val colModifier = if (singleRowSingleCol) {
                        Modifier.fillMaxHeight().weight(1f)
                    } else {
                        Modifier.weight(1f).padding(horizontal = 4.dp)
                    }
                    Column(modifier = colModifier) {
                        CommentatorHeader(name, config.textSizes.commentTextSize)
                        CommentaryListView(
                            lineId = config.lineId,
                            commentatorId = id,
                            isPrimary = isPrimary,
                            config = config,
                            uiState = uiState,
                            initialIndex = uiState.content.commentariesColumnScrollIndexByCommentator[id]
                                ?: uiState.content.commentariesScrollIndex,
                            initialOffset = uiState.content.commentariesColumnScrollOffsetByCommentator[id]
                                ?: uiState.content.commentariesScrollOffset,
                            onScroll = { i, o ->
                                onEvent(BookContentEvent.CommentaryColumnScrolled(id, i, o))
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun buildCommentatorRows(selected: List<String>): List<List<String>> {
    return when (selected.size) {
        0 -> emptyList()
        1 -> listOf(selected)
        2 -> listOf(selected)
        3 -> listOf(selected.take(2), selected.drop(2))
        else -> listOf(selected.take(2), selected.drop(2).take(2))
    }
}


@OptIn(FlowPreview::class)
@Composable
private fun CommentaryListView(
    lineId: Long,
    commentatorId: Long,
    isPrimary: Boolean,
    config: CommentariesLayoutConfig,
    uiState: BookContentUiState,
    initialIndex: Int,
    initialOffset: Int,
    onScroll: (Int, Int) -> Unit,
) {
    val providers = uiState.providers ?: return

    val pagerFlow = remember(lineId, commentatorId) {
        providers.buildCommentariesPagerFor(lineId, commentatorId)
    }

    val lazyPagingItems = pagerFlow.collectAsLazyPagingItems()

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialIndex,
        initialFirstVisibleItemScrollOffset = initialOffset
    )

    // Ensure restoration occurs after paging completes
    var hasRestored by remember(lineId, commentatorId) { mutableStateOf(false) }
    LaunchedEffect(lineId, commentatorId, lazyPagingItems.loadState, initialIndex, initialOffset) {
        if (!hasRestored) {
            // Wait until initial refresh finishes
            if (lazyPagingItems.loadState.refresh is LoadState.Loading) {
                while (lazyPagingItems.loadState.refresh is LoadState.Loading) {
                    delay(16)
                }
            }
            if (lazyPagingItems.itemCount > 0) {
                val safeIndex = initialIndex.coerceIn(0, lazyPagingItems.itemCount - 1)
                val safeOffset = initialOffset.coerceAtLeast(0)
                listState.scrollToItem(safeIndex, safeOffset)
                hasRestored = true
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .debounce(250)
            .collect { (i, o) ->
                onScroll(i, o)
            }
    }

    SelectionContainer {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            items(lazyPagingItems.itemCount) { index ->
                lazyPagingItems[index]?.let { commentary ->
                    CommentaryItem(
                        commentary = commentary,
                        textSizes = config.textSizes,
                        onClick = { config.onCommentClick(commentary) }
                    )
                }
            }

            // Loading states
            when (lazyPagingItems.loadState.refresh) {
                is LoadState.Loading -> item { LoadingIndicator() }
                is LoadState.Error -> item {
                    ErrorMessage((lazyPagingItems.loadState.refresh as LoadState.Error).error)
                }
                else -> {}
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun CommentaryItem(
    commentary: CommentaryWithText,
    textSizes: AnimatedTextSizes,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            }
    ) {
        // Unified HTML to AnnotatedString
        val annotated = remember(commentary.link.id, commentary.targetText, textSizes.commentTextSize) {
            buildAnnotatedFromHtml(
                commentary.targetText,
                textSizes.commentTextSize
            )
        }
        Text(
            text = annotated,
            textAlign = TextAlign.Justify,
            fontFamily = FontFamily(Font(resource = Res.font.notorashihebrew)),
            lineHeight = (textSizes.commentTextSize * textSizes.lineHeight).sp
        )
    }
}

@Composable
private fun CommentatorHeader(
    commentator: String,
    commentTextSize: Float
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = commentator,
            fontWeight = FontWeight.Bold,
            fontSize = (commentTextSize * 1.1f).sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CenteredMessage(
    message: String,
    fontSize: Float = 14f
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            fontSize = fontSize.sp
        )
    }
}

@Composable
private fun LoadingIndicator() {
    Box(
        Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorMessage(error: Throwable) {
    Text(
        text = error.message ?: "Error loading commentaries",
        modifier = Modifier.padding(16.dp)
    )
}

// Helper functions and data classes

@Composable
private fun rememberAnimatedTextSettings(): AnimatedTextSizes {
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

    return AnimatedTextSizes(commentTextSize, lineHeight)
}

@Composable
private fun rememberCommentators(
    lineId: Long,
    getAvailableCommentatorsForLine: suspend (Long) -> Map<String, Long>
): Map<String, Long> {
    var titleToIdMap by remember(lineId) {
        mutableStateOf<Map<String, Long>>(emptyMap())
    }

    LaunchedEffect(lineId) {
        runCatching {
            getAvailableCommentatorsForLine(lineId)
        }.onSuccess { map ->
            titleToIdMap = map
        }.onFailure {
            titleToIdMap = emptyMap()
        }
    }

    return titleToIdMap
}

@Composable
private fun rememberSelectedCommentators(
    availableCommentators: List<String>,
    initiallySelectedIds: Set<Long>,
    titleToIdMap: Map<String, Long>,
    onSelectionChange: (Set<Long>) -> Unit
): MutableState<Set<String>> {
    val selectedCommentators = remember(availableCommentators) {
        mutableStateOf<Set<String>>(emptySet())
    }

    // Initialize selection
    LaunchedEffect(initiallySelectedIds, titleToIdMap) {
        if (initiallySelectedIds.isNotEmpty() && titleToIdMap.isNotEmpty()) {
            val desiredNames = titleToIdMap
                .filterValues { it in initiallySelectedIds }
                .keys
                .toSet()
            if (desiredNames != selectedCommentators.value) {
                selectedCommentators.value = desiredNames
            }
        }
    }

    // Emit selection changes
    LaunchedEffect(selectedCommentators.value, titleToIdMap) {
        val ids = selectedCommentators.value
            .mapNotNull { titleToIdMap[it] }
            .toSet()
        onSelectionChange(ids)
    }

    // Keep selection valid
    LaunchedEffect(availableCommentators) {
        val filtered = selectedCommentators.value
            .filter { it in availableCommentators.toSet() }
            .toSet()
        if (filtered != selectedCommentators.value) {
            selectedCommentators.value = filtered
        }
    }

    return selectedCommentators
}

private data class AnimatedTextSizes(
    val commentTextSize: Float,
    val lineHeight: Float
)

private data class CommentariesLayoutConfig(
    val selectedCommentators: List<String>,
    val titleToIdMap: Map<String, Long>,
    val lineId: Long,
    val scrollIndex: Int,
    val scrollOffset: Int,
    val onScroll: (Int, Int) -> Unit,
    val onCommentClick: (CommentaryWithText) -> Unit,
    val textSizes: AnimatedTextSizes
)