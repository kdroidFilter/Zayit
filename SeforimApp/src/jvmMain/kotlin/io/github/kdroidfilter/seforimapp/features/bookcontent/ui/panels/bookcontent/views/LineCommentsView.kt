package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforimapp.core.presentation.components.HorizontalDivider
import io.github.kdroidfilter.seforimapp.core.presentation.text.highlightAnnotated
import io.github.kdroidfilter.seforimapp.core.presentation.typography.FontCatalog
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.CommentatorGroup
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.CommentatorItem
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.LineConnectionsSnapshot
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.EnhancedHorizontalSplitPane
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.PaneHeader
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.asStable
import io.github.kdroidfilter.seforimapp.icons.LayoutSidebarRight
import io.github.kdroidfilter.seforimapp.icons.LayoutSidebarRightOff
import io.github.kdroidfilter.seforimlibrary.core.text.HebrewTextUtils
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.rememberSplitPaneState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import seforimapp.seforimapp.generated.resources.*

private const val MAX_COMMENTATORS = 4
private const val SCROLL_DEBOUNCE_MS = 100L

@OptIn(ExperimentalSplitPaneApi::class)
@Composable
fun LineCommentsView(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    lineConnections: Map<Long, LineConnectionsSnapshot> = emptyMap(),
    showDiacritics: Boolean
) {
    val contentState = uiState.content
    val selectedLine = contentState.selectedLine

    // Animation settings with stable memorization
    val textSizes = rememberAnimatedTextSettings()
    val findQuery by AppSettings.findQueryFlow(uiState.tabId).collectAsState("")
    val showFind by AppSettings.findBarOpenFlow(uiState.tabId).collectAsState()
    val activeQuery = if (showFind) findQuery else ""

    val paneInteractionSource = remember { MutableInteractionSource() }
    var showCommentatorsList by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize().hoverable(paneInteractionSource)) {
        // Header
        PaneHeader(
            label = stringResource(Res.string.commentaries),
            interactionSource = paneInteractionSource,
            onHide = { onEvent(BookContentEvent.ToggleCommentaries) },
            actions = {
                CommentatorsSidebarToggleButton(
                    isVisible = showCommentatorsList,
                    onToggle = { showCommentatorsList = !showCommentatorsList }
                )
            }
        )
        Column(modifier = Modifier.padding(horizontal = 8.dp)) {
            when (selectedLine) {
                null -> CenteredMessage(stringResource(Res.string.select_line_for_commentaries))
                else -> CommentariesContent(
                    selectedLineId = selectedLine.id,
                    uiState = uiState,
                    onEvent = onEvent,
                    textSizes = textSizes,
                    onShowMaxLimit = { onEvent(BookContentEvent.CommentatorsSelectionLimitExceeded) },
                    findQueryText = activeQuery,
                    isCommentatorsListVisible = showCommentatorsList,
                    prefetchedGroups = lineConnections[selectedLine.id]?.commentatorGroups,
                    showDiacritics = showDiacritics
                )
            }
        }
    }
}

