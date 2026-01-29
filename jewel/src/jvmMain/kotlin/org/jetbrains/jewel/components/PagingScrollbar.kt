package org.jetbrains.jewel.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalDragOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.foundation.v2.maxScrollOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility.AlwaysVisible
import org.jetbrains.jewel.ui.component.styling.TrackClickBehavior
import org.jetbrains.jewel.ui.component.styling.TrackClickBehavior.JumpToSpot
import org.jetbrains.jewel.ui.component.styling.TrackClickBehavior.NextPage
import org.jetbrains.jewel.ui.theme.scrollbarStyle

/**
 * A vertical scrollbar that accepts a custom [ScrollbarAdapter].
 *
 * This is useful for paging scenarios where you want to show a scrollbar
 * that reflects the total content size, not just the currently loaded items.
 *
 * @param adapter The custom [ScrollbarAdapter] that provides scroll information.
 * @param modifier The modifier to apply to this layout node.
 * @param reverseLayout `true` to reverse the direction of the scrollbar, `false` otherwise.
 * @param enabled `true` to enable the scrollbar, `false` otherwise.
 * @param interactionSource The [MutableInteractionSource] that will be used to dispatch events.
 * @param style The [ScrollbarStyle] to use for this scrollbar.
 * @param keepVisible `true` to keep the scrollbar visible even when not scrolling, `false` otherwise.
 */
@Composable
fun VerticalPagingScrollbar(
    adapter: ScrollbarAdapter,
    modifier: Modifier = Modifier,
    reverseLayout: Boolean = false,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ScrollbarStyle = JewelTheme.scrollbarStyle,
    keepVisible: Boolean = false,
) {
    val isHovered by interactionSource.collectIsHoveredAsState()

    val dragInteraction = remember { mutableStateOf<DragInteraction.Start?>(null) }
    var showScrollbar by remember { mutableStateOf(false) }

    DisposableEffect(interactionSource) {
        onDispose {
            dragInteraction.value?.let { interaction ->
                interactionSource.tryEmit(DragInteraction.Cancel(interaction))
                dragInteraction.value = null
            }
        }
    }

    val visibilityStyle by rememberUpdatedState(style.scrollbarVisibility)
    val isOpaque by remember { derivedStateOf { visibilityStyle is AlwaysVisible } }

    val isDragging by remember { derivedStateOf { dragInteraction.value != null } }
    val isExpanded by remember { derivedStateOf { showScrollbar && (isHovered || isDragging) } }
    // For custom adapter, we detect scrolling via offset changes
    var lastScrollOffset by remember { mutableStateOf(adapter.scrollOffset) }
    val isScrolling by remember {
        derivedStateOf {
            val currentOffset = adapter.scrollOffset
            val scrolling = currentOffset != lastScrollOffset
            lastScrollOffset = currentOffset
            scrolling || isDragging
        }
    }
    val isActive by remember { derivedStateOf { isOpaque || isScrolling || (keepVisible && showScrollbar) } }

    LaunchedEffect(isActive, isHovered, isDragging) {
        if (isActive || isHovered) {
            showScrollbar = true
        } else if (!isDragging) {
            launch {
                delay(visibilityStyle.lingerDuration.inWholeMilliseconds)
                showScrollbar = false
            }
        }
    }

    val animatedThickness by
        animateDpAsState(
            if (isExpanded) visibilityStyle.trackThicknessExpanded else visibilityStyle.trackThickness,
            tween(visibilityStyle.expandAnimationDuration.inWholeMilliseconds.toInt(), easing = LinearEasing),
            "scrollbar_thickness",
        )

    with(LocalDensity.current) {
        var containerSize by remember { mutableIntStateOf(0) }
        val thumbMinHeight = style.metrics.minThumbLength.toPx()

        val coroutineScope = rememberCoroutineScope()
        val sliderAdapter =
            remember(adapter, containerSize, thumbMinHeight, reverseLayout, coroutineScope) {
                SliderAdapter(adapter, containerSize, thumbMinHeight, reverseLayout, isVertical = true, coroutineScope)
            }

        val thumbBackgroundColor = getThumbBackgroundColor(isOpaque, isHovered, isScrolling, style, showScrollbar)
        val thumbBorderColor = getThumbBorderColor(isOpaque, isHovered, isScrolling, style, showScrollbar)
        val hasVisibleBorder = !areTheSameColor(thumbBackgroundColor, thumbBorderColor)
        val trackPadding by
            rememberUpdatedState(
                when {
                    isExpanded -> visibilityStyle.trackPaddingExpanded
                    hasVisibleBorder -> visibilityStyle.trackPaddingWithBorder
                    else -> visibilityStyle.trackPadding
                }
            )

        val layoutDirection = LocalLayoutDirection.current
        val thumbThicknessPx =
            (animatedThickness -
                trackPadding.calculateLeftPadding(layoutDirection) -
                trackPadding.calculateRightPadding(layoutDirection))
                .roundToPx()

        val measurePolicy =
            remember(sliderAdapter, thumbThicknessPx) {
                verticalMeasurePolicy(sliderAdapter, { containerSize = it }, thumbThicknessPx)
            }

        val canScroll = sliderAdapter.thumbSize < containerSize

        val trackBackground by
            animateColorAsState(
                targetValue = getTrackColor(isOpaque, isDragging, isHovered, style, isExpanded),
                animationSpec = trackColorTween(visibilityStyle),
                label = "scrollbar_trackBackground",
            )

        // Create a simple scrollable state for the scrollable modifier
        val scrollableState = remember(adapter) {
            androidx.compose.foundation.gestures.ScrollableState { delta ->
                // This is a simplified implementation - the actual scrolling is handled by the adapter
                delta
            }
        }

        Layout(
            content = {
                Thumb(
                    showScrollbar,
                    visibilityStyle,
                    canScroll,
                    enabled,
                    interactionSource,
                    dragInteraction,
                    sliderAdapter,
                    thumbBackgroundColor,
                    thumbBorderColor,
                    hasVisibleBorder,
                    style.metrics.thumbCornerSize,
                )
            },
            modifier =
                modifier
                    .semantics { hideFromAccessibility() }
                    .thenIf(showScrollbar && canScroll && isExpanded) { background(trackBackground) }
                    .scrollable(
                        state = scrollableState,
                        orientation = Orientation.Vertical,
                        enabled = enabled,
                        reverseDirection = true,
                    )
                    .padding(trackPadding)
                    .hoverable(interactionSource = interactionSource)
                    .thenIf(enabled && showScrollbar) {
                        scrollOnPressTrack(style.trackClickBehavior, reverseLayout, sliderAdapter)
                    },
            measurePolicy = measurePolicy,
        )
    }
}

