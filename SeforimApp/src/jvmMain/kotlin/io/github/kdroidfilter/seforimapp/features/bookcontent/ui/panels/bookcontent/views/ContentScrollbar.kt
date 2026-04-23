package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

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
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.theme.scrollbarStyle
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

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
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    var trackHeightPx by remember { mutableIntStateOf(0) }
    var dragRatio by remember { mutableStateOf<Float?>(null) }
    var dragStartRatio by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val visuals =
        rememberScrollbarVisuals(
            listState = listState,
            isHovered = isHovered,
            dragRatio = dragRatio,
            label = "scrollbar",
        )

    // Pixel-space prefix sum: `cumPx[i]` is the total content height of lines `[0, i)`,
    // exact match for what Compose lays out. Used both for the thumb **size** (last
    // entry = `totalContentPx`) and for converting a thumb ratio → target line index
    // in O(log N) on drag/jump (see `applyTarget`). Accumulates in Double to avoid
    // drift over large books, then snapshots to Long.
    val cumPx by remember(counts, capacity, lineHeightPx, paddingPerItemPx) {
        derivedStateOf {
            val n = counts.size
            val arr = LongArray(n + 1)
            var acc = 0.0
            for (i in 0 until n) {
                arr[i] = acc.toLong()
                acc += visualLinesForCount(counts[i], capacity) * lineHeightPx.toDouble() + paddingPerItemPx
            }
            arr[n] = acc.toLong()
            arr
        }
    }
    val itemCount = counts.size
    val totalContentPx = cumPx[itemCount].toFloat()

    val latched =
        rememberLatchedThumbSize(
            latchKey = counts,
            capacity = capacity,
            lineHeightPx = lineHeightPx,
            totalContentPx = totalContentPx,
            listState = listState,
        ) ?: return
    if (latched.hidden) return
    val size = latched.size

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
    val cumPxRef = rememberUpdatedState(cumPx)
    val totalContentPxRef = rememberUpdatedState(totalContentPx)
    val onScrollToLineIndexState = rememberUpdatedState(onScrollToLineIndex)

    // Far-drag pager rebuild emissions. During a drag, every delta that lands outside
    // the loaded pager window emits a target into this flow; the collector throttles
    // emissions to one rebuild every [FAR_DRAG_THROTTLE], with `DROP_OLDEST` so only
    // the latest target survives. This keeps the displayed text catching up to fast
    // drags (~5 Hz) without spamming `buildLinesPager` on every frame. The latest
    // target is also stashed in [pendingFarDragTarget] so we can flush it once more
    // on `onDragStopped` — guaranteeing the final landing spot is exact even if the
    // last throttled emission was dropped.
    val pendingFarDragTarget = remember { mutableStateOf<Int?>(null) }
    val farDragFlow =
        remember {
            MutableSharedFlow<Int>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        }
    LaunchedEffect(farDragFlow) {
        farDragFlow.collect { lineIndex ->
            onScrollToLineIndexState.value(lineIndex)
            delay(FAR_DRAG_THROTTLE)
        }
    }

    // Convert a thumb ratio → target book line index via the **pixel-space** prefix
    // sum: `targetPx = ratio × (totalContentPx − viewport)`, then binary-search
    // `cumPx` for the line whose top is closest to `targetPx`. This makes the drag
    // visually consistent with the thumb (50 % of the thumb travel = 50 % of the
    // book's pixel content), even on books with highly non-uniform line lengths
    // (e.g. Talmud sugyot followed by short mishnayot). Snap locally when the
    // target line is loaded in the pager; otherwise route through the throttled
    // far-drag flow (drag) or call back immediately (tap-jump).
    val applyTarget =
        remember {
            fun(
                thumbRatio: Float,
                viaDrag: Boolean,
            ) {
                val ls = listStateRef.value
                val total = bookLineCountRef.value
                if (total == 0) return
                val cum = cumPxRef.value
                if (cum.size < total + 1) return
                val info = ls.layoutInfo
                val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
                val totalPx = totalContentPxRef.value
                val maxScrollPx = (totalPx - viewport).coerceAtLeast(0f).toDouble()
                val targetPx = (thumbRatio.toDouble() * maxScrollPx).coerceIn(0.0, totalPx.toDouble())
                val targetLineIndex = findLineIndexForPixel(cum, total, targetPx)
                val snapshot = pagingRef.value.itemSnapshotList.items
                val localIndex = snapshot.indexOfFirst { it.lineIndex == targetLineIndex }
                if (localIndex >= 0) {
                    ls.requestScrollToItem(localIndex, 0)
                    pendingFarDragTarget.value = null
                    return
                }
                if (viaDrag) {
                    pendingFarDragTarget.value = targetLineIndex
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
        modifier =
            modifier
                .width(visuals.thickness)
                .fillMaxHeight()
                .hoverable(interactionSource)
                .onSizeChanged { trackHeightPx = it.height }
                .background(visuals.trackColor)
                .pointerInput(trackHeightPx) {
                    detectTapGestures(onTap = { offset ->
                        if (trackHeightPx <= 0) return@detectTapGestures
                        val thumbRatio = (offset.y / trackHeightPx).coerceIn(0f, 1f)
                        dragRatio = thumbRatio
                        applyTarget(thumbRatio, false)
                    })
                },
    ) {
        Box(
            modifier =
                Modifier
                    .offset { IntOffset(0, thumbTopPx.toInt()) }
                    .fillMaxWidth()
                    .height(with(density) { thumbHeightPx.toDp() })
                    .clip(RoundedCornerShape(style.metrics.thumbCornerSize))
                    .background(visuals.thumbColor)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state =
                            rememberDraggableState { delta ->
                                val travel = travelPxState.value
                                if (travel <= 0f) return@rememberDraggableState
                                val current = dragRatio ?: dragStartRatio
                                val newRatio = (current + delta / travel).coerceIn(0f, 1f)
                                dragRatio = newRatio
                                applyTarget(newRatio, true)
                            },
                        onDragStarted = {
                            isDragging = true
                            val start = displayPositionState.value
                            dragStartRatio = start
                            dragRatio = start
                            pendingFarDragTarget.value = null
                        },
                        onDragStopped = {
                            isDragging = false
                            pendingFarDragTarget.value?.let { idx ->
                                pendingFarDragTarget.value = null
                                onScrollToLineIndexState.value(idx)
                            }
                        },
                    ),
        )
    }
}

