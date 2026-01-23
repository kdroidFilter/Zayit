@file:OptIn(ExperimentalJewelApi::class, ExperimentalFoundationApi::class)

package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.icons.Telescope
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip

@Composable
fun CustomToggleableChip(
    checked: Boolean,
    onClick: (Boolean) -> Unit,
    tooltipText: String,
    modifier: Modifier = Modifier,
    withPadding: Boolean = true,
) {
    // Style inspiré de l'IntegratedSwitch
    Tooltip(
        tooltip = {
            Text(
                text = tooltipText,
                fontSize = 13.sp,
            )
        },
    ) {
        Box(
            modifier =
                modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(JewelTheme.globalColors.panelBackground)
                    .border(
                        width = 1.dp,
                        color = JewelTheme.globalColors.borders.normal,
                        shape = RoundedCornerShape(20.dp),
                    ).then(if (withPadding) Modifier.padding(2.dp) else Modifier),
        ) {
            // Bouton avec icône Telescope au lieu du texte
            TelescopeIconButton(
                isSelected = checked,
                onClick = { onClick(!checked) },
                withPadding = withPadding,
            )
        }
    }
}

@Composable
fun TelescopeIconButton(
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    withPadding: Boolean = true,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF0E639C) else Color.Transparent,
        animationSpec = tween(200),
        label = "backgroundColor",
    )

    val iconColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color(0xFFCCCCCC),
        animationSpec = tween(200),
        label = "iconColor",
    )

    Box(
        modifier =
            modifier
                .pointerHoverIcon(PointerIcon.Hand)
                .clip(RoundedCornerShape(18.dp))
                .background(backgroundColor)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }
                .then(if (withPadding) Modifier.padding(horizontal = 8.dp, vertical = 4.dp) else Modifier)
                .defaultMinSize(minWidth = 32.dp, minHeight = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Telescope,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(16.dp),
        )
    }
}