private fun getTrackColor(
    isOpaque: Boolean,
    isDragging: Boolean,
    isHovered: Boolean,
    style: ScrollbarStyle,
    isExpanded: Boolean,
) =
    if (isOpaque) {
        if (isHovered || isDragging) {
            style.colors.trackOpaqueBackgroundHovered
        } else {
            style.colors.trackOpaqueBackground
        }
    } else {
        if (isExpanded) {
            style.colors.trackBackgroundExpanded
        } else {
            style.colors.trackBackground
        }
    }

private fun getThumbBackgroundColor(
    isOpaque: Boolean,
    isHovered: Boolean,
    isScrolling: Boolean,
    style: ScrollbarStyle,
    showScrollbar: Boolean,
) =
    if (isOpaque) {
        if (isHovered || isScrolling) {
            style.colors.thumbOpaqueBackgroundHovered
        } else {
            style.colors.thumbOpaqueBackground
        }
    } else {
        if (showScrollbar) {
            style.colors.thumbBackgroundActive
        } else {
            style.colors.thumbBackground
        }
    }

private fun getThumbBorderColor(
    isOpaque: Boolean,
    isHovered: Boolean,
    isScrolling: Boolean,
    style: ScrollbarStyle,
    showScrollbar: Boolean,
) =
    if (isOpaque) {
        if (isHovered || isScrolling) {
            style.colors.thumbOpaqueBorderHovered
        } else {
            style.colors.thumbOpaqueBorder
        }
    } else {
        if (showScrollbar) {
            style.colors.thumbBorderActive
        } else {
            style.colors.thumbBorder
        }
    }

private fun areTheSameColor(first: Color, second: Color) = first.toArgb() == second.toArgb()

