package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator

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

/** A card whose content is still being computed off the UI thread (see rememberOffMain). */
@Composable
internal fun WidgetCardLoading(modifier: Modifier = Modifier) {
    PanelCard(modifier) {
        CircularProgressIndicator(Modifier.align(Alignment.Center))
    }
}

/** The frame of the text widgets (the calendar, the luach ones): the panel's colour, see-through. */
@Composable
internal fun PanelCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val isDark = JewelTheme.isDark
    val shape = RoundedCornerShape(18.dp)
    val border = if (isDark) JewelTheme.globalColors.borders.disabled else JewelTheme.globalColors.borders.normal
    Box(
        modifier
            .clip(shape)
            .background(JewelTheme.globalColors.panelBackground.copy(alpha = if (isDark) 0.25f else 0.35f), shape)
            .border(1.5.dp, border, shape),
        content = content,
    )
}

@Composable
internal fun rememberAccentColor(isDark: Boolean): Color {
    val accentColor by LocalAppGraph.current.mainAppState.accentColor
        .collectAsState()
    return accentColor.resolveColor(isDark)
}
