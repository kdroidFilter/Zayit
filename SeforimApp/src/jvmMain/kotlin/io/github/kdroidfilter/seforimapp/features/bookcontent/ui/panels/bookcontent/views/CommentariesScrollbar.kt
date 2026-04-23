package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.paging.compose.LazyPagingItems
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility.AlwaysVisible
import org.jetbrains.jewel.ui.theme.scrollbarStyle

/**
 * Content-aware vertical scrollbar for a paginated list of [CommentaryWithText].
 *
 * Works in pixel-space, using three exact inputs supplied by the caller:
 *  - [capacity] — chars per visual line, measured once by [androidx.compose.ui.text.TextMeasurer]
 *    against the current text area width, font family and font size. Deterministic: does not
 *    move during scroll, updates naturally on resize / font change.
 *  - [lineHeightPx] — exact line-height in px, derived from the font settings (`fontSize ×
 *    lineHeight multiplier`). The per-visual-line pixel cost in Compose's layout.
 *  - [paddingPerItemPx] — total vertical padding applied to every item by its container
 *    (e.g. `padding(vertical = 8.dp)` on the Column in each item → 16 dp total).
 *
 * The total content height becomes
 * `totalContentPx = totalVisualLines × lineHeightPx + N × paddingPerItemPx`, which matches
 * what Compose actually lays out. Thumb size = `viewport / totalContentPx`, thumb position
 * = `scrollPx / (totalContentPx − viewport)`. No per-frame sampling, no latch heuristics.
 *
 * Visual styling — colors, thumb corner radius, track thickness, hover expand, fade
 * durations — is read from [JewelTheme.scrollbarStyle] so the scrollbar stays identical
 * to every other Jewel scrollbar in the app.
 */
