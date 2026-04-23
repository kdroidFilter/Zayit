package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.PagingData
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
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.Providers
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.rememberSplitPaneState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import seforimapp.seforimapp.generated.resources.*
import kotlin.time.Duration.Companion.milliseconds

private val SCROLL_DEBOUNCE = 100.milliseconds

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
    val showCommentatorsList = contentState.isCommentatorsListVisible

    Column(modifier = Modifier.fillMaxSize().hoverable(paneInteractionSource)) {
        // Header
        PaneHeader(
            label = stringResource(Res.string.commentaries),
            interactionSource = paneInteractionSource,
            onHide = { onEvent(BookContentEvent.ToggleCommentaries) },
            actions = {
                CommentatorsSidebarToggleButton(
                    isVisible = showCommentatorsList,
                    onToggle = { onEvent(BookContentEvent.ToggleCommentatorsList) },
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
                        selectedCommentators.value =
                            if (checked) {
                                selectedCommentators.value + name
                            } else {
                                selectedCommentators.value - name
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
                selection = LineSelection.Single(selectedLineId),
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
                        selectedCommentators.value =
                            if (checked) {
                                selectedCommentators.value + name
                            } else {
                                selectedCommentators.value - name
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
            CommentariesDisplay(
                selectedCommentators = selectedInDisplayOrder,
                titleToIdMap = titleToIdMap,
                selection = LineSelection.Multi(selectedLineIds),
                uiState = uiState,
                onEvent = onEvent,
                textSizes = textSizes,
                findQueryText = findQueryText,
                showDiacritics = showDiacritics,
            )
        },
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
                .debounce(SCROLL_DEBOUNCE)
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
    selection: LineSelection,
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

    CommentatorsGrid(
        config = layoutConfig,
        selection = selection,
        uiState = uiState,
        onEvent = onEvent,
    )
}

@Composable
private fun CommentatorsGrid(
    config: CommentariesLayoutConfig,
    selection: LineSelection,
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
) {
    val providers = uiState.providers ?: return
    CommentatorsGridScaffold(
        config = config,
        initialPage = uiState.content.commentariesPageIndex,
        onPageChange = { page -> onEvent(BookContentEvent.CommentariesPageChanged(page)) },
    ) { commentatorId ->
        val pagerFlow =
            remember(selection, commentatorId) {
                providers.buildCommentariesPagerFor(selection, commentatorId)
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
        )
    }
}

// Grid capacity thresholds at the reference commentary font size ([REFERENCE_TEXT_SIZE]).
// At runtime the effective minimums scale with the user's chosen font size so the grid
// widens when the text is enlarged and tightens when it shrinks.
private val MIN_CELL_WIDTH_AT_REF = 320.dp
private val MIN_CELL_HEIGHT_AT_REF = 150.dp
private const val MAX_ROWS_PER_PAGE = 2

// Default commentTextSize as produced by [rememberAnimatedTextSettings]: the global
// DEFAULT_TEXT_SIZE (16) multiplied by the 0.875 commentary ratio.
private const val REFERENCE_TEXT_SIZE = 14f

// Floor for the downward scaling — prevents absurdly narrow cells at very small fonts.
private const val MIN_TEXT_SCALE = 0.55f

// Amplifies the deviation below the reference size so the user feels a meaningful shift
// when zooming out (the raw commentTextSize range is narrow — ~12.25 to 28 — so a
// direct proportional scale barely moves). Only applied when rawScale < 1.
private const val DOWNWARD_AMPLIFIER = 3f

// Below this pane width the grid is allowed to collapse to 1×1 (pane truly too narrow
// to split). Anything wider keeps at least 2 cells per page so the layout never feels
// wasted.
private val NARROW_PANE_WIDTH = 420.dp

internal data class CommentariesGridCapacity(
    val cols: Int,
    val rows: Int,
    val perPage: Int,
)

internal data class CommentariesPageLayout(
    val rows: Int,
    val colsPerRow: Int,
)

/**
 * Derive the commentaries grid capacity from the pane size and the user's commentary
 * font size. Pure helper — extracted so it can be unit-tested without Compose.
 */
internal fun computeCommentariesGridCapacity(
    paneWidthDp: Float,
    paneHeightDp: Float,
    commentTextSize: Float,
): CommentariesGridCapacity {
    val rawScale = commentTextSize / REFERENCE_TEXT_SIZE
    val textScale =
        if (rawScale >= 1f) {
            1f
        } else {
            (1f - (1f - rawScale) * DOWNWARD_AMPLIFIER).coerceAtLeast(MIN_TEXT_SCALE)
        }
    val minWidth = MIN_CELL_WIDTH_AT_REF.value * textScale
    val minHeight = MIN_CELL_HEIGHT_AT_REF.value * textScale
    var cols = maxOf(1, (paneWidthDp / minWidth).toInt())
    var rows = maxOf(1, (paneHeightDp / minHeight).toInt()).coerceAtMost(MAX_ROWS_PER_PAGE)
    if (cols * rows < 2 && paneWidthDp >= NARROW_PANE_WIDTH.value) {
        if (paneWidthDp >= paneHeightDp) cols = 2 else rows = 2
    }
    return CommentariesGridCapacity(cols = cols, rows = rows, perPage = cols * rows)
}

/**
 * Spread [itemCount] commentators across the minimal number of rows bounded by [cols].
 * Partial pages are rebalanced so the actual items fill the row width instead of
 * leaving empty slots (e.g. 4 items in a 5-cap page → 2 rows of 2).
 */
internal fun computeCommentariesPageLayout(
    itemCount: Int,
    cols: Int,
): CommentariesPageLayout {
    if (itemCount <= 0 || cols <= 0) return CommentariesPageLayout(rows = 0, colsPerRow = 0)
    val rowsNeeded = ((itemCount + cols - 1) / cols).coerceAtLeast(1)
    val colsPerRow = ((itemCount + rowsNeeded - 1) / rowsNeeded).coerceAtLeast(1)
    return CommentariesPageLayout(rows = rowsNeeded, colsPerRow = colsPerRow)
}

/**
 * Shared grid scaffolding for single-line and multi-line commentaries. The [column] slot
 * renders one commentator column given its bookId.
 *
 * The grid capacity (cols × rows) is derived from the available space using
 * [MIN_CELL_WIDTH_AT_REF] and [MIN_CELL_HEIGHT_AT_REF]; any overflow spills into additional
 * vertical pager pages. The anchor
 * commentator (top-left of the current page) is preserved across resizes so the user keeps
 * their reading context when the pager re-paginates.
 */
@Composable
private fun CommentatorsGridScaffold(
    config: CommentariesLayoutConfig,
    initialPage: Int,
    onPageChange: (Int) -> Unit,
    column: @Composable (commentatorId: Long) -> Unit,
) {
    val selected = config.selectedCommentators
    if (selected.isEmpty()) return

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val capacity =
            computeCommentariesGridCapacity(
                paneWidthDp = maxWidth.value,
                paneHeightDp = maxHeight.value,
                commentTextSize = config.textSizes.commentTextSize,
            )
        val cols = capacity.cols
        val perPage = capacity.perPage
        val pages =
            remember(selected, perPage) {
                selected.chunked(perPage)
            }
        val pagerState =
            rememberPagerState(
                initialPage = initialPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
                pageCount = { pages.size },
            )

        val currentOnPageChange by rememberUpdatedState(onPageChange)
        LaunchedEffect(pagerState.settledPage) {
            currentOnPageChange(pagerState.settledPage)
        }

        // Anchor = commentator the user is focused on. Used to follow:
        //   - page navigation (anchor tracks the first item of the current page)
        //   - selection additions (anchor jumps to the newly added commentator)
        //   - repagination from resize (anchor stays put so the visible commentator remains)
        val anchorName = remember { mutableStateOf(selected.firstOrNull()) }

        // When the user navigates between pages, update the anchor to the first commentator
        // of the new page. Keyed on [settledPage] (not [currentPage]) so that mid-animation
        // updates do not overwrite the target anchor and abort an in-flight scroll.
        LaunchedEffect(pagerState.settledPage) {
            pages.getOrNull(pagerState.settledPage)?.firstOrNull()?.let { anchorName.value = it }
        }

        // Detect newly added commentators and promote the last one as the anchor so the
        // pager scrolls to the page containing it. Skips the first composition so the
        // restored page from saved state is preserved. The same name also flags a
        // short-lived highlight on the new cell so the user can locate it visually.
        val previousSelected = remember { mutableStateOf<List<String>?>(null) }
        val recentlyAdded = remember { mutableStateOf<String?>(null) }
        LaunchedEffect(selected) {
            val prev = previousSelected.value
            if (prev != null) {
                val addedLast = selected.lastOrNull { it !in prev }
                if (addedLast != null) {
                    anchorName.value = addedLast
                    recentlyAdded.value = addedLast
                }
            }
            previousSelected.value = selected.toList()
        }
        LaunchedEffect(recentlyAdded.value) {
            if (recentlyAdded.value != null) {
                // Slightly longer than the underline draw + hold + fade-out cycle so the
                // header animation completes before we clear the flag.
                delay(2000)
                recentlyAdded.value = null
            }
        }

        // Scroll the pager to the anchor's page whenever pagination changes (resize,
        // selection update) or the anchor itself moves (new commentator picked).
        LaunchedEffect(perPage, pages, anchorName.value) {
            val anchor = anchorName.value ?: return@LaunchedEffect
            val idx = selected.indexOf(anchor)
            if (idx < 0) return@LaunchedEffect
            val target = (idx / perPage).coerceAtMost((pages.size - 1).coerceAtLeast(0))
            if (target != pagerState.currentPage) {
                pagerState.animateScrollToPage(target)
            }
        }

        Row(modifier = Modifier.fillMaxSize()) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                beyondViewportPageCount = 0,
                // Navigation is driven solely by indicator clicks to avoid swallowing the
                // LazyColumn scrolls rendered inside each commentator cell.
                userScrollEnabled = false,
            ) { pageIdx ->
                CommentatorPageGrid(
                    names = pages[pageIdx],
                    cols = cols,
                    config = config,
                    recentlyAdded = recentlyAdded.value,
                    column = column,
                )
            }
            if (pages.size > 1) {
                VerticalPagerIndicator(pagerState = pagerState, pageCount = pages.size)
            }
        }
    }
}

/**
 * Lays out the commentators of a single pager page. The grid is rebalanced so that
 * partial pages fill the full area: instead of reserving empty slots for the unused
 * capacity, the actual commentators are spread evenly across as few rows as needed
 * (bounded by [cols]) and each cell expands via `weight(1f)` to consume the remaining
 * space.
 */
@Composable
private fun CommentatorPageGrid(
    names: List<String>,
    cols: Int,
    config: CommentariesLayoutConfig,
    recentlyAdded: String?,
    column: @Composable (commentatorId: Long) -> Unit,
) {
    if (names.isEmpty()) return
    val layout = computeCommentariesPageLayout(itemCount = names.size, cols = cols)
    val rowsChunks = names.chunked(layout.colsPerRow)
    Column(modifier = Modifier.fillMaxSize()) {
        rowsChunks.forEach { rowNames ->
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                rowNames.forEach { name ->
                    val id = config.titleToIdMap[name] ?: return@forEach
                    CommentatorCell(
                        name = name,
                        commentTextSize = config.textSizes.commentTextSize,
                        isRecentlyAdded = name == recentlyAdded,
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 4.dp),
                    ) {
                        column(id)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentatorCell(
    name: String,
    commentTextSize: Float,
    isRecentlyAdded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier) {
        CommentatorHeader(
            commentator = name,
            commentTextSize = commentTextSize,
            isRecentlyAdded = isRecentlyAdded,
        )
        content()
    }
}

@Composable
private fun VerticalPagerIndicator(
    pagerState: PagerState,
    pageCount: Int,
) {
    val activeColor = JewelTheme.globalColors.text.normal
    val inactiveColor = JewelTheme.globalColors.borders.normal
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier.fillMaxHeight().width(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        repeat(pageCount) { i ->
            val isActive = pagerState.currentPage == i
            // Clickable hit-box padded outwards so the dot itself stays small while the
            // click target remains comfortable.
            Box(
                modifier =
                    Modifier
                        .padding(vertical = 2.dp)
                        .size(20.dp)
                        .clickable {
                            if (!isActive) {
                                scope.launch { pagerState.animateScrollToPage(i) }
                            }
                        },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(if (isActive) 10.dp else 7.dp)
                            .background(
                                color = if (isActive) activeColor else inactiveColor,
                                shape = CircleShape,
                            ),
                )
            }
        }
    }
}

/**
 * Paged list of [CommentaryItem]s for one commentator, wrapped in a [SafeSelectionContainer].
 * Shared by single-line and multi-line views.
 *
 * IMPORTANT — [pagerFlow] identity drives the one-shot scroll restore. Callers MUST build it via
 * `remember(key1, key2, ...) { providers.buildPagerFor(...) }` so that the same logical source
 * yields the same [Flow] reference across recompositions. Passing a freshly-constructed Flow on
 * every recomposition (e.g. inline `providers.buildPagerFor(...).map { ... }`) will reset the
 * one-shot state on each frame and fight the user's scroll position.
 */
@OptIn(FlowPreview::class)
@Composable
private fun CommentariesPagedList(
    pagerFlow: Flow<PagingData<CommentaryWithText>>,
    initialIndex: Int,
    initialOffset: Int,
    onScroll: (Int, Int) -> Unit,
    config: CommentariesLayoutConfig,
) {
    val currentOnScroll by rememberUpdatedState(onScroll)
    val lazyPagingItems = pagerFlow.collectAsLazyPagingItems()

    val listState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = initialIndex,
            initialFirstVisibleItemScrollOffset = initialOffset,
        )

    var hasRestored by remember(pagerFlow) { mutableStateOf(false) }
    LaunchedEffect(pagerFlow, lazyPagingItems.loadState.refresh, initialIndex, initialOffset) {
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
            .debounce(SCROLL_DEBOUNCE)
            .collect { (i, o) -> currentOnScroll(i, o) }
    }

    SafeSelectionContainer(modifier = Modifier.fillMaxSize()) {
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
    isRecentlyAdded: Boolean = false,
) {
    // Progressive underline: the stroke grows from the reading-side edge (LTR → left to
    // right, RTL → right to left) while fading in, holds briefly, then fades out.
    val progress = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(isRecentlyAdded) {
        if (isRecentlyAdded) {
            progress.snapTo(0f)
            alpha.snapTo(1f)
            progress.animateTo(1f, tween(durationMillis = 450, easing = FastOutSlowInEasing))
            delay(700)
            alpha.animateTo(0f, tween(durationMillis = 650))
        } else if (alpha.value > 0f) {
            alpha.animateTo(0f, tween(durationMillis = 250))
        }
    }
    val highlightColor = JewelTheme.globalColors.outlines.focused
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

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
            modifier =
                Modifier.drawWithContent {
                    drawContent()
                    val a = alpha.value
                    val p = progress.value
                    if (a <= 0f || p <= 0f) return@drawWithContent
                    val y = size.height - 1.dp.toPx()
                    val thickness = 1.5.dp.toPx()
                    val span = size.width * p
                    val startX = if (isRtl) size.width - span else 0f
                    val endX = if (isRtl) size.width else span
                    drawLine(
                        color = highlightColor.copy(alpha = a),
                        start = Offset(startX, y),
                        end = Offset(endX, y),
                        strokeWidth = thickness,
                    )
                },
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

/**
 * Source of lines feeding a commentary view: either a single selected line or a manual
 * multi-selection. Chooses which provider pager to build in [Providers.buildCommentariesPagerFor].
 */
private sealed interface LineSelection {
    data class Single(
        val lineId: Long,
    ) : LineSelection

    data class Multi(
        val lineIds: ImmutableList<Long>,
    ) : LineSelection
}

private fun Providers.buildCommentariesPagerFor(
    selection: LineSelection,
    commentatorId: Long,
): Flow<PagingData<CommentaryWithText>> =
    when (selection) {
        is LineSelection.Single -> buildCommentariesPagerFor(selection.lineId, commentatorId)
        is LineSelection.Multi -> buildCommentariesPagerForLines(selection.lineIds, commentatorId)
    }

/**
 * UI + callbacks shared between the single-line and multi-line commentary views.
 *
 * Deliberately does not carry the line id(s) being displayed — the pager is built by the
 * caller and injected via the [CommentatorsGridScaffold] column slot, so this config stays
 * agnostic of single vs multi-line mode.
 */
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
