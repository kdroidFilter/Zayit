package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import org.jetbrains.jewel.foundation.theme.JewelTheme

/** The frame shared by the Home widgets: rounded, bordered, black behind. */
@Composable
internal fun WidgetCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(Color.Black)
                .border(1.5.dp, JewelTheme.globalColors.borders.disabled, shape),
        content = content,
    )
}

@Composable
internal fun rememberAccentColor(isDark: Boolean): Color {
    val accentColor by LocalAppGraph.current.mainAppState.accentColor
        .collectAsState()
    return accentColor.resolveColor(isDark)
}
