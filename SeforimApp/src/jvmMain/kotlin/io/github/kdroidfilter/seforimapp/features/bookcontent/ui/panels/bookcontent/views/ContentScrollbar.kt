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
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility.AlwaysVisible
import org.jetbrains.jewel.ui.theme.scrollbarStyle

/**
 * Content-aware vertical scrollbar for the book content pane.
 *
 * Works in pixel-space, using three exact inputs supplied by the caller:
 *  - [capacity] — chars per visual line, measured once by [androidx.compose.ui.text.TextMeasurer]
 *    against the current text area width, font family and font size. Deterministic: does not
 *    move during scroll, updates naturally on resize / font change.
 *  - [lineHeightPx] — exact line-height in px, derived from the font settings.
 *  - [paddingPerItemPx] — total vertical padding applied to every line's slot.
 *
 * The total content height becomes
 * `totalContentPx = totalVisualLines × lineHeightPx + N × paddingPerItemPx`, which matches
 * what Compose actually lays out. Thumb size = `viewport / totalContentPx`, thumb position
 * = `scrollPx / (totalContentPx − viewport)`. No per-frame sampling, no latch heuristics.
 *
 * [bookCharCounts] is the per-line raw char-count vector for the currently loaded book in
 * `lineIndex` order (the VM prefetches it once per book). The composable prefix-sums the
 * `max(1, ceil(charCount[i] / capacity))` mapping, binary-searches it on drag/jump, and
 * defers to [onScrollToLineIndex] when the target line lies outside the loaded pager window.
 *
 * Visual styling comes from [JewelTheme.scrollbarStyle] so the scrollbar blends in with
 * every other Jewel scrollbar in the app.
 */
