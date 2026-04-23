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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility.AlwaysVisible
import org.jetbrains.jewel.ui.theme.scrollbarStyle
import kotlin.math.abs

/**
 * Latched thumb-size sample. Lazily captured on the first frame where every input is
 * valid, then re-captured whenever `capacity` (font / text-size change) or the viewport
 * (resize / SplitPane move) drifts beyond [RELATCH_VIEWPORT_THRESHOLD]. Sub-pixel
 * jitter from layout settle is absorbed.
 */
internal data class LatchedThumbSize(
    val size: Float,
    val hidden: Boolean,
)

@Composable
internal fun rememberLatchedThumbSize(
    latchKey: Any,
    capacity: Int,
    lineHeightPx: Float,
    totalContentPx: Float,
    listState: LazyListState,
): LatchedThumbSize? {
    var latchedSize by remember(latchKey) { mutableFloatStateOf(-1f) }
    var latchedHidden by remember(latchKey) { mutableStateOf(false) }
    var latchedViewport by remember(latchKey) { mutableFloatStateOf(-1f) }
    var latchedCapacity by remember(latchKey) { mutableIntStateOf(-1) }
    LaunchedEffect(latchKey) {
        snapshotFlow {
            val viewport =
                (
                    listState.layoutInfo.viewportEndOffset -
                        listState.layoutInfo.viewportStartOffset
                ).toFloat()
            if (viewport > 0f && capacity > 0 && lineHeightPx > 0f && totalContentPx > 0f) {
                Triple(viewport, capacity, totalContentPx)
            } else {
                null
            }
        }.filterNotNull().collect { (viewport, cap, total) ->
            val viewportDrift =
                if (latchedViewport > 0f) {
                    abs(viewport - latchedViewport) / latchedViewport
                } else {
                    Float.POSITIVE_INFINITY
                }
            val needsRelatch =
                latchedSize < 0f || cap != latchedCapacity || viewportDrift >= RELATCH_VIEWPORT_THRESHOLD
            if (needsRelatch) {
                latchedSize = (viewport / total).coerceIn(0f, 1f)
                latchedHidden = total <= viewport
                latchedViewport = viewport
                latchedCapacity = cap
            }
        }
    }
    return if (latchedSize < 0f) null else LatchedThumbSize(latchedSize, latchedHidden)
}

/**
 * Animated visuals for a Jewel-themed scrollbar: track thickness, thumb / track colors
 * (smoothly transitioning to fully-transparent when the auto-hide bar is dormant) and
 * the show/hide state. Encapsulates the active / hovered / dragging logic shared by
 * both the book and commentary scrollbars.
 */
@Composable
internal fun rememberScrollbarVisuals(
    listState: LazyListState,
    isHovered: Boolean,
    dragRatio: Float?,
    label: String,
): ScrollbarVisuals {
    val style = JewelTheme.scrollbarStyle
    val visibility = style.scrollbarVisibility
    val isOpaque = visibility is AlwaysVisible

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

    val thickness by animateDpAsState(
        targetValue = if (isExpanded) visibility.trackThicknessExpanded else visibility.trackThickness,
        animationSpec = tween(visibility.expandAnimationDuration.inWholeMilliseconds.toInt(), easing = LinearEasing),
        label = "${label}_thickness",
    )
    val targetThumbColor =
        when {
            isOpaque && isHovered -> style.colors.thumbOpaqueBackgroundHovered
            isOpaque -> style.colors.thumbOpaqueBackground
            showScrollbar && (isHovered || dragRatio != null) -> style.colors.thumbBackgroundActive
            showScrollbar -> style.colors.thumbBackground
            else -> style.colors.thumbBackground.copy(alpha = 0f)
        }
    val thumbColor by animateColorAsState(
        targetValue = targetThumbColor,
        animationSpec = tween(visibility.thumbColorAnimationDuration.inWholeMilliseconds.toInt(), easing = LinearEasing),
        label = "${label}_thumb_color",
    )
    val targetTrackColor =
        when {
            isOpaque -> if (isHovered) style.colors.trackOpaqueBackgroundHovered else style.colors.trackOpaqueBackground
            isExpanded -> style.colors.trackBackgroundExpanded
            else -> style.colors.trackBackground
        }
    val trackColor by animateColorAsState(
        targetValue = targetTrackColor,
        animationSpec = tween(visibility.trackColorAnimationDuration.inWholeMilliseconds.toInt(), easing = LinearEasing),
        label = "${label}_track_color",
    )
    return ScrollbarVisuals(thickness, thumbColor, trackColor)
}

internal data class ScrollbarVisuals(
    val thickness: Dp,
    val thumbColor: Color,
    val trackColor: Color,
)

/**
 * Shared track + thumb shell used by [ContentScrollbar] and [CommentariesScrollbar].
 * Owns the drag state, hover detection, theming, convergence/timeout effects and the
 * gesture wiring. Callers supply the precomputed thumb [position] and [thumbSize], plus
 * [onApplyTarget] which converts a drag/tap thumb ratio into a scroll action — this is
 * where caller-specific geometry (book-line vs item-index, far-drag throttling) lives.
 */
@Composable
internal fun ContentAwareScrollbarShell(
    listState: LazyListState,
    position: Float,
    thumbSize: Float,
    visualsLabel: String,
    onApplyTarget: (thumbRatio: Float, viaDrag: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onDragStart: () -> Unit = {},
    onDragStop: () -> Unit = {},
) {
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
            label = visualsLabel,
        )

    val density = LocalDensity.current
    val minThumbHeightPx = with(density) { style.metrics.minThumbLength.toPx() }

    val displayPosition = dragRatio ?: position
    val thumbHeightPx = (thumbSize * trackHeightPx).coerceAtLeast(minThumbHeightPx)
    val travelPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
    val thumbTopPx = (displayPosition * travelPx).coerceIn(0f, travelPx)

    val travelPxState = rememberUpdatedState(travelPx)
    val displayPositionState = rememberUpdatedState(displayPosition)
    val onApplyTargetState = rememberUpdatedState(onApplyTarget)
    val onDragStartState = rememberUpdatedState(onDragStart)
    val onDragStopState = rememberUpdatedState(onDragStop)

    // Convergence: unpin the thumb once the live scroll catches up to the dragged target.
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
                        onApplyTargetState.value(thumbRatio, false)
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
                                onApplyTargetState.value(newRatio, true)
                            },
                        onDragStarted = {
                            isDragging = true
                            val start = displayPositionState.value
                            dragStartRatio = start
                            dragRatio = start
                            onDragStartState.value()
                        },
                        onDragStopped = {
                            isDragging = false
                            onDragStopState.value()
                        },
                    ),
        )
    }
}
