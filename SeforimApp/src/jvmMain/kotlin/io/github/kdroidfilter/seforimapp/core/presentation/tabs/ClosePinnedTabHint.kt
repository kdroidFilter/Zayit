package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.theme.tooltipStyle
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.close_pinned_tab_hint

/** Chromium's close-pinned-tab toast: a second Ctrl+W while it shows closes the pinned tab. */
@Composable
fun ClosePinnedTabHint(
    tabsViewModel: TabsViewModel,
    modifier: Modifier = Modifier,
) {
    val visible by tabsViewModel.closePinnedHint.collectAsState()
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        val colors = JewelTheme.tooltipStyle.colors
        Text(
            text = stringResource(Res.string.close_pinned_tab_hint, if (PlatformInfo.isMacOS) "⌘+W" else "Ctrl+W"),
            color = colors.content,
            modifier =
                Modifier
                    .padding(top = 12.dp)
                    .background(colors.background, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}
