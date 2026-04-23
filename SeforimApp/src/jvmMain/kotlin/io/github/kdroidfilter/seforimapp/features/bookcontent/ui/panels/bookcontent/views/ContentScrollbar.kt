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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility.AlwaysVisible
import org.jetbrains.jewel.ui.theme.scrollbarStyle

/**
 * Content-aware vertical scrollbar painted with Jewel's [scrollbarStyle].
 *
 * The thumb position and size do not come from LazyColumn item indices but from the
 * cumulative-character offsets persisted on every [Line] at generation time, so the
 * thumb stays accurate even when individual lines wrap to thousands of visual rows.
 *
 * Visual styling — colors, thumb corner radius, track thickness, expand-on-hover and
 * fade durations — is read from [JewelTheme.scrollbarStyle] so the scrollbar blends in
 * with every other Jewel scrollbar in the app and adapts to the current Int UI theme.
 *
 * Drag interactions stay cheap by deferring expensive jumps:
 *  - drags whose target stays inside the loaded paging window resolve via `scrollToItem`;
 *  - drags that leave the loaded window are debounced and forwarded to [onScrollToRatio]
 *    once the user lingers, so the pager only rebuilds at the end of a long jump.
 */
@Composable
fun ContentScrollbar(
    listState: LazyListState,
    lazyPagingItems: LazyPagingItems<Line>,
    totalChars: Long,
    onScrollToRatio: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (totalChars <= 0L || lazyPagingItems.itemCount == 0) return

    val style = JewelTheme.scrollbarStyle
    val visibility = style.scrollbarVisibility
    val isOpaque = visibility is AlwaysVisible

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val scope = rememberCoroutineScope()
    var trackHeightPx by remember { mutableIntStateOf(0) }
    // Thumb visual override: represents the user's requested target position while the
    // underlying LazyColumn scroll is catching up. Set during drag, set on tap, and
    // cleared by a convergence LaunchedEffect once `rawMetrics.topChars` reaches the
    // target (or by a safety timeout). Keeping it set past drag release is what stops
    // the thumb from visually bouncing back while the pager rebuild is still in flight.
    var dragRatio by remember { mutableStateOf<Float?>(null) }
    var dragStartRatio by remember { mutableFloatStateOf(0f) }
    // True while the pointer is actively dragging the thumb. Used by the convergence
    // effect to avoid clearing `dragRatio` mid-drag.
    var isDragging by remember { mutableStateOf(false) }

    val isScrolling by remember(listState) {
        derivedStateOf { listState.isScrollInProgress || dragRatio != null }
    }
    val isExpanded = isHovered || dragRatio != null
    val isActive = isOpaque || isScrolling || isHovered

    // Linger after scroll/hover stops, matching Jewel's WhenScrolling behavior.
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

    // Hold the last fully-resolved raw metrics. While the paging window is mid-refresh,
    // the visible items can momentarily fail to resolve (peek/key lookup returns null)
    // — without this cache the thumb would briefly snap to the top.
    val rawMetrics by produceState(
        initialValue = ScrollbarMetrics(topChars = 0L, visibleChars = totalChars),
        listState,
        lazyPagingItems,
        totalChars,
    ) {
        snapshotFlow { computeScrollbarMetricsOrNull(listState, lazyPagingItems, totalChars) }
            .collect { computed -> if (computed != null) value = computed }
    }

    // EMA-smoothed viewportChars used as the position/size denominator. `topChars` is
    // NOT smoothed, so real scroll input produces immediate 1:1 thumb movement. Only
    // the scale factor slowly drifts, which stays invisible on books where the
    // viewport covers a small fraction of the content and stays imperceptibly small
    // even on short books like Mishna Berakhot. This eliminates the ~10 px jitter
    // caused by viewportChars changing each time a line enters or leaves the viewport.
    val smoothedViewport = remember { mutableFloatStateOf(-1f) }
    LaunchedEffect(rawMetrics, totalChars) {
        snapshotFlow { rawMetrics.visibleChars }.collect { target ->
            val targetF = target.toFloat()
            val current = smoothedViewport.floatValue
            smoothedViewport.floatValue = when {
                // First valid sample seeds the filter; no warm-up ramp means no 20-frame
                // thumb resize when the book first opens.
                current < 0f -> targetF
                // Large jumps are treated as real transitions (window resize, book
                // reload) and snap through the filter instantly.
                kotlin.math.abs(targetF - current) > current * 0.5f + 1f -> targetF
                else -> 0.85f * current + 0.15f * targetF
            }
        }
    }

    val stableViewportChars = smoothedViewport.floatValue.coerceAtLeast(0f).toLong()
    val maxOffsetChars = (totalChars - stableViewportChars).coerceAtLeast(1L)
    val position = (rawMetrics.topChars.toFloat() / maxOffsetChars).coerceIn(0f, 1f)
    val size = (stableViewportChars.toFloat() / totalChars).coerceIn(0f, 1f)

    val density = LocalDensity.current
    val minThumbHeightPx = with(density) { style.metrics.minThumbLength.toPx() }

    val displayPosition = dragRatio ?: position
    val thumbHeightPx = (size * trackHeightPx).coerceAtLeast(minThumbHeightPx)
    val travelPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
    val thumbTopPx = (displayPosition * travelPx).coerceIn(0f, travelPx)

    // The draggable() lambdas are remembered once; capture state-backed proxies so the
    // gesture handlers always read the latest layout-derived values (track height,
    // current position, viewport size) instead of stale snapshots from the first composition.
    val travelPxState = rememberUpdatedState(travelPx)
    val displayPositionState = rememberUpdatedState(displayPosition)
    // Drag/tap math uses the same smoothed viewport the user sees, so the thumb visually
    // "catches up" perfectly to wherever the click lands.
    val visibleCharsState = rememberUpdatedState(stableViewportChars)
    val totalCharsState = rememberUpdatedState(totalChars)
    val listStateRef = rememberUpdatedState(listState)
    val pagingRef = rememberUpdatedState(lazyPagingItems)
    val onScrollToRatioState = rememberUpdatedState(onScrollToRatio)

    // Out-of-window drag throttle: we need the content to follow the thumb during a
    // fast drag (debouncing stalls the pager rebuild until the user stops moving,
    // which looks broken), but we also cannot rebuild the pager on every pointer
    // event. `BufferOverflow.DROP_OLDEST` + `extraBufferCapacity = 1` keeps only the
    // latest pending ratio while the collector sleeps between rebuilds, so a 50ms
    // throttle caps pager rebuilds at ~20 Hz while still processing the final target
    // within one throttle window after the user releases.
    val farDragFlow = remember {
        MutableSharedFlow<Float>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    }
    LaunchedEffect(farDragFlow) {
        farDragFlow.collect { bookRatio ->
            onScrollToRatioState.value(bookRatio.coerceIn(0f, 1f))
            delay(FAR_DRAG_THROTTLE)
        }
    }

    // Apply a thumb-track ratio as an actual scroll. In-window targets go through the
    // synchronous `requestScrollToItem` path so fast drags never get cancelled by the
    // next pointer event (which was the root cause of "the content doesn't follow the
    // thumb during fast drag"). Out-of-window targets either tap-jump directly or, for
    // continuous drag, feed the debounced [farDragFlow] so the pager only rebuilds
    // once the user lingers.
    val applyTarget = remember {
        fun(thumbRatio: Float, viaDrag: Boolean) {
            val viewport = visibleCharsState.value.coerceAtLeast(0L)
            val total = totalCharsState.value
            if (total <= 0L) return
            val maxOffset = (total - viewport).coerceAtLeast(1L)
            val targetChars = (thumbRatio.toDouble() * maxOffset).toLong()
            val snapshot = pagingRef.value.itemSnapshotList.items
            val first = snapshot.firstOrNull()
            val last = snapshot.lastOrNull()
            val inWindow = first != null && last != null &&
                targetChars >= first.cumulativeChars &&
                targetChars <= last.cumulativeChars + last.charCount
            if (inWindow) {
                val idx = snapshot.indexOfFirst { it.cumulativeChars >= targetChars }
                if (idx >= 0) listStateRef.value.requestScrollToItem(idx, 0)
            } else {
                val bookRatio = (targetChars.toFloat() / total).coerceIn(0f, 1f)
                if (viaDrag) {
                    farDragFlow.tryEmit(bookRatio)
                } else {
                    onScrollToRatioState.value(bookRatio)
                }
            }
        }
    }

    // Convergence: keep the thumb pinned at the user's requested position until the
    // real scroll state catches up. Without this, on a long jump the content loads
    // first and the thumb visibly snaps *after* — a latency the user sees as lag.
    // Only clears once the drag gesture has ended (isDragging=false) and the live
    // topChars is close enough to the target, or after a safety timeout.
    LaunchedEffect(dragRatio, isDragging, rawMetrics.topChars, stableViewportChars, totalChars) {
        val target = dragRatio ?: return@LaunchedEffect
        if (isDragging) return@LaunchedEffect
        val maxOffset = (totalChars - stableViewportChars).coerceAtLeast(1L)
        val targetTopChars = (target.toDouble() * maxOffset).toLong()
        val delta = kotlin.math.abs(rawMetrics.topChars - targetTopChars)
        val tolerance = (maxOffset / 200L).coerceAtLeast(50L)
        if (delta <= tolerance) {
            dragRatio = null
        }
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
            .pointerInput(trackHeightPx, totalChars) {
                detectTapGestures(onTap = { offset ->
                    if (trackHeightPx <= 0) return@detectTapGestures
                    val thumbRatio = (offset.y / trackHeightPx).coerceIn(0f, 1f)
                    // Lock the thumb on the tap position until content catches up.
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
                        // Apply synchronously via requestScrollToItem (no suspend, no
                        // cancellation): a fast drag now keeps the content glued to the
                        // thumb instead of losing every scroll to the next pointer event.
                        applyTarget(newRatio, /* viaDrag = */ true)
                    },
                    onDragStarted = {
                        isDragging = true
                        val start = displayPositionState.value
                        dragStartRatio = start
                        dragRatio = start
                    },
                    onDragStopped = {
                        // Keep `dragRatio` set; the convergence effect will clear it once
                        // the live scroll reaches the final target. This avoids the thumb
                        // "snapping back" toward the metrics position during far jumps
                        // while the pager rebuild is still in flight.
                        isDragging = false
                    },
                ),
        )
    }
}

