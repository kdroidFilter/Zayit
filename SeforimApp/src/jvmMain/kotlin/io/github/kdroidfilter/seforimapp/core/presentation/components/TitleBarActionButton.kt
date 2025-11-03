package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icon.IconKey

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TitleBarActionButton(
    key: IconKey,
    onClick: () -> Unit,
    contentDescription: String,
    tooltipText: String,
    enabled: Boolean = true,
    ) {
    IconActionButton(
        key = key,
        onClick = onClick,
        enabled = enabled,
        contentDescription = contentDescription,
        tooltip = {
            Text(tooltipText)
        },
        modifier = Modifier.width(40.dp).fillMaxHeight()
    )
}
