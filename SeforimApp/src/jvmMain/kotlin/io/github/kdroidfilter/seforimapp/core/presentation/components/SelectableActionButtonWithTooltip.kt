package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupPositionProvider
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.ActionButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.styling.IconButtonColors
import org.jetbrains.jewel.ui.component.styling.IconButtonStyle
import org.jetbrains.jewel.ui.theme.iconButtonStyle
import org.jetbrains.jewel.ui.theme.tooltipStyle

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
    val compactMode by AppSettings.compactModeFlow.collectAsState()
    val isIslands = ThemeUtils.isIslandsStyle()
    val iconSize = if (compactMode) 22.dp else 24.dp
    val buttonPadding =
        when {
            compactMode -> 2.dp
            isIslands -> 2.dp
            else -> 4.dp
        }

    val barPosition = LocalVerticalBarPosition.current
    val density = LocalDensity.current
    val sideTooltipPlacement: TooltipPlacement? =
        if (barPosition == null) {
            null
        } else {
            remember(barPosition, density) {
                val gap = with(density) { 4.dp.roundToPx() }
                object : TooltipPlacement {
                    @Composable
                    override fun positionProvider(cursorPosition: Offset): PopupPositionProvider =
                        object : PopupPositionProvider {
                            override fun calculatePosition(
                                anchorBounds: IntRect,
                                windowSize: IntSize,
                                layoutDirection: LayoutDirection,
                                popupContentSize: IntSize,
                            ): IntOffset {
                                val y =
                                    (anchorBounds.top + (anchorBounds.height - popupContentSize.height) / 2)
                                        .coerceIn(
                                            0,
                                            (windowSize.height - popupContentSize.height).coerceAtLeast(0),
                                        )
                                // In RTL, Start is physically on the right and End on the left.
                                val isPhysicallyOnRight =
                                    (barPosition == VerticalLateralBarPosition.Start && layoutDirection == LayoutDirection.Rtl) ||
                                        (barPosition == VerticalLateralBarPosition.End && layoutDirection == LayoutDirection.Ltr)
                                val x =
                                    if (isPhysicallyOnRight) {
                                        anchorBounds.left - popupContentSize.width - gap
                                    } else {
                                        anchorBounds.right + gap
                                    }
                                return IntOffset(x, y)
                            }
                        }
                }
            }
        }

    val baseStyle = JewelTheme.iconButtonStyle
    val buttonStyle =
        remember(isSelected, baseStyle) {
            val c = baseStyle.colors
            IconButtonStyle(
                colors =
                    IconButtonColors(
                        foregroundSelectedActivated = c.foregroundSelectedActivated,
                        background = if (isSelected) c.backgroundSelected else c.background,
                        backgroundDisabled = c.backgroundDisabled,
                        backgroundSelected = c.backgroundSelected,
                        backgroundSelectedActivated = c.backgroundSelectedActivated,
                        backgroundFocused = c.backgroundFocused,
                        backgroundPressed = c.backgroundPressed,
                        backgroundHovered = if (isSelected) c.backgroundSelected else c.backgroundHovered,
                        border = c.border,
                        borderDisabled = c.borderDisabled,
                        borderSelected = c.borderSelected,
                        borderSelectedActivated = c.borderSelectedActivated,
                        borderFocused = c.borderFocused,
                        borderPressed = c.borderPressed,
                        borderHovered = if (isSelected) c.borderSelected else c.borderHovered,
                    ),
                metrics = baseStyle.metrics,
            )
        }

    Tooltip(
        modifier = modifier,
        tooltip = {
            if (shortcutHint.isNullOrBlank()) {
                Text(toolTipText)
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(toolTipText)
                    Text(shortcutHint, color = JewelTheme.globalColors.text.disabled)
                }
            }
        },
        tooltipPlacement = sideTooltipPlacement ?: JewelTheme.tooltipStyle.metrics.placement,
    ) {
        ActionButton(
            onClick = onClick,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (compactMode) Modifier.aspectRatio(1f) else Modifier.height(64.dp),
                    ).pointerHoverIcon(PointerIcon.Hand),
            focusable = false,
            enabled = enabled,
            style = buttonStyle,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(buttonPadding),
            ) {
                Icon(
                    icon,
                    iconDescription,
                    tint = if (enabled) JewelTheme.globalColors.text.selected else JewelTheme.globalColors.text.disabled,
                    modifier = Modifier.size(iconSize),
                )
                if (!compactMode) {
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
}
