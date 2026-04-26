package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider

@Composable
fun VerticalDivider() {
    Divider(
        orientation = Orientation.Vertical,
        modifier =
            Modifier
                .fillMaxHeight()
                .width(1.dp),
        color = JewelTheme.globalColors.borders.disabled,
    )
}

/**
 * A horizontal divider that automatically adapts to island mode.
 * Pass [modifier] to control width — defaults to full width.
 */
@Composable
fun HorizontalDivider(
    modifier: Modifier = Modifier.fillMaxWidth(),
    color: Color =
        if (ThemeUtils.isIslandsStyle()) {
            val alpha = if (JewelTheme.isDark) 0.35f else 1f
            JewelTheme.globalColors.borders.normal
                .copy(alpha = alpha)
        } else {
            JewelTheme.globalColors.borders.disabled
        },
) {
    Divider(
        orientation = Orientation.Horizontal,
        modifier = modifier.padding(bottom = 4.dp),
        color = color,
    )
}