@OptIn(ExperimentalSplitPaneApi::class)
@Composable
private fun CommentariesContent(
    selectedLineId: Long,
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    textSizes: AnimatedTextSizes,
    onShowMaxLimit: () -> Unit,
    findQueryText: String,
    isCommentatorsListVisible: Boolean,
    prefetchedGroups: List<CommentatorGroup>?,
    showDiacritics: Boolean
) {
    val providers = uiState.providers ?: return
    val contentState = uiState.content

    val commentatorSelection = rememberCommentarySelectionData(
        lineId = selectedLineId,
        getCommentatorGroupsForLine = providers.getCommentatorGroupsForLine,
        prefetchedGroups = prefetchedGroups
    )

    val titleToIdMap = commentatorSelection.titleToIdMap
    val commentatorGroups = commentatorSelection.groups

    if (titleToIdMap.isEmpty()) {
        CenteredMessage(stringResource(Res.string.no_commentaries_for_line))
        return
    }

    // Flatten the grouped commentators to preserve global ordering (category, then pubDate)
    val commentatorsInDisplayOrder = remember(commentatorGroups) {
        commentatorGroups.flatMap { group -> group.commentators.map(CommentatorItem::name) }
    }

    // Manage selected commentators
    val selectedCommentators = rememberSelectedCommentators(
        availableCommentators = commentatorsInDisplayOrder,
        initiallySelectedIds = contentState.selectedCommentatorIds,
        titleToIdMap = titleToIdMap,
        onSelectionChange = { ids ->
            onEvent(BookContentEvent.SelectedCommentatorsChanged(selectedLineId, ids))
        }
    )

    val splitState = rememberSplitPaneState(0.10f)

    LaunchedEffect(isCommentatorsListVisible) {
        if (!isCommentatorsListVisible) {
            splitState.positionPercentage = 0f
        } else if (splitState.positionPercentage <= 0f) {
            splitState.positionPercentage = 0.10f
        }
    }

    EnhancedHorizontalSplitPane(
        splitPaneState = splitState.asStable(),
        firstMinSize = if (isCommentatorsListVisible) 150f else 0f,
        showSplitter = isCommentatorsListVisible,
        firstContent = {
            if (isCommentatorsListVisible) {
                CommentatorsList(
                    groups = commentatorGroups,
                    selectedCommentators = selectedCommentators.value,
                    initialScrollIndex = uiState.content.commentatorsListScrollIndex,
                    initialScrollOffset = uiState.content.commentatorsListScrollOffset,
                    onScroll = { index, offset ->
                        onEvent(BookContentEvent.CommentatorsListScrolled(index, offset))
                    },
                    onSelectionChange = { name, checked ->
                        if (checked && selectedCommentators.value.size >= MAX_COMMENTATORS) {
                            onShowMaxLimit()
                        } else {
                            selectedCommentators.value = if (checked) {
                                selectedCommentators.value + name
                            } else {
                                selectedCommentators.value - name
                            }
                        }
                    }
                )
            }
        },
        secondContent = {
            // Ensure selected commentators are always displayed in a stable order,
            // independent of the order in which they were selected.
            val selectedInDisplayOrder = commentatorsInDisplayOrder.filter { it in selectedCommentators.value }
            CommentariesDisplay(
                selectedCommentators = selectedInDisplayOrder,
                titleToIdMap = titleToIdMap,
                selectedLineId = selectedLineId,
                uiState = uiState,
                onEvent = onEvent,
                textSizes = textSizes,
                findQueryText = findQueryText,
                showDiacritics = showDiacritics
            )
        }
    )
}

