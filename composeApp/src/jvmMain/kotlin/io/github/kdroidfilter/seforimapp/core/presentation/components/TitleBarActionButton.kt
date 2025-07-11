package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import jdk.jfr.Description
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icon.IconKey

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TitleBarActionButton(
    tooltipText: String,
    onClick: () -> Unit,
    iconKey: IconKey,
    iconDescription: String
) {
    IconActionButton(
        key = iconKey,
        onClick = onClick,
        contentDescription = iconDescription,
        tooltip = {
            Text(tooltipText)
        }
    )
}