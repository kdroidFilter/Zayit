package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
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
