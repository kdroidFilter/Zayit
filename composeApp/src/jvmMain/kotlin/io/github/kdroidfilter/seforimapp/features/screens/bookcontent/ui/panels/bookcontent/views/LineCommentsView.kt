package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.panels.bookcontent.views

import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.state.BookContentUiState

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
import app.cash.paging.compose.collectAsLazyPagingItems
import io.github.kdroidfilter.seforimapp.core.presentation.components.WarningBanner
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.rememberSplitPaneState
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Text
import seforimapp.composeapp.generated.resources.*

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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Header
        CommentariesHeader(showMaxCommentatorsWarning) {
            showMaxCommentatorsWarning = false
        }

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

@Composable
private fun CommentariesHeader(
    showWarning: Boolean,
    onCloseWarning: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(Res.string.commentaries),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(end = 16.dp)
        )
        if (showWarning) {
            WarningBanner(
                message = stringResource(Res.string.max_commentators_limit),
                onClose = onCloseWarning,
                modifier = Modifier.weight(1f)
            )
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

    _root_ide_package_.io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components.EnhancedHorizontalSplitPane(
        splitPaneState = splitState,
        firstMinSize = 150f,
        firstContent = {
            CommentatorsList(
                commentators = titleToIdMap.keys.sorted(),
                selectedCommentators = selectedCommentators.value,
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

@Composable
private fun CommentatorsList(
    commentators: List<String>,
    selectedCommentators: Set<String>,
    onSelectionChange: (String, Boolean) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val listState = rememberLazyListState()
        Row(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxHeight()
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
                            .padding(vertical = 4.dp, horizontal = 8.dp)
                    )
                }
            }
            VerticalScrollbar(
                modifier = Modifier.fillMaxHeight(),
                adapter = rememberScrollbarAdapter(listState)
            )
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
            onEvent(BookContentEvent.OpenCommentaryTarget(
                bookId = commentary.link.targetBookId,
                lineId = commentary.link.targetLineId
            ))
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
    val count = layoutConfig.selectedCommentators.size

    when (count) {
        1 -> SingleCommentatorView(layoutConfig, uiState)
        2 -> TwoCommentatorsView(layoutConfig, uiState)
        3 -> ThreeCommentatorsView(layoutConfig, uiState)
        4 -> FourCommentatorsView(layoutConfig, uiState)
    }
}

@Composable
private fun SingleCommentatorView(
    config: CommentariesLayoutConfig,
    uiState: BookContentUiState,
) {
    val name = config.selectedCommentators[0]
    val id = config.titleToIdMap[name] ?: return

    Column(modifier = Modifier.fillMaxSize()) {
        CommentatorHeader(name, config.textSizes.commentTextSize)
        CommentaryListView(
            lineId = config.lineId,
            commentatorId = id,
            isPrimary = true,
            config = config,
            uiState = uiState,
        )
    }
}

@Composable
private fun TwoCommentatorsView(
    config: CommentariesLayoutConfig,
    uiState: BookContentUiState,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        config.selectedCommentators.forEachIndexed { index, name ->
            val id = config.titleToIdMap[name] ?: return@forEachIndexed
            Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                CommentatorHeader(name, config.textSizes.commentTextSize)
                CommentaryListView(
                    lineId = config.lineId,
                    commentatorId = id,
                    isPrimary = index == 0,
                    config = config,
                    uiState = uiState,
                )
            }
        }
    }
}

@Composable
private fun ThreeCommentatorsView(
    config: CommentariesLayoutConfig,
    uiState: BookContentUiState,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // First row with 2 commentators
        Row(modifier = Modifier.weight(1f)) {
            config.selectedCommentators.take(2).forEachIndexed { index, name ->
                val id = config.titleToIdMap[name] ?: return@forEachIndexed
                Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    CommentatorHeader(name, config.textSizes.commentTextSize)
                    CommentaryListView(
                        lineId = config.lineId,
                        commentatorId = id,
                        isPrimary = index == 0,
                        config = config,
                        uiState = uiState,
                    )
                }
            }
        }
        // Second row with 1 commentator
        val thirdName = config.selectedCommentators[2]
        val thirdId = config.titleToIdMap[thirdName] ?: return
        Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
            CommentatorHeader(thirdName, config.textSizes.commentTextSize)
            CommentaryListView(
                lineId = config.lineId,
                commentatorId = thirdId,
                isPrimary = false,
                config = config,
                uiState = uiState,
            )
        }
    }
}

@Composable
private fun FourCommentatorsView(
    config: CommentariesLayoutConfig,
    uiState: BookContentUiState,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Two rows with 2 commentators each
        listOf(
            config.selectedCommentators.take(2),
            config.selectedCommentators.drop(2)
        ).forEachIndexed { rowIndex, rowCommentators ->
            Row(modifier = Modifier.weight(1f)) {
                rowCommentators.forEachIndexed { colIndex, name ->
                    val id = config.titleToIdMap[name] ?: return@forEachIndexed
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        CommentatorHeader(name, config.textSizes.commentTextSize)
                        CommentaryListView(
                            lineId = config.lineId,
                            commentatorId = id,
                            isPrimary = rowIndex == 0 && colIndex == 0,
                            config = config,
                            uiState = uiState,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentaryListView(
    lineId: Long,
    commentatorId: Long,
    isPrimary: Boolean,
    config: CommentariesLayoutConfig,
    uiState: BookContentUiState,
) {
    val providers = uiState.providers ?: return

    val pagerFlow = remember(lineId, commentatorId) {
        providers.buildCommentariesPagerFor(lineId, commentatorId)
    }

    val lazyPagingItems = pagerFlow.collectAsLazyPagingItems()

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = config.scrollIndex,
        initialFirstVisibleItemScrollOffset = config.scrollOffset
    )

    if (isPrimary) {
        LaunchedEffect(listState) {
            snapshotFlow {
                listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            }.collect { (i, o) ->
                config.onScroll(i, o)
            }
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

@Composable
private fun CommentaryItem(
    commentary: CommentaryWithText,
    textSizes: AnimatedTextSizes,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            }
    ) {
        Text(
            text = commentary.targetText,
            textAlign = TextAlign.Justify,
            fontFamily = FontFamily(Font(resource = Res.font.notorashihebrew)),
            fontSize = textSizes.commentTextSize.sp,
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