@Composable
fun CommentariesScrollbar(
    listState: LazyListState,
    lazyPagingItems: LazyPagingItems<CommentaryWithText>,
    allCharCounts: List<Int>,
    capacity: Int,
    lineHeightPx: Float,
    paddingPerItemPx: Float,
    modifier: Modifier = Modifier,
) {
    if (lazyPagingItems.itemCount == 0 || allCharCounts.isEmpty()) return
    if (capacity <= 0 || lineHeightPx <= 0f) return

    val style = JewelTheme.scrollbarStyle
    val visibility = style.scrollbarVisibility
    val isOpaque = visibility is AlwaysVisible

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    var trackHeightPx by remember { mutableIntStateOf(0) }
    var dragRatio by remember { mutableStateOf<Float?>(null) }
    var dragStartRatio by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val isScrolling by remember(listState) {
        derivedStateOf { listState.isScrollInProgress || dragRatio != null }
    }
    val isExpanded = isHovered || dragRatio != null
    val isActive = isOpaque || isScrolling || isHovered

    var showScrollbar by remember { mutableStateOf(isOpaque) }
    LaunchedEffect(isActive) {
        if (isActive) {
            showScrollbar = true
        } else {
            delay(visibility.lingerDuration)
            showScrollbar = false
        }
    }

    val animatedThickness by animateDpAsState(
        targetValue = if (isExpanded) visibility.trackThicknessExpanded else visibility.trackThickness,
        animationSpec = tween(visibility.expandAnimationDuration.inWholeMilliseconds.toInt(), easing = LinearEasing),
        label = "commentaries_scrollbar_thickness",
    )
    val targetThumbColor = when {
        isOpaque && isHovered -> style.colors.thumbOpaqueBackgroundHovered
        isOpaque -> style.colors.thumbOpaqueBackground
        showScrollbar && (isHovered || dragRatio != null) -> style.colors.thumbBackgroundActive
        showScrollbar -> style.colors.thumbBackground
        else -> style.colors.thumbBackground.copy(alpha = 0f)
    }
    val thumbColor by animateColorAsState(
        targetValue = targetThumbColor,
        animationSpec = tween(visibility.thumbColorAnimationDuration.inWholeMilliseconds.toInt(), easing = LinearEasing),
        label = "commentaries_scrollbar_thumb_color",
    )
    val targetTrackColor = when {
        isOpaque -> if (isHovered) style.colors.trackOpaqueBackgroundHovered else style.colors.trackOpaqueBackground
        isExpanded -> style.colors.trackBackgroundExpanded
        else -> style.colors.trackBackground
    }
    val trackColor by animateColorAsState(
        targetValue = targetTrackColor,
        animationSpec = tween(visibility.trackColorAnimationDuration.inWholeMilliseconds.toInt(), easing = LinearEasing),
        label = "commentaries_scrollbar_track_color",
    )

    // Total visual-line count over all items: `Σ ceil(charCount[i] / capacity)`. Used
    // only for the **thumb size** — position is derived separately from LazyListState's
    // item geometry, which is boundary-accurate.
    val totalVisualLines by remember(allCharCounts, capacity) {
        derivedStateOf {
            var acc = 0L
            for (c in allCharCounts) acc += visualLinesOf(c, capacity)
            acc
        }
    }
    val itemCount = allCharCounts.size

    // Exact total content height Compose will lay out: `Σ (perItem[i] × lineHeightPx) +
    // N × paddingPerItemPx`. Stable as long as `capacity`, `lineHeightPx` and
    // `paddingPerItemPx` are — which they are, since they come from deterministic
    // inputs (TextMeasurer, font settings, layout constants).
    val totalContentPx = totalVisualLines.toFloat() * lineHeightPx + itemCount * paddingPerItemPx

    // Thumb **size** is one-shot latched: sampled once on the first frame where every
    // input (viewport, capacity-measured `totalContentPx`, etc.) is valid, then frozen
    // for the lifetime of this `allCharCounts`. Prevents the initial jump from
    // `capacity == 0` (all items counted as 1 line) → real capacity. Also stores the
    // hide/show decision at the same moment — a short list (content fits) hides the
    // scrollbar entirely, matching every other scrollbar in the UI toolkit.
    var latchedSize by remember(allCharCounts) { mutableFloatStateOf(-1f) }
    var latchedHidden by remember(allCharCounts) { mutableStateOf(false) }
    LaunchedEffect(allCharCounts) {
        snapshotFlow {
            val viewport = (listState.layoutInfo.viewportEndOffset -
                listState.layoutInfo.viewportStartOffset).toFloat()
            if (viewport > 0f && capacity > 0 && lineHeightPx > 0f && totalContentPx > 0f) {
                viewport to totalContentPx
            } else null
        }.filterNotNull().first().let { (viewport, total) ->
            latchedSize = (viewport / total).coerceIn(0f, 1f)
            latchedHidden = total <= viewport
        }
    }
    if (latchedSize < 0f) return
    if (latchedHidden) return
    val size = latchedSize

    // Thumb **position** uses Compose's native avg-item scroll geometry, not the
    // pixel-space estimate. Internally consistent: both `scrollOffsetAvg` and
    // `contentSizeAvg` scale with the same `avgItemSize`, so `position` reaches 0 at
    // scroll-start and 1 at scroll-end **exactly** — no boundary pinning required,
    // no flicker. The avg is recomputed per frame from visible items, so it stays
    // current as scroll progresses.
    val position = computeAvgScrollPosition(listState).coerceIn(0f, 1f)

    val density = LocalDensity.current
    val minThumbHeightPx = with(density) { style.metrics.minThumbLength.toPx() }

    val displayPosition = dragRatio ?: position
    val thumbHeightPx = (size * trackHeightPx).coerceAtLeast(minThumbHeightPx)
    val travelPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
    val thumbTopPx = (displayPosition * travelPx).coerceIn(0f, travelPx)

    val travelPxState = rememberUpdatedState(travelPx)
    val displayPositionState = rememberUpdatedState(displayPosition)
    val listStateRef = rememberUpdatedState(listState)

    // Convert a thumb ratio back to a target item index via the same avg-based geometry
    // used for `position` — so that dragging the thumb is the inverse of reading it.
    val applyTarget = remember {
        fun(thumbRatio: Float) {
            val ls = listStateRef.value
            val info = ls.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) return
            val visible = info.visibleItemsInfo.filter { it.index in 0 until total }
            if (visible.isEmpty()) return
            val avgItemSize = visible.sumOf { it.size }.toFloat() / visible.size
            if (avgItemSize <= 0f) return
            val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
            val visibleCount = (viewport / avgItemSize).toInt().coerceAtLeast(1)
            val maxIdx = (total - visibleCount).coerceAtLeast(1)
            val targetIdx = (thumbRatio * maxIdx).toInt().coerceIn(0, total - 1)
            ls.requestScrollToItem(targetIdx, 0)
        }
    }

    // Convergence: unpin the thumb once the live scroll catches up to the target ratio.
    LaunchedEffect(dragRatio, isDragging, position) {
        val target = dragRatio ?: return@LaunchedEffect
        if (isDragging) return@LaunchedEffect
        if (abs(position - target) <= 0.005f) dragRatio = null
    }
    LaunchedEffect(dragRatio, isDragging) {
        if (isDragging || dragRatio == null) return@LaunchedEffect
        delay(1500)
        dragRatio = null
    }

    Box(
        modifier = modifier
            .width(animatedThickness)
            .fillMaxHeight()
            .hoverable(interactionSource)
            .onSizeChanged { trackHeightPx = it.height }
            .background(trackColor)
            .pointerInput(trackHeightPx) {
                detectTapGestures(onTap = { offset ->
                    if (trackHeightPx <= 0) return@detectTapGestures
                    val thumbRatio = (offset.y / trackHeightPx).coerceIn(0f, 1f)
                    dragRatio = thumbRatio
                    applyTarget(thumbRatio)
                })
            },
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(0, thumbTopPx.toInt()) }
                .fillMaxWidth()
                .height(with(density) { thumbHeightPx.toDp() })
                .clip(RoundedCornerShape(style.metrics.thumbCornerSize))
                .background(thumbColor)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        val travel = travelPxState.value
                        if (travel <= 0f) return@rememberDraggableState
                        val current = dragRatio ?: dragStartRatio
                        val newRatio = (current + delta / travel).coerceIn(0f, 1f)
                        dragRatio = newRatio
                        applyTarget(newRatio)
                    },
                    onDragStarted = {
                        isDragging = true
                        val start = displayPositionState.value
                        dragStartRatio = start
                        dragRatio = start
                    },
                    onDragStopped = { isDragging = false },
                ),
        )
    }
}

