package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.icon.IconKey

/** A small round action of a dropdown row; an invisible one still keeps its slot, so the rows line up. */
@Composable
internal fun ActionIcon(
    key: IconKey,
    visible: Boolean,
    onClick: () -> Unit,
    contentDescription: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val accent = JewelTheme.globalColors.outlines.focused
    val hoverBg = if (isHovered && visible) accent.copy(alpha = 0.12f) else Color.Transparent

    Box(
        modifier =
            Modifier
                .size(18.dp)
                .hoverable(interactionSource)
                .background(hoverBg, CircleShape)
                .clickable(interactionSource = interactionSource, indication = null) {
                    // Invisible icons still reserve their slot for alignment — don't let them click
                    if (visible) onClick()
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            key = key,
            contentDescription = contentDescription,
            modifier = Modifier.size(14.dp),
            tint = if (visible) JewelTheme.globalColors.text.normal else Color.Transparent,
        )
    }
}
