package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.kdroidfilter.seforim.htmlparser.SkiaHtmlImageBuilder
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.presentation.components.HorizontalDivider
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import io.github.kdroidfilter.seforimapp.core.presentation.text.highlightAnnotated
import io.github.kdroidfilter.seforimapp.core.presentation.typography.FontCatalog
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.CommentatorGroup
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.CommentatorItem
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.LineConnectionsSnapshot
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.EnhancedHorizontalSplitPane
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.PaneHeader
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.SafeSelectionContainer
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.asStable
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import io.github.kdroidfilter.seforimapp.icons.LayoutSidebarRight
import io.github.kdroidfilter.seforimapp.icons.LayoutSidebarRightOff
import io.github.kdroidfilter.seforimlibrary.core.text.HebrewTextUtils
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
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
    showDiacritics: Boolean,
    lineConnections: Map<Long, LineConnectionsSnapshot> = emptyMap(),
) {
    val contentState = uiState.content
    val selectedLine = contentState.primaryLine
    val selectedLineIds = contentState.selectedLineIds.toImmutableList()
    // Multi-sélection manuelle (Ctrl+click) = afficher commentaires de toutes les lignes
    // TOC entry selection = afficher commentaires seulement de la ligne primaire
    val isManualMultiSelection = selectedLineIds.size > 1 && !contentState.isTocEntrySelection

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
                    onToggle = { showCommentatorsList = !showCommentatorsList },
                )
            },
        )
        Column(modifier = Modifier.padding(horizontal = 8.dp)) {
            when {
                selectedLine == null -> {
                    CenteredMessage(stringResource(Res.string.select_line_for_commentaries))
                }

                isManualMultiSelection -> {
                    MultiLineCommentariesContent(
                        selectedLineIds = selectedLineIds,
                        uiState = uiState,
                        onEvent = onEvent,
                        textSizes = textSizes,
                        onShowMaxLimit = { onEvent(BookContentEvent.CommentatorsSelectionLimitExceeded) },
                        findQueryText = activeQuery,
                        isCommentatorsListVisible = showCommentatorsList,
                        showDiacritics = showDiacritics,
                    )
                }

                else -> {
                    CommentariesContent(
                        selectedLineId = selectedLine.id,
                        uiState = uiState,
                        onEvent = onEvent,
                        textSizes = textSizes,
                        onShowMaxLimit = { onEvent(BookContentEvent.CommentatorsSelectionLimitExceeded) },
                        findQueryText = activeQuery,
                        isCommentatorsListVisible = showCommentatorsList,
                        prefetchedGroups = lineConnections[selectedLine.id]?.commentatorGroups,
                        showDiacritics = showDiacritics,
                    )
                }
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
    showDiacritics: Boolean,
) {
    val providers = uiState.providers ?: return
    val contentState = uiState.content

    val commentatorSelection =
        rememberCommentarySelectionData(
            lineId = selectedLineId,
            getCommentatorGroupsForLine = providers.getCommentatorGroupsForLine,
            prefetchedGroups = prefetchedGroups,
        )

    val titleToIdMap = commentatorSelection.titleToIdMap
    val commentatorGroups = commentatorSelection.groups

    if (titleToIdMap.isEmpty()) {
        CenteredMessage(stringResource(Res.string.no_commentaries_for_line))
        return
    }

    // Flatten the grouped commentators to preserve global ordering (category, then pubDate)
    val commentatorsInDisplayOrder =
        remember(commentatorGroups) {
            commentatorGroups.flatMap { group -> group.commentators.map(CommentatorItem::name) }
        }

    // Manage selected commentators
    val selectedCommentators =
        rememberSelectedCommentators(
            availableCommentators = commentatorsInDisplayOrder,
            initiallySelectedIds = contentState.selectedCommentatorIds,
            titleToIdMap = titleToIdMap,
            onSelectionChange = { ids ->
                onEvent(BookContentEvent.SelectedCommentatorsChanged(selectedLineId, ids))
            },
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
                            selectedCommentators.value =
                                if (checked) {
                                    selectedCommentators.value + name
                                } else {
                                    selectedCommentators.value - name
                                }
                        }
                    },
                )
            }
        },
        secondContent = {
            // Ensure selected commentators are always displayed in a stable order,
            // independent of the order in which they were selected.
            val selectedInDisplayOrder =
                remember(commentatorsInDisplayOrder, selectedCommentators.value) {
                    commentatorsInDisplayOrder.filter { it in selectedCommentators.value }.toImmutableList()
                }
            CommentariesDisplay(
                selectedCommentators = selectedInDisplayOrder,
                titleToIdMap = titleToIdMap,
                selectedLineId = selectedLineId,
                uiState = uiState,
                onEvent = onEvent,
                textSizes = textSizes,
                findQueryText = findQueryText,
                showDiacritics = showDiacritics,
            )
        },
    )
}

