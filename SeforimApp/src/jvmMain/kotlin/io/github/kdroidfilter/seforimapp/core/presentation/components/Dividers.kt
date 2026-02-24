package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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

@Composable
fun HorizontalDivider(
    color: Color = JewelTheme.globalColors.borders.disabled,
    modifier: Modifier = Modifier,
) {
    Divider(
        orientation = Orientation.Horizontal,
        modifier =
            modifier
                .fillMaxWidth()
                .width(1.dp)
                .padding(bottom = 4.dp),
        color = color,
    )
}