@Composable
fun ContentScrollbar(
    listState: LazyListState,
    lazyPagingItems: LazyPagingItems<Line>,
    bookCharCounts: IntArray?,
    capacity: Int,
    lineHeightPx: Float,
    paddingPerItemPx: Float,
    onScrollToLineIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val counts = bookCharCounts
    if (counts == null || counts.isEmpty() || lazyPagingItems.itemCount == 0) return
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
        label = "scrollbar_thickness",
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
        label = "scrollbar_thumb_color",
    )
    val targetTrackColor = when {
        isOpaque -> if (isHovered) style.colors.trackOpaqueBackgroundHovered else style.colors.trackOpaqueBackground
        isExpanded -> style.colors.trackBackgroundExpanded
        else -> style.colors.trackBackground
    }
    val trackColor by animateColorAsState(
        targetValue = targetTrackColor,
        animationSpec = tween(visibility.trackColorAnimationDuration.inWholeMilliseconds.toInt(), easing = LinearEasing),
        label = "scrollbar_track_color",
    )

    // Total visual-line count over the whole book, used only for the **thumb size** so
    // long lines visually dominate short ones. Recomputed only on `counts` or `capacity`
    // changes.
    val totalVisualLines by remember(counts, capacity) {
        derivedStateOf {
            var acc = 0L
            for (c in counts) acc += visualLinesForCount(c, capacity)
            acc
        }
    }
    val itemCount = counts.size

    // Exact total content height Compose would lay out: `Σ (perItem[i] × lineHeightPx) +
    // N × paddingPerItemPx`. Stable during scroll — inputs are deterministic.
    val totalContentPx = totalVisualLines.toFloat() * lineHeightPx + itemCount * paddingPerItemPx

    // Thumb **size** is one-shot latched: sampled once on the first frame where every
    // input is valid, then frozen for the lifetime of this `counts`. Prevents the
    // initial jump from `capacity == 0` (all items counted as 1 line) → real capacity.
    // Also stores the hide/show decision at the same moment — a short book (content
    // fits) hides the scrollbar, matching every other scrollbar in the UI toolkit.
    var latchedSize by remember(counts) { mutableFloatStateOf(-1f) }
    var latchedHidden by remember(counts) { mutableStateOf(false) }
    LaunchedEffect(counts) {
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

    // Thumb **position** uses the book-wide line-index geometry (not the paged-window
    // line index): `position = (firstLineIdx + firstInnerOffset) / (N − visibleLines)`.
    // At scroll start `firstLineIdx = 0` → 0. At scroll end `firstLineIdx = N −
    // visibleLines` → 1. Numerator and denominator scale with the same `avgItemSize`
    // so the ratio reaches the boundaries exactly, no pinning required, no flicker.
    val position = computeBookPosition(listState, lazyPagingItems, counts.size).coerceIn(0f, 1f)

    val density = LocalDensity.current
    val minThumbHeightPx = with(density) { style.metrics.minThumbLength.toPx() }

    val displayPosition = dragRatio ?: position
    val thumbHeightPx = (size * trackHeightPx).coerceAtLeast(minThumbHeightPx)
    val travelPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
    val thumbTopPx = (displayPosition * travelPx).coerceIn(0f, travelPx)

    val travelPxState = rememberUpdatedState(travelPx)
    val displayPositionState = rememberUpdatedState(displayPosition)
    val listStateRef = rememberUpdatedState(listState)
    val pagingRef = rememberUpdatedState(lazyPagingItems)
    val bookLineCountRef = rememberUpdatedState(counts.size)
    val onScrollToLineIndexState = rememberUpdatedState(onScrollToLineIndex)

    // Throttled pager rebuild for out-of-window drag positions.
    val farDragFlow = remember {
        MutableSharedFlow<Int>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    }
    LaunchedEffect(farDragFlow) {
        farDragFlow.collect { lineIndex ->
            onScrollToLineIndexState.value(lineIndex)
            delay(FAR_DRAG_THROTTLE)
        }
    }

    // Convert a thumb ratio → target book line index via the same book-wide geometry
    // used for `position`. Snap locally when the target line is loaded in the pager;
    // otherwise defer to the VM-backed callback to rebuild the pager around the target.
    val applyTarget = remember {
        fun(thumbRatio: Float, viaDrag: Boolean) {
            val ls = listStateRef.value
            val info = ls.layoutInfo
            val pagingItemCount = pagingRef.value.itemCount
            val visible = info.visibleItemsInfo.filter { it.index in 0 until pagingItemCount }
            val total = bookLineCountRef.value
            if (total == 0 || visible.isEmpty()) return
            val avgItemSize = visible.sumOf { it.size }.toFloat() / visible.size
            if (avgItemSize <= 0f) return
            val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
            val visibleCount = (viewport / avgItemSize).toInt().coerceAtLeast(1)
            val maxIdx = (total - visibleCount).coerceAtLeast(1)
            val targetLineIndex = (thumbRatio * maxIdx).toInt().coerceIn(0, total - 1)
            val snapshot = pagingRef.value.itemSnapshotList.items
            val localIndex = snapshot.indexOfFirst { it.lineIndex == targetLineIndex }
            if (localIndex >= 0) {
                ls.requestScrollToItem(localIndex, 0)
                return
            }
            if (viaDrag) {
                farDragFlow.tryEmit(targetLineIndex)
            } else {
                onScrollToLineIndexState.value(targetLineIndex)
            }
        }
    }

    // Convergence: unpin the thumb once the live scroll catches up to the target.
    LaunchedEffect(dragRatio, isDragging, position) {
        val target = dragRatio ?: return@LaunchedEffect
        if (isDragging) return@LaunchedEffect
        if (abs(position - target) <= 0.005f) dragRatio = null
    }
    LaunchedEffect(dragRatio, isDragging) {
        if (isDragging || dragRatio == null) return@LaunchedEffect
        delay(PENDING_JUMP_TIMEOUT)
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
                    applyTarget(thumbRatio, /* viaDrag = */ false)
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
                        applyTarget(newRatio, /* viaDrag = */ true)
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

private val FAR_DRAG_THROTTLE = 50.milliseconds
private val PENDING_JUMP_TIMEOUT = 1500.milliseconds

private fun visualLinesForCount(charCount: Int, capacity: Int): Int {
    if (capacity <= 0) return 1
    if (charCount <= 0) return 1
    return max(1, ceil(charCount.toDouble() / capacity).toInt())
}

/**
 * Book-wide thumb position in `[0, 1]`, using the same avg-item geometry as the standard
 * Compose scrollbar adapter — but indexed on **book line number** rather than paging-
 * window index, so it reflects progress through the whole book, not just the loaded
 * page. Numerator and denominator both scale with `avgItemSize` so the ratio reaches 0
 * and 1 exactly at the book's scroll boundaries. No boundary pinning, no flicker.
 */
private fun computeBookPosition(
    listState: LazyListState,
    lazyPagingItems: LazyPagingItems<Line>,
    bookLineCount: Int,
): Float {
    if (bookLineCount == 0) return 0f
    val itemCount = lazyPagingItems.itemCount
    if (itemCount == 0) return 0f
    val info = listState.layoutInfo
    // Guard against paging prepends/appends where `visibleItemsInfo` briefly contains
    // indices beyond the snapshot size — `peek()` would throw `IndexOutOfBoundsException`.
    val visible = info.visibleItemsInfo.filter { it.index in 0 until itemCount }
    if (visible.isEmpty()) return 0f
    val firstInfo = visible.first()
    val firstLine = lazyPagingItems.peek(firstInfo.index) ?: return 0f
    val firstLineIdx = firstLine.lineIndex.coerceAtLeast(0)
    val firstSize = firstInfo.size.coerceAtLeast(1)
    val firstInnerOffset = ((-firstInfo.offset).toFloat() / firstSize).coerceIn(0f, 1f)
    val avgItemSize = visible.sumOf { it.size }.toFloat() / visible.size
    if (avgItemSize <= 0f) return 0f
    val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
    val visibleCount = (viewport / avgItemSize).coerceAtLeast(1f)
    val maxProgress = (bookLineCount - visibleCount).coerceAtLeast(1f)
    return ((firstLineIdx + firstInnerOffset) / maxProgress)
}