/**
 * Multi-line version of CommentariesContent for multi-selection.
 * Aggregates commentators and commentaries from all selected lines.
 */
@OptIn(ExperimentalSplitPaneApi::class)
@Composable
private fun MultiLineCommentariesContent(
    selectedLineIds: ImmutableList<Long>,
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    textSizes: AnimatedTextSizes,
    onShowMaxLimit: () -> Unit,
    findQueryText: String,
    isCommentatorsListVisible: Boolean,
    showDiacritics: Boolean,
) {
    val providers = uiState.providers ?: return
    val contentState = uiState.content
    val primaryLineId = contentState.primarySelectedLineId ?: selectedLineIds.firstOrNull() ?: return

    // Use multi-line provider to get aggregated commentator groups
    val commentatorGroups by produceState<List<CommentatorGroup>>(emptyList(), selectedLineIds) {
        value = providers.getCommentatorGroupsForLines(selectedLineIds)
    }

    val titleToIdMap =
        remember(commentatorGroups) {
            commentatorGroups.flatMap { it.commentators }.associate { it.name to it.bookId }
        }

    if (titleToIdMap.isEmpty()) {
        CenteredMessage(stringResource(Res.string.no_commentaries_for_line))
        return
    }

    val commentatorsInDisplayOrder =
        remember(commentatorGroups) {
            commentatorGroups.flatMap { group -> group.commentators.map(CommentatorItem::name) }
        }

    val selectedCommentators =
        rememberSelectedCommentators(
            availableCommentators = commentatorsInDisplayOrder,
            initiallySelectedIds = contentState.selectedCommentatorIds,
            titleToIdMap = titleToIdMap,
            onSelectionChange = { ids ->
                onEvent(BookContentEvent.SelectedCommentatorsChanged(primaryLineId, ids))
            },
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
                            selectedCommentators.value =
                                if (checked) {
                                    selectedCommentators.value + name
                                } else {
                                    selectedCommentators.value - name
                                }
                        }
                    },
                )
            }
        },
        secondContent = {
            val selectedInDisplayOrder =
                remember(commentatorsInDisplayOrder, selectedCommentators.value) {
                    commentatorsInDisplayOrder.filter { it in selectedCommentators.value }.toImmutableList()
                }
            MultiLineCommentariesDisplay(
                selectedCommentators = selectedInDisplayOrder,
                titleToIdMap = titleToIdMap,
                selectedLineIds = selectedLineIds,
                uiState = uiState,
                onEvent = onEvent,
                textSizes = textSizes,
                findQueryText = findQueryText,
                showDiacritics = showDiacritics,
            )
        },
    )
}

/**
 * Multi-line version of CommentariesDisplay.
 * Uses the multi-line pager to aggregate commentaries from all selected lines.
 */
