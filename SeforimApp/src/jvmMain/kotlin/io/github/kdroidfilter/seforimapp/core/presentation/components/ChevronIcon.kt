package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.icons.ChevronDown
import io.github.kdroidfilter.seforimapp.icons.ChevronRight
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon

/**
 * Reusable chevron icon that switches between right/down variants based on [expanded].
 *
 * Usage examples:
 * - Category list: ChevronIcon(expanded = isExpanded)
 * - TOC list with reserved width: ChevronIcon(expanded = isExpanded, modifier = Modifier.height(12.dp).width(24.dp))
 */
@Composable
fun ChevronIcon(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
    tint: Color = JewelTheme.globalColors.text.normal,
    contentDescription: String? = "",
) {
    Icon(
        if (expanded) ChevronDown else ChevronRight,
        contentDescription = contentDescription,
        modifier = if (modifier == Modifier) Modifier.size(size) else modifier,
        tint = tint,
    )
}
