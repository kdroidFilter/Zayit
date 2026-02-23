package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.styling.IconButtonColors
import org.jetbrains.jewel.ui.component.styling.IconButtonStyle
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.theme.iconButtonStyle

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TitleBarActionButton(
    key: IconKey,
    onClick: () -> Unit,
    contentDescription: String,
    tooltipText: String,
    shortcutHint: String? = null,
    enabled: Boolean = true,
) {
    val accent = JewelTheme.globalColors.outlines.focused
    val baseStyle = JewelTheme.iconButtonStyle
    val style = remember(accent, baseStyle) {
        val c = baseStyle.colors
        IconButtonStyle(
            colors = IconButtonColors(
                foregroundSelectedActivated = c.foregroundSelectedActivated,
                background = c.background,
                backgroundDisabled = c.backgroundDisabled,
                backgroundSelected = c.backgroundSelected,
                backgroundSelectedActivated = c.backgroundSelectedActivated,
                backgroundFocused = c.backgroundFocused,
                backgroundPressed = accent.copy(alpha = 0.20f),
                backgroundHovered = accent.copy(alpha = 0.12f),
                border = c.border,
                borderDisabled = c.borderDisabled,
                borderSelected = c.borderSelected,
                borderSelectedActivated = c.borderSelectedActivated,
                borderFocused = c.borderFocused,
                borderPressed = Color.Transparent,
                borderHovered = Color.Transparent,
            ),
            metrics = baseStyle.metrics,
        )
    }

    Tooltip({
        if (shortcutHint.isNullOrBlank()) {
            Text(tooltipText)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tooltipText)
                Text(shortcutHint, color = JewelTheme.globalColors.text.disabled)
            }
        }
    }) {
        IconActionButton(
            key = key,
            onClick = onClick,
            enabled = enabled,
            contentDescription = contentDescription,
            modifier = Modifier.width(40.dp).fillMaxHeight(),
            style = style,
        )
    }
}
