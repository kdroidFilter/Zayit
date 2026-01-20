package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.ActionButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.styling.IconButtonColors
import org.jetbrains.jewel.ui.component.styling.IconButtonStyle
import org.jetbrains.jewel.ui.theme.iconButtonStyle

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectableIconButtonWithToolip(
    toolTipText: String,
    onClick: () -> Unit,
    isSelected: Boolean,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    iconDescription: String = "",
    enabled: Boolean = true,
    shortcutHint: String? = null,
) {
    Tooltip({
        if (shortcutHint.isNullOrBlank()) {
            Text(toolTipText)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(toolTipText)

                Text(shortcutHint, color = JewelTheme.globalColors.text.disabled)
            }
        }
    }) {
        ActionButton(
            onClick = onClick,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .pointerHoverIcon(PointerIcon.Hand),
            focusable = false,
            enabled = enabled,
            style =
                IconButtonStyle(
                    colors =
                        IconButtonColors(
                            foregroundSelectedActivated =
                                JewelTheme.iconButtonStyle.colors.foregroundSelectedActivated,
                            background =
                                if (isSelected) {
                                    JewelTheme.iconButtonStyle.colors.backgroundSelected
                                } else {
                                    JewelTheme.iconButtonStyle.colors.background
                                },
                            backgroundDisabled = JewelTheme.iconButtonStyle.colors.backgroundDisabled,
                            backgroundSelected = JewelTheme.iconButtonStyle.colors.backgroundSelected,
                            backgroundSelectedActivated =
                                JewelTheme.iconButtonStyle.colors.backgroundSelectedActivated,
                            backgroundFocused = JewelTheme.iconButtonStyle.colors.backgroundFocused,
                            backgroundPressed = JewelTheme.iconButtonStyle.colors.backgroundPressed,
                            backgroundHovered =
                                if (isSelected) {
                                    JewelTheme.iconButtonStyle.colors.backgroundSelected
                                } else {
                                    JewelTheme.iconButtonStyle.colors.backgroundHovered
                                },
                            border = JewelTheme.iconButtonStyle.colors.border,
                            borderDisabled = JewelTheme.iconButtonStyle.colors.borderDisabled,
                            borderSelected = JewelTheme.iconButtonStyle.colors.borderSelected,
                            borderSelectedActivated =
                                JewelTheme.iconButtonStyle.colors.borderSelectedActivated,
                            borderFocused = JewelTheme.iconButtonStyle.colors.borderFocused,
                            borderPressed = JewelTheme.iconButtonStyle.colors.borderPressed,
                            borderHovered =
                                if (isSelected) {
                                    JewelTheme.iconButtonStyle.colors.borderSelected
                                } else {
                                    JewelTheme.iconButtonStyle.colors.borderHovered
                                },
                        ),
                    metrics = JewelTheme.iconButtonStyle.metrics,
                ),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(4.dp),
            ) {
                Icon(
                    icon,
                    iconDescription,
                    tint = if (enabled)JewelTheme.globalColors.text.selected else JewelTheme.globalColors.text.disabled,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    label,
                    color = if (enabled) JewelTheme.globalColors.text.normal else JewelTheme.globalColors.text.disabled,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 10.sp,
                )
            }
        }
    }
}