private fun visualLinesOf(charCount: Int, capacity: Int): Int {
    if (capacity <= 0) return 1
    if (charCount <= 0) return 1
    return max(1, ceil(charCount.toDouble() / capacity).toInt())
}

/**
 * Thumb position in `[0, 1]`, computed from `LazyListState` the same way the standard
 * Compose scrollbar adapter does: `(firstIdx × avgItemSize + firstOffset) / (totalCount
 * × avgItemSize − viewport)`. Because both numerator and denominator scale with the same
 * `avgItemSize` (averaged over currently visible items), the ratio **reaches 0 and 1
 * exactly** at the scroll boundaries — regardless of per-item size variance. No boundary
 * pinning, no flicker. Only appproximate between boundaries, but that's acceptable for a
 * progress indicator.
 */
private fun computeAvgScrollPosition(listState: LazyListState): Float {
    val info = listState.layoutInfo
    val total = info.totalItemsCount
    if (total == 0) return 0f
    // Guard against paging prepends/appends where `visibleItemsInfo` briefly contains
    // indices beyond the paging snapshot size.
    val visible = info.visibleItemsInfo.filter { it.index in 0 until total }
    if (visible.isEmpty()) return 0f
    val avgItemSize = visible.sumOf { it.size }.toFloat() / visible.size
    if (avgItemSize <= 0f) return 0f
    val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
    val contentSize = total * avgItemSize
    val maxScroll = (contentSize - viewport).coerceAtLeast(1f)
    val scrollOffset = listState.firstVisibleItemIndex * avgItemSize +
        listState.firstVisibleItemScrollOffset
    return scrollOffset / maxScroll
}