@OptIn(FlowPreview::class)
@Composable
private fun CommentatorsList(
    groups: List<CommentatorGroup>,
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
                .debounce(SCROLL_DEBOUNCE_MS)
                .collect { (i, o) -> onScroll(i, o) }
        }

        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
            VerticallyScrollableContainer(
                scrollState = listState as ScrollableState,
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    val showGroupHeaders = groups.size > 1
                    groups.forEachIndexed { groupIndex, group ->
                        if (group.commentators.isEmpty()) return@forEachIndexed

                        if (groupIndex > 0) {
                            item(key = "divider-$groupIndex") {
                                HorizontalDivider()
                            }
                        }

                        if (showGroupHeaders && group.label.isNotBlank()) {
                            item(key = "header-$groupIndex-${group.label}") {
                                CommentatorGroupHeader(
                                    label = group.label
                                )
                            }
                        }

                        items(
                            count = group.commentators.size,
                            key = { index -> group.commentators[index].bookId }
                        ) { idx ->
                            val commentatorItem = group.commentators[idx]
                            val commentator = commentatorItem.name
                            val isSelected = remember(selectedCommentators, commentator) {
                                commentator in selectedCommentators
                            }
                            CheckboxRow(
                                text = commentator,
                                checked = isSelected,
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
}

@Composable
private fun CommentariesDisplay(
    selectedCommentators: List<String>,
    titleToIdMap: Map<String, Long>,
    selectedLineId: Long,
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    textSizes: AnimatedTextSizes,
    findQueryText: String,
    showDiacritics: Boolean
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

    // Memorizes the configuration to avoid recreating it
    val commentaryFontCode by AppSettings.commentaryFontCodeFlow.collectAsState()
    val commentaryFontFamily = FontCatalog.familyFor(commentaryFontCode)
    val boldScaleForPlatform = remember(commentaryFontCode) {
        val lacksBold = commentaryFontCode in setOf("notoserifhebrew", "notorashihebrew", "frankruhllibre")
        if (PlatformInfo.isMacOS && lacksBold) 1.08f else 1.0f
    }

    val layoutConfig = remember(
        selectedCommentators,
        titleToIdMap,
        selectedLineId,
        contentState.commentariesScrollIndex,
        contentState.commentariesScrollOffset,
        textSizes,
        commentaryFontFamily,
        boldScaleForPlatform,
        findQueryText,
        showDiacritics
    ) {
        CommentariesLayoutConfig(
            selectedCommentators = selectedCommentators,
            titleToIdMap = titleToIdMap,
            lineId = selectedLineId,
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
            textSizes = textSizes,
            fontFamily = commentaryFontFamily,
            boldScale = boldScaleForPlatform,
            highlightQuery = findQueryText,
            showDiacritics = showDiacritics
        )
    }

    CommentariesLayout(
        layoutConfig = layoutConfig,
        uiState = uiState,
        onEvent = onEvent
    )
}

@Composable
private fun CommentariesLayout(
    layoutConfig: CommentariesLayoutConfig,
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit
) {
    CommentatorsGridView(layoutConfig, uiState, onEvent)
}

@Composable
private fun CommentatorsGridView(
    config: CommentariesLayoutConfig,
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit
) {
    // Cache the calculation of lines
    val rows = remember(config.selectedCommentators) {
        buildCommentatorRows(config.selectedCommentators)
    }

    var primaryAssigned = false

    Column(modifier = Modifier.fillMaxSize()) {
        rows.forEachIndexed { rowIndex, rowCommentators ->
            val rowModifier = remember(rows.size) {
                if (rows.size > 1) Modifier.weight(1f) else Modifier.fillMaxHeight()
            }
            Row(modifier = rowModifier.fillMaxWidth()) {
                rowCommentators.forEachIndexed { colIndex, name ->
                    val id = config.titleToIdMap[name] ?: return@forEachIndexed
                    val isPrimary = remember(primaryAssigned) {
                        if (!primaryAssigned) {
                            primaryAssigned = true
                            true
                        } else false
                    }
                    val singleRowSingleCol = rows.size == 1 && rowCommentators.size == 1
                    val colModifier = remember(singleRowSingleCol) {
                        if (singleRowSingleCol) {
                            Modifier.fillMaxHeight().weight(1f)
                        } else {
                            Modifier.weight(1f).padding(horizontal = 4.dp)
                        }
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
                            },
                            highlightQuery = config.highlightQuery
                        )
                    }
                }
            }
        }
    }
}

private inline fun buildCommentatorRows(selected: List<String>): List<List<String>> {
    return when (selected.size) {
        0 -> emptyList()
        1 -> listOf(selected)
        2 -> listOf(selected)
        3 -> listOf(selected.subList(0, 2), selected.subList(2, selected.size))
        else -> listOf(selected.subList(0, 2), selected.subList(2, minOf(4, selected.size)))
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun CommentaryListView(
    lineId: Long,
    commentatorId: Long,
    isPrimary: Boolean,
    config: CommentariesLayoutConfig,
    uiState: BookContentState,
    initialIndex: Int,
    initialOffset: Int,
    onScroll: (Int, Int) -> Unit,
    highlightQuery: String
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

    var hasRestored by remember(lineId, commentatorId) { mutableStateOf(false) }
    LaunchedEffect(lineId, commentatorId, lazyPagingItems.loadState.refresh, initialIndex, initialOffset) {
        if (!hasRestored && lazyPagingItems.loadState.refresh !is LoadState.Loading) {
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
            .debounce(SCROLL_DEBOUNCE_MS)
            .collect { (i, o) ->
                onScroll(i, o)
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val isShiftPrimaryPress = event.buttons.isPrimaryPressed && event.keyboardModifiers.isShiftPressed
                    if (isShiftPrimaryPress) {
                        event.changes.forEach { it.consume() }
                    }
                }
            }
    ) {
        items(
            count = lazyPagingItems.itemCount,
            key = { index -> lazyPagingItems[index]?.link?.id ?: index } // Clé stable
        ) { index ->
            lazyPagingItems[index]?.let { commentary ->
                CommentaryItem(
                    commentary = commentary,
                    textSizes = config.textSizes,
                    fontFamily = config.fontFamily,
                    boldScale = config.boldScale,
                    highlightQuery = highlightQuery,
                    showDiacritics = config.showDiacritics,
                    onClick = { config.onCommentClick(commentary) }
                )
            }
        }

        // Loading states
        when (val loadState = lazyPagingItems.loadState.refresh) {
            is LoadState.Loading -> item { LoadingIndicator() }
            is LoadState.Error -> item {
                ErrorMessage(loadState.error)
            }
            else -> {}
        }
    }
}

@Composable
private fun CommentaryItem(
    commentary: CommentaryWithText,
    textSizes: AnimatedTextSizes,
    fontFamily: FontFamily,
    boldScale: Float = 1.0f,
    highlightQuery: String,
    showDiacritics: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .pointerInput(onClick) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val modifiers = currentEvent.keyboardModifiers
                    val isCtrlMetaPrimary = (modifiers.isCtrlPressed || modifiers.isMetaPressed) &&
                        currentEvent.buttons.isPrimaryPressed
                    if (isCtrlMetaPrimary) {
                        down.consume()
                    }
                    val up = waitForUpOrCancellation()
                    if (isCtrlMetaPrimary && up != null) {
                        up.consume()
                        onClick()
                    }
                }
            }
    ) {
        val processedText = remember(commentary.link.id, commentary.targetText, showDiacritics) {
            if (showDiacritics) commentary.targetText else HebrewTextUtils.removeAllDiacritics(commentary.targetText)
        }

        val annotated = remember(
            commentary.link.id,
            processedText,
            textSizes.commentTextSize,
            boldScale,
            showDiacritics
        ) {
            buildAnnotatedFromHtml(
                processedText,
                textSizes.commentTextSize,
                boldScale = if (boldScale < 1f) 1f else boldScale
            )
        }

        val display: AnnotatedString = remember(annotated, highlightQuery) {
            if (highlightQuery.isBlank()) annotated
            else highlightAnnotated(annotated, highlightQuery)
        }

        SelectionContainer {
            Text(
                text = display,
                textAlign = TextAlign.Justify,
                fontFamily = fontFamily,
                lineHeight = (textSizes.commentTextSize * textSizes.lineHeight).sp
            )
        }
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
        animationSpec = tween(durationMillis = 200), // Réduit de 300ms
        label = "commentTextSizeAnim"
    )
    val rawLineHeight by AppSettings.lineHeightFlow.collectAsState()
    val lineHeight by animateFloatAsState(
        targetValue = rawLineHeight,
        animationSpec = tween(durationMillis = 200), // Réduit de 300ms
        label = "commentLineHeightAnim"
    )

    return remember(commentTextSize, lineHeight) {
        AnimatedTextSizes(commentTextSize, lineHeight)
    }
}

