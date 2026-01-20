package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * Small rounded badge to display counts (e.g., search results) consistently across the app.
 */
@Composable
fun CountBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(JewelTheme.globalColors.panelBackground)
                .border(1.dp, JewelTheme.globalColors.borders.disabled, shape),
    ) {
        Text(
            text = count.toString(),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = JewelTheme.globalColors.text.disabled,
        )
    }
}