// Minimum gap between two pager-rebuild emissions during a drag. Keeps the visible
// text catching up to fast drags (~5 Hz) without spamming `buildLinesPager` on every
// frame. Final exact target is flushed on `onDragStopped` regardless.
private val FAR_DRAG_THROTTLE = 200.milliseconds
private val PENDING_JUMP_TIMEOUT = 1500.milliseconds

private fun visualLinesForCount(
    charCount: Int,
    capacity: Int,
): Int {
    if (capacity <= 0) return 1
    if (charCount <= 0) return 1
    return max(1, ceil(charCount.toDouble() / capacity).toInt())
}

/**
 * Largest line index `i ∈ [0, total)` such that `cumPx[i] ≤ targetPx`. Pure binary
 * search, no allocation. `cumPx` is monotonically non-decreasing with `cumPx[0] = 0`
 * and `cumPx[total]` = total content height.
 */
private fun findLineIndexForPixel(
    cumPx: LongArray,
    total: Int,
    targetPx: Double,
): Int {
    if (total <= 0) return 0
    val target = targetPx.toLong()
    var lo = 0
    var hi = total - 1
    while (lo < hi) {
        val mid = (lo + hi + 1) ushr 1
        if (cumPx[mid] <= target) lo = mid else hi = mid - 1
    }
    return lo
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