@Composable
private fun Thumb(
    showScrollbar: Boolean,
    visibilityStyle: ScrollbarVisibility,
    canScroll: Boolean,
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    dragInteraction: MutableState<DragInteraction.Start?>,
    sliderAdapter: SliderAdapter,
    thumbBackgroundColor: Color,
    thumbBorderColor: Color,
    hasVisibleBorder: Boolean,
    cornerSize: CornerSize,
) {
    val background by
        animateColorAsState(
            targetValue = thumbBackgroundColor,
            animationSpec = thumbColorTween(showScrollbar, visibilityStyle),
            label = "scrollbar_thumbBackground",
        )

    val border by
        animateColorAsState(
            targetValue = thumbBorderColor,
            animationSpec = thumbColorTween(showScrollbar, visibilityStyle),
            label = "scrollbar_thumbBorder",
        )

    val borderWidth = 1.dp
    val density = LocalDensity.current
    Box(
        Modifier.layoutId("thumb")
            .thenIf(canScroll) { drawThumb(background, borderWidth, border, hasVisibleBorder, cornerSize, density) }
            .thenIf(enabled) { scrollbarDrag(interactionSource, dragInteraction, sliderAdapter) }
    )
}

private fun Modifier.drawThumb(
    backgroundColor: Color,
    borderWidth: Dp,
    borderColor: Color,
    hasVisibleBorder: Boolean,
    cornerSize: CornerSize,
    density: Density,
) = drawBehind {
    val borderWidthPx = if (hasVisibleBorder) borderWidth.toPx() else 0f

    val bgCornerRadius = CornerRadius((cornerSize.toPx(size, density) - borderWidthPx * 2).coerceAtLeast(0f))
    drawRoundRect(
        color = backgroundColor,
        topLeft = Offset(borderWidthPx, borderWidthPx),
        size = Size(size.width - borderWidthPx * 2, size.height - borderWidthPx * 2f),
        cornerRadius = bgCornerRadius,
    )

    if (hasVisibleBorder) {
        val strokeCornerRadius = CornerRadius(cornerSize.toPx(size, density))
        drawRoundRect(
            color = borderColor,
            topLeft = Offset(borderWidthPx / 2, borderWidthPx / 2),
            size = Size(size.width - borderWidthPx, size.height - borderWidthPx),
            cornerRadius = strokeCornerRadius,
            style = Stroke(borderWidthPx),
        )
    }
}

private fun trackColorTween(visibility: ScrollbarVisibility) =
    tween<Color>(visibility.trackColorAnimationDuration.inWholeMilliseconds.toInt(), easing = LinearEasing)

private fun thumbColorTween(showScrollbar: Boolean, visibility: ScrollbarVisibility) =
    tween<Color>(
        durationMillis =
            when {
                visibility is AlwaysVisible || !showScrollbar ->
                    visibility.thumbColorAnimationDuration.inWholeMilliseconds.toInt()
                else -> 0
            },
        delayMillis =
            when {
                visibility is AlwaysVisible && !showScrollbar -> visibility.lingerDuration.inWholeMilliseconds.toInt()
                else -> 0
            },
        easing = LinearEasing,
    )

private val SliderAdapter.thumbPixelRange: IntRange
    get() {
        val start = position.roundToInt()
        val endExclusive = start + thumbSize.roundToInt()
        return (start until endExclusive)
    }

private val IntRange.size
    get() = last + 1 - first

private fun verticalMeasurePolicy(sliderAdapter: SliderAdapter, setContainerSize: (Int) -> Unit, thumbThickness: Int) =
    MeasurePolicy { measurables, constraints ->
        setContainerSize(constraints.maxHeight)
        val pixelRange = sliderAdapter.thumbPixelRange
        val placeable =
            measurables.first().measure(Constraints.fixed(constraints.constrainWidth(thumbThickness), pixelRange.size))
        layout(placeable.width, constraints.maxHeight) { placeable.place(0, pixelRange.first) }
    }

private fun Modifier.scrollbarDrag(
    interactionSource: MutableInteractionSource,
    draggedInteraction: MutableState<DragInteraction.Start?>,
    sliderAdapter: SliderAdapter,
): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val interaction = DragInteraction.Start()
        interactionSource.tryEmit(interaction)
        draggedInteraction.value = interaction
        sliderAdapter.onDragStarted()
        val isSuccess =
            drag(down.id) { change ->
                sliderAdapter.onDragDelta(change.positionChange())
                change.consume()
            }
        val finishInteraction =
            if (isSuccess) {
                DragInteraction.Stop(interaction)
            } else {
                DragInteraction.Cancel(interaction)
            }
        interactionSource.tryEmit(finishInteraction)
        draggedInteraction.value = null
    }
}

private fun Modifier.scrollOnPressTrack(
    clickBehavior: TrackClickBehavior,
    reverseLayout: Boolean,
    sliderAdapter: SliderAdapter,
): Modifier = this.pointerInput(sliderAdapter, reverseLayout, clickBehavior) {
    detectScrollViaTrackGestures(sliderAdapter, reverseLayout, clickBehavior)
}