// Minimum spacing between consecutive pager rebuilds while the user drags through
// out-of-paging-window positions. Caps rebuild rate to avoid thrashing the VM while
// still letting the content chase the thumb during a fast continuous drag.
private val FAR_DRAG_THROTTLE = 50.milliseconds

// Safety timeout for the "thumb pinned on target" state. If `rawMetrics.topChars` never
// converges to the requested target (pager rebuild failed, book ended unexpectedly, …)
// we release the pin so the thumb cannot stay frozen forever.
private val PENDING_JUMP_TIMEOUT = 1500.milliseconds

// Above this normalized delta we treat a metrics change as an intentional jump and
// snap instantly; below it we run the smoothing tween. 2% of the track is large
// enough to skip per-frame jitter from line composition while still snapping every
// real navigation (tap, drag end, TOC click, …).
private const val JUMP_THRESHOLD = 0.02f

private class ContentJumpDispatcher(
    @property:StructuredScope private val scope: CoroutineScope,
    private val listState: LazyListState,
    private val lazyPagingItems: LazyPagingItems<Line>,
    private val totalChars: Long,
    private val onScrollToRatio: (Float) -> Unit,
) {
    /**
     * Resolves [thumbRatio] (0..1 of the track) either by snapping to a line that is
     * already in the loaded paging window (no SQL, no pager rebuild) or by debouncing a
     * far jump that ends up calling the VM-backed [onScrollToRatio]. The track ratio is
     * mapped against `totalChars - viewportChars` so a fully-bottom thumb truly lands on
     * the last viewport-worth of content even when the book barely fills the screen.
     */
    fun dispatchJump(thumbRatio: Float, viewportChars: Long): Job {
        val viewport = viewportChars.coerceAtLeast(0L)
        val maxOffsetChars = (totalChars - viewport).coerceAtLeast(1L)
        val targetChars = (thumbRatio.coerceIn(0f, 1f).toDouble() * maxOffsetChars).toLong()
        val snapshot = lazyPagingItems.itemSnapshotList.items
        val first = snapshot.firstOrNull()
        val last = snapshot.lastOrNull()
        // Fast path requires the target to be strictly inside the loaded window. Otherwise
        // `indexOfFirst { cumulativeChars >= target }` would clamp to snapshot[0] for any
        // target above the window, parking the thumb on the first loaded line instead of
        // the real position the user clicked.
        val fitsInWindow = first != null && last != null &&
            targetChars >= first.cumulativeChars &&
            targetChars <= last.cumulativeChars + last.charCount
        if (fitsInWindow) {
            val localIndex = snapshot.indexOfFirst { it.cumulativeChars >= targetChars }
            if (localIndex >= 0) {
                return scope.launch { listState.scrollToItem(localIndex, 0) }
            }
        }
        return scope.launch {
            delay(FAR_JUMP_DEBOUNCE)
            // VM expects the absolute book-position ratio (target / totalChars), not the
            // thumb-track ratio.
            onScrollToRatio((targetChars.toFloat() / totalChars).coerceIn(0f, 1f))
        }
    }

    private companion object {
        val FAR_JUMP_DEBOUNCE = 150.milliseconds
    }
}

