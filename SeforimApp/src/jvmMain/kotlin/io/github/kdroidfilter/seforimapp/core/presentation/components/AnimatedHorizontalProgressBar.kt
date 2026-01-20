package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.HorizontalProgressBarStyle
import org.jetbrains.jewel.ui.theme.horizontalProgressBarStyle

/**
 * A horizontal progress bar that animates when [value] changes.
 *
 * @param value Target progress (0f..1f). Animation will smoothly reach this value.
 * @param modifier Modifier to be applied to the progress bar.
 * @param style Visual styling for the progress bar.
 * @param animationSpec Interpolation used for progress changes.
 */
@Composable
fun AnimatedHorizontalProgressBar(
    value: Float,
    modifier: Modifier = Modifier,
    style: HorizontalProgressBarStyle = JewelTheme.horizontalProgressBarStyle,
    animationSpec: AnimationSpec<Float> = tween(durationMillis = 400, easing = FastOutSlowInEasing),
) {
    // Clamp the target to a valid range
    val target = value.coerceIn(0f, 1f)

    // Animate the progress towards the target value
    val animatedProgress by animateFloatAsState(
        targetValue = target,
        animationSpec = animationSpec,
        label = "AnimatedHorizontalProgress",
    )

    val colors = style.colors
    val shape = RoundedCornerShape(style.metrics.cornerSize)

    Box(
        modifier
            .defaultMinSize(minHeight = style.metrics.minHeight)
            .clip(shape)
            .drawWithContent {
                // Draw track
                drawRect(color = colors.track)

                // Compute animated width & origin (RTL aware)
                val progressWidth = size.width * animatedProgress
                val progressX = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - progressWidth

                // Use the same corner rounding as the track to keep ends nicely rounded
                val cornerSizePx = style.metrics.cornerSize.toPx(size, Density(density, fontScale))
                val cornerRadius = CornerRadius(cornerSizePx, cornerSizePx)

                drawRoundRect(
                    color = colors.progress,
                    topLeft = Offset(progressX, 0f),
                    size = size.copy(width = progressWidth),
                    cornerRadius = cornerRadius,
                )
            },
    )
}