private suspend fun PointerInputScope.detectScrollViaTrackGestures(
    sliderAdapter: SliderAdapter,
    reverseLayout: Boolean,
    clickBehavior: TrackClickBehavior,
) {
    val coroutineScope = kotlinx.coroutines.MainScope()

    fun directionOfScrollTowards(offset: Float): Int {
        val pixelRange = sliderAdapter.thumbPixelRange
        return when {
            offset < pixelRange.first -> if (reverseLayout) 1 else -1
            offset > pixelRange.last -> if (reverseLayout) -1 else 1
            else -> 0
        }
    }

    var scrollJob: Job? = null

    awaitEachGesture {
        val down = awaitFirstDown()
        val offset = down.position.y
        val direction = directionOfScrollTowards(offset)

        if (direction != 0) {
            when (clickBehavior) {
                NextPage -> {
                    scrollJob?.cancel()
                    scrollJob = coroutineScope.launch {
                        with(sliderAdapter.adapter) {
                            scrollTo(scrollOffset + direction * viewportSize)
                        }
                        delay(300L)
                        while (true) {
                            val currentDirection = directionOfScrollTowards(offset)
                            if (currentDirection == direction) {
                                with(sliderAdapter.adapter) {
                                    scrollTo(scrollOffset + direction * viewportSize)
                                }
                            }
                            delay(100L)
                        }
                    }
                }
                JumpToSpot -> {
                    scrollJob?.cancel()
                    scrollJob = coroutineScope.launch {
                        val contentSize = sliderAdapter.adapter.contentSize
                        val scrollOffset = offset / sliderAdapter.adapter.viewportSize * contentSize
                        sliderAdapter.adapter.scrollTo(scrollOffset)
                    }
                }
            }
        }

        while (true) {
            val drag = awaitVerticalDragOrCancellation(down.id)
            if (drag == null || !drag.pressed) {
                scrollJob?.cancel()
                break
            } else if (clickBehavior == JumpToSpot) {
                scrollJob?.cancel()
                scrollJob = coroutineScope.launch {
                    val contentSize = sliderAdapter.adapter.contentSize
                    val newScrollOffset = drag.position.y / sliderAdapter.adapter.viewportSize * contentSize
                    sliderAdapter.adapter.scrollTo(newScrollOffset)
                }
            }
        }
    }
}

internal class SliderAdapter(
    val adapter: ScrollbarAdapter,
    private val trackSize: Int,
    private val minHeight: Float,
    private val reverseLayout: Boolean,
    private val isVertical: Boolean,
    private val coroutineScope: CoroutineScope,
) {
    private val contentSize
        get() = adapter.contentSize

    private val visiblePart: Double
        get() {
            val contentSize = contentSize
            return if (contentSize == 0.0) {
                1.0
            } else {
                (adapter.viewportSize / contentSize).coerceAtMost(1.0)
            }
        }

    val thumbSize
        get() = (trackSize * visiblePart).coerceAtLeast(minHeight.toDouble())

    private val scrollScale: Double
        get() {
            val extraScrollbarSpace = trackSize - thumbSize
            val extraContentSpace = adapter.maxScrollOffset
            return if (extraContentSpace == 0.0) 1.0 else extraScrollbarSpace / extraContentSpace
        }

    private val rawPosition: Double
        get() = scrollScale * adapter.scrollOffset

    val position: Double
        get() = if (reverseLayout) trackSize - thumbSize - rawPosition else rawPosition

    private var unscrolledDragDistance = 0.0

    fun onDragStarted() {
        unscrolledDragDistance = 0.0
    }

    private suspend fun setPosition(value: Double) {
        val rawPosition =
            if (reverseLayout) {
                trackSize - thumbSize - value
            } else {
                value
            }
        adapter.scrollTo(rawPosition / scrollScale)
    }

    private val dragMutex = Mutex()

    fun onDragDelta(offset: Offset) {
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            dragMutex.withLock {
                val dragDelta = if (isVertical) offset.y else offset.x
                val maxScrollPosition = adapter.maxScrollOffset * scrollScale
                val currentPosition = position
                val targetPosition =
                    (currentPosition + dragDelta + unscrolledDragDistance).coerceIn(0.0, maxScrollPosition)
                val sliderDelta = targetPosition - currentPosition

                val newPos = position + sliderDelta
                setPosition(newPos)
                unscrolledDragDistance += dragDelta - sliderDelta
            }
        }
    }
}