@Composable
private fun MultiLineCommentariesDisplay(
    selectedCommentators: ImmutableList<String>,
    titleToIdMap: Map<String, Long>,
    selectedLineIds: ImmutableList<Long>,
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    textSizes: AnimatedTextSizes,
    findQueryText: String,
    showDiacritics: Boolean,
) {
    if (selectedCommentators.isEmpty()) {
        CenteredMessage(
            message = stringResource(Res.string.select_at_least_one_commentator),
            fontSize = textSizes.commentTextSize,
        )
        return
    }

    val layoutConfig =
        rememberCommentariesLayoutConfig(
            selectedCommentators = selectedCommentators,
            titleToIdMap = titleToIdMap,
            textSizes = textSizes,
            findQueryText = findQueryText,
            showDiacritics = showDiacritics,
            onEvent = onEvent,
        )

    MultiLineCommentatorsGridView(
        config = layoutConfig,
        lineIds = selectedLineIds,
        uiState = uiState,
        onEvent = onEvent,
    )
}

/**
 * Grid view for multi-line commentaries.
 */
@Composable
private fun MultiLineCommentatorsGridView(
    config: CommentariesLayoutConfig,
    lineIds: ImmutableList<Long>,
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
) {
    val providers = uiState.providers ?: return
    CommentatorsGrid(config = config) { commentatorId ->
        val pagerFlow =
            remember(lineIds, commentatorId) {
                providers.buildCommentariesPagerForLines(lineIds, commentatorId)
            }
        val initialIndex =
            uiState.content.commentariesColumnScrollIndexByCommentator[commentatorId]
                ?: uiState.content.commentariesScrollIndex
        val initialOffset =
            uiState.content.commentariesColumnScrollOffsetByCommentator[commentatorId]
                ?: uiState.content.commentariesScrollOffset
        CommentariesPagedList(
            pagerFlow = pagerFlow,
            initialIndex = initialIndex,
            initialOffset = initialOffset,
            onScroll = { i, o ->
                onEvent(BookContentEvent.CommentaryColumnScrolled(commentatorId, i, o))
            },
            config = config,
            restoreScrollKey = null,
            showLoadingStates = true,
            wrapInScrollableContainer = true,
        )
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun CommentatorsList(
    groups: List<CommentatorGroup>,
    selectedCommentators: Set<String>,
    initialScrollIndex: Int,
    initialScrollOffset: Int,
    onScroll: (Int, Int) -> Unit,
    onSelectionChange: (String, Boolean) -> Unit,
) {
    val currentOnScroll by rememberUpdatedState(onScroll)
    Box(modifier = Modifier.fillMaxSize()) {
        val listState =
            rememberLazyListState(
                initialFirstVisibleItemIndex = initialScrollIndex,
                initialFirstVisibleItemScrollOffset = initialScrollOffset,
            )

        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .distinctUntilChanged()
                .debounce(SCROLL_DEBOUNCE_MS)
                .collect { (i, o) -> currentOnScroll(i, o) }
        }

        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
            VerticallyScrollableContainer(
                scrollState = listState as ScrollableState,
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxHeight(),
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
                                    label = group.label,
                                )
                            }
                        }

                        items(
                            count = group.commentators.size,
                            key = { index -> group.commentators[index].bookId },
                        ) { idx ->
                            val commentatorItem = group.commentators[idx]
                            val commentator = commentatorItem.name
                            val isSelected =
                                remember(selectedCommentators, commentator) {
                                    commentator in selectedCommentators
                                }
                            CheckboxRow(
                                text = commentator,
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    onSelectionChange(commentator, checked)
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp),
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
    selectedCommentators: ImmutableList<String>,
    titleToIdMap: Map<String, Long>,
    selectedLineId: Long,
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    textSizes: AnimatedTextSizes,
    findQueryText: String,
    showDiacritics: Boolean,
) {
    if (selectedCommentators.isEmpty()) {
        CenteredMessage(
            message = stringResource(Res.string.select_at_least_one_commentator),
            fontSize = textSizes.commentTextSize,
        )
        return
    }

    val layoutConfig =
        rememberCommentariesLayoutConfig(
            selectedCommentators = selectedCommentators,
            titleToIdMap = titleToIdMap,
            textSizes = textSizes,
            findQueryText = findQueryText,
            showDiacritics = showDiacritics,
            onEvent = onEvent,
        )

    CommentatorsGridView(
        config = layoutConfig,
        lineId = selectedLineId,
        uiState = uiState,
        onEvent = onEvent,
    )
}

@Composable
private fun CommentatorsGridView(
    config: CommentariesLayoutConfig,
    lineId: Long,
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
) {
    val providers = uiState.providers ?: return
    CommentatorsGrid(config = config) { commentatorId ->
        val pagerFlow =
            remember(lineId, commentatorId) {
                providers.buildCommentariesPagerFor(lineId, commentatorId)
            }
        val initialIndex =
            uiState.content.commentariesColumnScrollIndexByCommentator[commentatorId]
                ?: uiState.content.commentariesScrollIndex
        val initialOffset =
            uiState.content.commentariesColumnScrollOffsetByCommentator[commentatorId]
                ?: uiState.content.commentariesScrollOffset
        CommentariesPagedList(
            pagerFlow = pagerFlow,
            initialIndex = initialIndex,
            initialOffset = initialOffset,
            onScroll = { i, o ->
                onEvent(BookContentEvent.CommentaryColumnScrolled(commentatorId, i, o))
            },
            config = config,
            restoreScrollKey = lineId to commentatorId,
            showLoadingStates = true,
            wrapInScrollableContainer = true,
        )
    }
}

/**
 * Shared grid scaffolding for single-line and multi-line commentaries. The [column] slot
 * renders one commentator column given its bookId.
 */
@Composable
private fun CommentatorsGrid(
    config: CommentariesLayoutConfig,
    column: @Composable (commentatorId: Long) -> Unit,
) {
    val rows =
        remember(config.selectedCommentators) {
            buildCommentatorRows(config.selectedCommentators)
        }

    Column(modifier = Modifier.fillMaxSize()) {
        rows.forEach { rowCommentators ->
            val rowModifier =
                remember(rows.size) {
                    if (rows.size > 1) Modifier.weight(1f) else Modifier.fillMaxHeight()
                }
            Row(modifier = rowModifier.fillMaxWidth()) {
                rowCommentators.forEach { name ->
                    val id = config.titleToIdMap[name] ?: return@forEach
                    val singleRowSingleCol = rows.size == 1 && rowCommentators.size == 1
                    val colModifier =
                        remember(singleRowSingleCol) {
                            if (singleRowSingleCol) {
                                Modifier.fillMaxHeight().weight(1f)
                            } else {
                                Modifier.weight(1f).padding(horizontal = 4.dp)
                            }
                        }
                    Column(modifier = colModifier) {
                        CommentatorHeader(name, config.textSizes.commentTextSize)
                        column(id)
                    }
                }
            }
        }
    }
}

private fun buildCommentatorRows(selected: List<String>): List<List<String>> =
    when (selected.size) {
        0 -> emptyList()
        1 -> listOf(selected)
        2 -> listOf(selected)
        3 -> listOf(selected.subList(0, 2), selected.subList(2, selected.size))
        else -> listOf(selected.subList(0, 2), selected.subList(2, minOf(4, selected.size)))
    }

/**
 * Paged list of [CommentaryItem]s for one commentator, wrapped in a [SafeSelectionContainer].
 * Shared by single-line and multi-line views; asymmetric behaviors are gated behind flags.
 */
@OptIn(FlowPreview::class)
@Composable
private fun CommentariesPagedList(
    pagerFlow: Flow<PagingData<CommentaryWithText>>,
    initialIndex: Int,
    initialOffset: Int,
    onScroll: (Int, Int) -> Unit,
    config: CommentariesLayoutConfig,
    restoreScrollKey: Any?,
    showLoadingStates: Boolean,
    wrapInScrollableContainer: Boolean,
) {
    val currentOnScroll by rememberUpdatedState(onScroll)
    val lazyPagingItems = pagerFlow.collectAsLazyPagingItems()

    val listState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = initialIndex,
            initialFirstVisibleItemScrollOffset = initialOffset,
        )

    if (restoreScrollKey != null) {
        var hasRestored by remember(restoreScrollKey) { mutableStateOf(false) }
        LaunchedEffect(restoreScrollKey, lazyPagingItems.loadState.refresh, initialIndex, initialOffset) {
            if (!hasRestored && lazyPagingItems.loadState.refresh !is LoadState.Loading) {
                if (lazyPagingItems.itemCount > 0) {
                    val safeIndex = initialIndex.coerceIn(0, lazyPagingItems.itemCount - 1)
                    val safeOffset = initialOffset.coerceAtLeast(0)
                    listState.scrollToItem(safeIndex, safeOffset)
                    hasRestored = true
                }
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .debounce(SCROLL_DEBOUNCE_MS)
            .collect { (i, o) -> currentOnScroll(i, o) }
    }

    SafeSelectionContainer(modifier = Modifier.fillMaxSize()) {
        if (wrapInScrollableContainer) {
            VerticallyScrollableContainer(
                scrollState = listState as ScrollableState,
                scrollbarModifier = Modifier.fillMaxHeight(),
            ) {
                CommentariesLazyColumn(
                    listState = listState,
                    lazyPagingItems = lazyPagingItems,
                    config = config,
                    showLoadingStates = showLoadingStates,
                )
            }
        } else {
            CommentariesLazyColumn(
                listState = listState,
                lazyPagingItems = lazyPagingItems,
                config = config,
                showLoadingStates = showLoadingStates,
            )
        }
    }
}

@Composable
private fun CommentariesLazyColumn(
    listState: LazyListState,
    lazyPagingItems: LazyPagingItems<CommentaryWithText>,
    config: CommentariesLayoutConfig,
    showLoadingStates: Boolean,
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(
            count = lazyPagingItems.itemCount,
            key = { index -> lazyPagingItems[index]?.link?.id ?: index },
        ) { index ->
            lazyPagingItems[index]?.let { commentary ->
                CommentaryItem(
                    linkId = commentary.link.id,
                    targetText = commentary.targetText,
                    textSizes = config.textSizes,
                    fontFamily = config.fontFamily,
                    boldScale = config.boldScale,
                    highlightQuery = config.highlightQuery,
                    showDiacritics = config.showDiacritics,
                    onClick = { config.onCommentClick(commentary) },
                )
            }
        }

        if (showLoadingStates) {
            when (val loadState = lazyPagingItems.loadState.refresh) {
                is LoadState.Loading -> item { LoadingIndicator() }
                is LoadState.Error -> item { ErrorMessage(loadState.error) }
                else -> {}
            }
        }
    }
}

@Composable
private fun CommentaryItem(
    linkId: Long,
    targetText: String,
    textSizes: AnimatedTextSizes,
    fontFamily: FontFamily,
    highlightQuery: String,
    showDiacritics: Boolean,
    boldScale: Float = 1.0f,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .pointerInput(onClick) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val modifiers = currentEvent.keyboardModifiers
                        val isCtrlMetaPrimary =
                            (modifiers.isCtrlPressed || modifiers.isMetaPressed) &&
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
                },
    ) {
        val processedText =
            remember(linkId, targetText, showDiacritics) {
                if (showDiacritics) targetText else HebrewTextUtils.removeAllDiacritics(targetText)
            }

        // Footnote marker color from theme
        val footnoteMarkerColor = JewelTheme.globalColors.outlines.focused

        val isDarkTheme = JewelTheme.isDark
        val imageColorFilter: @Composable () -> ColorFilter? =
            remember(isDarkTheme) {
                { if (isDarkTheme) SkiaHtmlImageBuilder.InvertColorFilter else null }
            }
        val annotatedWithImages =
            remember(
                linkId,
                processedText,
                textSizes.commentTextSize,
                boldScale,
                showDiacritics,
                footnoteMarkerColor,
                imageColorFilter,
            ) {
                val inline = mutableMapOf<String, InlineTextContent>()
                val annotated =
                    buildAnnotatedFromHtml(
                        processedText,
                        textSizes.commentTextSize,
                        boldScale = if (boldScale < 1f) 1f else boldScale,
                        footnoteMarkerColor = footnoteMarkerColor,
                        inlineContent = inline,
                        imageContentBuilder = SkiaHtmlImageBuilder.build(imageColorFilter),
                    )
                annotated to inline.toMap()
            }
        val annotated = annotatedWithImages.first
        val inlineImageContent = annotatedWithImages.second

        val display: AnnotatedString =
            remember(annotated, highlightQuery) {
                if (highlightQuery.isBlank()) {
                    annotated
                } else {
                    highlightAnnotated(annotated, highlightQuery)
                }
            }

        Text(
            text = display,
            textAlign = TextAlign.Justify,
            fontFamily = fontFamily,
            lineHeight = (textSizes.commentTextSize * textSizes.lineHeight).sp,
            inlineContent = inlineImageContent,
        )
    }
}

