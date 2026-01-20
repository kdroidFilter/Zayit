package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icon.IconKey

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
        )
    }
}