/**
 * Raw scrollbar metrics read from the live layout info. Deliberately *not* normalized
 * to 0..1 here: the composable smooths the `visibleChars` denominator over time to
 * stabilize the thumb on small books (where one item entering/leaving the viewport
 * would otherwise shift the position formula by several percent), while keeping
 * `topChars` untouched so the thumb still tracks every scroll pixel in real time.
 */
internal data class ScrollbarMetrics(
    val topChars: Long,
    val visibleChars: Long,
)

/**
 * Returns scrollbar metrics for the current viewport, or `null` when the visible items
 * cannot be resolved yet (paging mid-refresh, no items materialized, etc.). Callers
 * should keep the previous value in that case to avoid the thumb snapping to the top.
 *
 * Lines are resolved by item **key** (the Line id we register via `itemKey { it.id }`)
 * instead of by index. During a paging prepend, the snapshot grows at the start so the
 * same global index briefly points to an earlier Line before `LazyListState` catches
 * up and shifts `firstVisibleItemIndex`. That 1-frame desync is exactly what produced
 * the ~10 px saccades the user saw; looking up by key sidesteps the transient state.
 */
internal fun computeScrollbarMetricsOrNull(
    listState: LazyListState,
    lazyPagingItems: LazyPagingItems<Line>,
    totalChars: Long,
): ScrollbarMetrics? {
    val info = listState.layoutInfo
    val itemCount = lazyPagingItems.itemCount
    if (totalChars <= 0L || itemCount == 0) return null

    // The LazyColumn injects loading/error placeholders past the real paging window;
    // their indices sit at [itemCount, itemCount + N) and would crash peek() with
    // IndexOutOfBoundsException, so restrict the metrics to the actual content rows.
    val visible = info.visibleItemsInfo.filter { it.index < itemCount }
    if (visible.isEmpty()) return null

    val snapshot = lazyPagingItems.itemSnapshotList.items
    val first = visible.first()
    val last = visible.last()
    val firstLine = resolveLine(first, snapshot, lazyPagingItems) ?: return null
    val lastLine = resolveLine(last, snapshot, lazyPagingItems) ?: firstLine

    val firstSize = first.size.coerceAtLeast(1)
    val firstWithin = ((-first.offset).toFloat() / firstSize).coerceIn(0f, 1f)
    val topChars = firstLine.cumulativeChars + (firstWithin * firstLine.charCount).toLong()

    val lastSize = last.size.coerceAtLeast(1)
    val lastVisiblePx = (info.viewportEndOffset - last.offset).coerceIn(0, lastSize)
    val lastWithin = (lastVisiblePx.toFloat() / lastSize).coerceIn(0f, 1f)
    val bottomChars = lastLine.cumulativeChars + (lastWithin * lastLine.charCount).toLong()

    val visibleChars = (bottomChars - topChars).coerceAtLeast(0L)

    // Standard scrollbar mapping: `position` is the offset relative to the maximum
    // scrollable distance (totalChars - viewportChars), not the full content. Without
    // this, the thumb tops out at `1 - viewport/total` of the track — visible as
    // missing 20% of the surface on short books like Mishna Berakhot where the
    // viewport covers a sizable fraction of `totalChars`.
    return ScrollbarMetrics(topChars = topChars, visibleChars = visibleChars)
}