@Composable
private fun CommentatorHeader(
    commentator: String,
    commentTextSize: Float,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = commentator,
            fontWeight = FontWeight.Bold,
            fontSize = (commentTextSize * 1.1f).sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CenteredMessage(
    message: String,
    fontSize: Float = 14f,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            fontSize = fontSize.sp,
        )
    }
}

@Composable
private fun LoadingIndicator() {
    Box(
        Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorMessage(error: Throwable) {
    Text(
        text = error.message ?: "Error loading commentaries",
        modifier = Modifier.padding(16.dp),
    )
}

// Helper functions and data classes

@Composable
private fun rememberAnimatedTextSettings(): AnimatedTextSizes {
    val rawTextSize by AppSettings.textSizeFlow.collectAsState()
    val isTabSelected = LocalTabSelected.current
    val zoomAnimSpec = if (isTabSelected) tween<Float>(durationMillis = 200) else snap()
    val commentTextSize by animateFloatAsState(
        targetValue = rawTextSize * 0.875f,
        animationSpec = zoomAnimSpec,
        label = "commentTextSizeAnim",
    )
    val rawLineHeight by AppSettings.lineHeightFlow.collectAsState()
    val lineHeight by animateFloatAsState(
        targetValue = rawLineHeight,
        animationSpec = zoomAnimSpec,
        label = "commentLineHeightAnim",
    )

    return remember(commentTextSize, lineHeight) {
        AnimatedTextSizes(commentTextSize, lineHeight)
    }
}

@Immutable
private data class CommentatorSelectionData(
    val titleToIdMap: Map<String, Long>,
    val groups: List<CommentatorGroup>,
)

@Composable
private fun rememberCommentarySelectionData(
    lineId: Long,
    getCommentatorGroupsForLine: suspend (Long) -> List<CommentatorGroup>,
    prefetchedGroups: List<CommentatorGroup>? = null,
): CommentatorSelectionData {
    var groups by remember(lineId, prefetchedGroups) {
        mutableStateOf(prefetchedGroups ?: emptyList())
    }
    val currentGetCommentatorGroupsForLine by rememberUpdatedState(getCommentatorGroupsForLine)

    LaunchedEffect(lineId, prefetchedGroups) {
        if (prefetchedGroups != null) {
            groups = prefetchedGroups
            return@LaunchedEffect
        }

        runSuspendCatching { currentGetCommentatorGroupsForLine(lineId) }
            .onSuccess { loaded -> groups = loaded }
            .onFailure { groups = emptyList() }
    }

    val titleToIdMap =
        remember(groups) {
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
            groups = groups,
        )
    }
}

@Composable
private fun CommentatorGroupHeader(label: String) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
private fun rememberSelectedCommentators(
    availableCommentators: List<String>,
    initiallySelectedIds: Set<Long>,
    titleToIdMap: Map<String, Long>,
    onSelectionChange: (Set<Long>) -> Unit,
): MutableState<Set<String>> {
    val selectedCommentators =
        remember(availableCommentators) {
            mutableStateOf<Set<String>>(emptySet())
        }
    // Only skip emissions when we programmatically change selection
    val skipEmit = remember { mutableStateOf(false) }
    val currentOnSelectionChange by rememberUpdatedState(onSelectionChange)

    // Initialize selection with optimization
    LaunchedEffect(initiallySelectedIds, titleToIdMap) {
        if (initiallySelectedIds.isNotEmpty() && titleToIdMap.isNotEmpty()) {
            val desiredNames =
                buildSet {
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
        val ids =
            buildSet {
                selectedCommentators.value.forEach { name ->
                    titleToIdMap[name]?.let { add(it) }
                }
            }
        if (skipEmit.value) {
            skipEmit.value = false
        } else {
            currentOnSelectionChange(ids)
        }
    }

    // Keep selection valid with optimization
    val availableSet =
        remember(availableCommentators) {
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
    val lineHeight: Float,
)

@Immutable
private data class CommentariesLayoutConfig(
    val selectedCommentators: ImmutableList<String>,
    val titleToIdMap: Map<String, Long>,
    val onCommentClick: (CommentaryWithText) -> Unit,
    val textSizes: AnimatedTextSizes,
    val fontFamily: FontFamily,
    val boldScale: Float,
    val highlightQuery: String,
    val showDiacritics: Boolean,
)

@Composable
private fun rememberCommentariesLayoutConfig(
    selectedCommentators: ImmutableList<String>,
    titleToIdMap: Map<String, Long>,
    textSizes: AnimatedTextSizes,
    findQueryText: String,
    showDiacritics: Boolean,
    onEvent: (BookContentEvent) -> Unit,
): CommentariesLayoutConfig {
    val windowInfo = LocalWindowInfo.current
    val commentaryFontCode by AppSettings.commentaryFontCodeFlow.collectAsState()
    val commentaryFontFamily = FontCatalog.familyFor(commentaryFontCode)
    val boldScaleForPlatform =
        remember(commentaryFontCode) {
            val lacksBold = commentaryFontCode in setOf("notoserifhebrew", "notorashihebrew", "frankruhllibre")
            if (PlatformInfo.isMacOS && lacksBold) 1.08f else 1.0f
        }

    return remember(
        selectedCommentators,
        titleToIdMap,
        textSizes,
        commentaryFontFamily,
        boldScaleForPlatform,
        findQueryText,
        showDiacritics,
    ) {
        CommentariesLayoutConfig(
            selectedCommentators = selectedCommentators,
            titleToIdMap = titleToIdMap,
            onCommentClick = { commentary ->
                val mods = windowInfo.keyboardModifiers
                if (mods.isCtrlPressed || mods.isMetaPressed) {
                    onEvent(
                        BookContentEvent.OpenCommentaryTarget(
                            bookId = commentary.link.targetBookId,
                            lineId = commentary.link.targetLineId,
                        ),
                    )
                }
            },
            textSizes = textSizes,
            fontFamily = commentaryFontFamily,
            boldScale = boldScaleForPlatform,
            highlightQuery = findQueryText,
            showDiacritics = showDiacritics,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommentatorsSidebarToggleButton(
    isVisible: Boolean,
    onToggle: () -> Unit,
) {
    val icon: ImageVector = if (isVisible) LayoutSidebarRight else LayoutSidebarRightOff
    val toggleText =
        if (isVisible) {
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
                tint = JewelTheme.globalColors.text.normal,
            )
        }
    }
}