@Immutable
private data class CommentatorSelectionData(
    val titleToIdMap: Map<String, Long>,
    val groups: List<CommentatorGroup>
)

@Composable
private fun rememberCommentarySelectionData(
    lineId: Long,
    getCommentatorGroupsForLine: suspend (Long) -> List<CommentatorGroup>,
    prefetchedGroups: List<CommentatorGroup>? = null
): CommentatorSelectionData {
    var groups by remember(lineId, prefetchedGroups) {
        mutableStateOf(prefetchedGroups ?: emptyList())
    }

    LaunchedEffect(lineId, prefetchedGroups) {
        if (prefetchedGroups != null) {
            groups = prefetchedGroups
            return@LaunchedEffect
        }

        runCatching { getCommentatorGroupsForLine(lineId) }
            .onSuccess { loaded -> groups = loaded }
            .onFailure { groups = emptyList() }
    }

    val titleToIdMap = remember(groups) {
        val map = LinkedHashMap<String, Long>()
        groups.forEach { group ->
            group.commentators.forEach { item ->
                if (!map.containsKey(item.name)) {
                    map[item.name] = item.bookId
                }
            }
        }
        map
    }

    return remember(groups, titleToIdMap) {
        CommentatorSelectionData(
            titleToIdMap = titleToIdMap,
            groups = groups
        )
    }
}