/** Convenience wrapper for tests/debug callers; never returns `null`. */
internal fun computeScrollbarMetrics(
    listState: LazyListState,
    lazyPagingItems: LazyPagingItems<Line>,
    totalChars: Long,
): ScrollbarMetrics =
    computeScrollbarMetricsOrNull(listState, lazyPagingItems, totalChars)
        ?: ScrollbarMetrics(topChars = 0L, visibleChars = totalChars)

/**
 * Resolves the [Line] backing a visible LazyColumn item. Prefers key-based lookup so a
 * stale [androidx.compose.foundation.lazy.LazyListItemInfo.index] (during a paging
 * prepend or diff) cannot cross-wire to the wrong line in the snapshot. Falls back to
 * index-based [LazyPagingItems.peek] only when the key cannot be matched — typically
 * the first frame before the snapshot catches up.
 */
private fun resolveLine(
    info: androidx.compose.foundation.lazy.LazyListItemInfo,
    snapshot: List<Line>,
    lazyPagingItems: LazyPagingItems<Line>,
): Line? {
    val key = info.key
    if (key is Long) {
        // Linear scan over the loaded window (~30 items with the current paging config);
        // cheap enough to run on every frame.
        for (line in snapshot) {
            if (line.id == key) return line
        }
    }
    return lazyPagingItems.peek(info.index)
}
