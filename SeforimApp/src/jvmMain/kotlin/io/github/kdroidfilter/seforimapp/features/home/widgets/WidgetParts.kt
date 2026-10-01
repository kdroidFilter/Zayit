package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

// The parts the text widgets are made of: a title, rows lit on hover, lists as long as the card holds.

/** A text widget's title, with an optional link at the end ("show all"). */
@Composable
internal fun WidgetTitle(
    title: String,
    modifier: Modifier = Modifier,
    end: @Composable () -> Unit = {},
) {
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
        end()
    }
}

/**
 * Its children from the top, as many as fit whole; the others aren't shown. [spread], the room left is shared between
 * the ones shown, above, between and below them.
 */
@Composable
internal fun FitColumn(
    modifier: Modifier = Modifier,
    spacing: Dp = 0.dp,
    spread: Boolean = false,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        var y = 0
        val placed =
            buildList {
                for (measurable in measurables) {
                    val placeable = measurable.measure(loose)
                    if (y + placeable.height > constraints.maxHeight) break
                    add(y to placeable)
                    y += placeable.height + gap
                }
            }
        val used = placed.lastOrNull()?.let { (top, placeable) -> top + placeable.height } ?: 0
        val share = if (spread) (constraints.maxHeight - used) / (placed.size + 1) else 0
        layout(constraints.maxWidth, constraints.maxHeight) {
            placed.forEachIndexed { i, (top, placeable) -> placeable.placeRelative(0, top + share * (i + 1)) }
        }
    }
}

/** A rounded area lit on hover, with a hand cursor where it can be clicked; [tinted] shows its area at rest. */
@Composable
internal fun HoverBox(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    tinted: Boolean = false,
    content: @Composable () -> Unit,
) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val shape = RoundedCornerShape(10.dp)
    val tint = JewelTheme.globalColors.text.normal
    val alpha =
        when {
            hovered && onClick != null -> 0.10f
            tinted -> 0.05f
            else -> 0f
        }
    Box(
        modifier
            .clip(shape)
            .background(if (alpha > 0f) tint.copy(alpha = alpha) else Color.Transparent)
            .then(
                if (onClick != null) {
                    Modifier.hoverable(hover).pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick)
                } else {
                    Modifier
                },
            ),
    ) { content() }
}
