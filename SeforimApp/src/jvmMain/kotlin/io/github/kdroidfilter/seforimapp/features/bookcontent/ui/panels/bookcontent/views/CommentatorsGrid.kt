package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

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
 * vertical pager pages. The anchor commentator (top-left of the current page) is preserved
 * across resizes so the user keeps their reading context when the pager re-paginates.
 */
@Composable
internal fun CommentatorsGridScaffold(
    config: CommentariesLayoutConfig,
    initialPage: Int,
    onPageChange: (Int) -> Unit,
    onFlushPersist: () -> Unit,
    column: @Composable (commentatorId: Long) -> Unit,
) {
    val selected = config.selectedCommentators
    if (selected.isEmpty()) return

    // Page-swipe events are coalesced via save = false upstream so rapid navigation
    // doesn't hammer the disk. The final in-memory state is flushed here when the
    // scaffold leaves composition (tab close, book change, pane hidden).
    val currentOnFlushPersist by rememberUpdatedState(onFlushPersist)
    DisposableEffect(Unit) {
        onDispose { currentOnFlushPersist() }
    }

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

        // Guard against out-of-bounds [currentPage] when the page count shrinks (the user
        // deselects enough commentators to collapse pages, or the pane widens and packs
        // the remaining items into fewer pages). Runs before the anchor effect so the
        // pager is always in a valid state even when the anchor no longer exists.
        LaunchedEffect(pages.size) {
            val maxValid = (pages.size - 1).coerceAtLeast(0)
            if (pagerState.currentPage > maxValid) {
                pagerState.scrollToPage(maxValid)
            }
        }

        val currentOnPageChange by rememberUpdatedState(onPageChange)

        // Anchor = commentator the user is focused on. Used to follow:
        //   - page navigation (anchor tracks the first item of the current page)
        //   - selection additions (anchor jumps to the newly added commentator)
        //   - repagination from resize (anchor stays put so the visible commentator remains)
        val anchorName = remember { mutableStateOf(selected.firstOrNull()) }

        // Keyed on [settledPage] (not [currentPage]) so that mid-animation updates do not
        // overwrite the target anchor and abort an in-flight scroll. Notifies the caller
        // and re-anchors to the first commentator of the new page in a single effect.
        LaunchedEffect(pagerState.settledPage) {
            val settled = pagerState.settledPage
            currentOnPageChange(settled)
            pages.getOrNull(settled)?.firstOrNull()?.let { anchorName.value = it }
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
                        onTitleClick = { config.onOpenCommentatorBook(id) },
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
    onTitleClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier) {
        CommentatorHeader(
            commentator = name,
            commentTextSize = commentTextSize,
            isRecentlyAdded = isRecentlyAdded,
            onClick = onTitleClick,
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

@Composable
private fun CommentatorHeader(
    commentator: String,
    commentTextSize: Float,
    isRecentlyAdded: Boolean = false,
    onClick: () -> Unit = {},
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
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .hoverable(interactionSource)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ).padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = commentator,
            fontWeight = FontWeight.Bold,
            fontSize = (commentTextSize * 1.1f).sp,
            textAlign = TextAlign.Center,
            textDecoration = if (isHovered) TextDecoration.Underline else TextDecoration.None,
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