@Composable
private fun CommentatorGroupHeader(
    label: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            textAlign = TextAlign.Start
        )
    }
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
    // Only skip emissions when we programmatically change selection
    val skipEmit = remember { mutableStateOf(false) }

    // Initialize selection with optimization
    LaunchedEffect(initiallySelectedIds, titleToIdMap) {
        if (initiallySelectedIds.isNotEmpty() && titleToIdMap.isNotEmpty()) {
            val desiredNames = buildSet {
                titleToIdMap.forEach { (name, id) ->
                    if (id in initiallySelectedIds) add(name)
                }
            }
            if (desiredNames != selectedCommentators.value) {
                skipEmit.value = true
                selectedCommentators.value = desiredNames
            }
        }
    }

    // Emit selection changes with optimization
    LaunchedEffect(selectedCommentators.value, titleToIdMap) {
        val ids = buildSet {
            selectedCommentators.value.forEach { name ->
                titleToIdMap[name]?.let { add(it) }
            }
        }
        if (skipEmit.value) {
            skipEmit.value = false
        } else {
            onSelectionChange(ids)
        }
    }

    // Keep selection valid with optimization
    val availableSet = remember(availableCommentators) {
        availableCommentators.toSet()
    }

    LaunchedEffect(availableSet) {
        val filtered = selectedCommentators.value.intersect(availableSet)
        if (filtered != selectedCommentators.value) {
            skipEmit.value = true
            selectedCommentators.value = filtered
        }
    }

    return selectedCommentators
}

@Immutable
private data class AnimatedTextSizes(
    val commentTextSize: Float,
    val lineHeight: Float
)

@Immutable
private data class CommentariesLayoutConfig(
    val selectedCommentators: List<String>,
    val titleToIdMap: Map<String, Long>,
    val lineId: Long,
    val scrollIndex: Int,
    val scrollOffset: Int,
    val onScroll: (Int, Int) -> Unit,
    val onCommentClick: (CommentaryWithText) -> Unit,
    val textSizes: AnimatedTextSizes,
    val fontFamily: FontFamily,
    val boldScale: Float,
    val highlightQuery: String,
    val showDiacritics: Boolean
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommentatorsSidebarToggleButton(
    isVisible: Boolean,
    onToggle: () -> Unit
) {
    val icon: ImageVector = if (isVisible) LayoutSidebarRight else LayoutSidebarRightOff
    val toggleText = if (isVisible) {
        stringResource(Res.string.hide_commentators_sidebar)
    } else {
        stringResource(Res.string.show_commentators_sidebar)
    }
    val painter = rememberVectorPainter(icon)
    Tooltip({ Text(toggleText) }) {
        IconButton(onClick = onToggle) { _ ->
            Icon(
                painter = painter,
                contentDescription = toggleText,
                modifier = Modifier.size(16.dp),
                tint = JewelTheme.globalColors.text.normal
            )
        }
    }
}
