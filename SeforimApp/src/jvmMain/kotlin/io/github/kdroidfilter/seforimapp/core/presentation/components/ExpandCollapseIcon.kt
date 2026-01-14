package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.icons.ChevronDown
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon

/**
 * Animated chevron icon that points down when collapsed and up when expanded.
 * Uses rotation animation for smooth transition.
 */
@Composable
fun ExpandCollapseIcon(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
    tint: Color = JewelTheme.globalColors.text.normal,
    contentDescription: String? = null
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "chevron_rotation"
    )

    Icon(
        ChevronDown,
        contentDescription = contentDescription,
        modifier = (if (modifier == Modifier) Modifier.size(size) else modifier).rotate(rotation),
        tint = tint
    )
}
