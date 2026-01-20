package io.github.kdroidfilter.seforimapp.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme

@Composable
fun PreviewContainer(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        IntUiTheme(isDark = isSystemInDarkTheme()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(JewelTheme.globalColors.panelBackground)
                        .padding(16.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                content()
            }
        }
    }
}
