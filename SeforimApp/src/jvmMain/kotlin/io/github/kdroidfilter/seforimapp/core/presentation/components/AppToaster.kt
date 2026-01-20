package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dokar.sonner.ToastWidthPolicy
import com.dokar.sonner.Toaster
import com.dokar.sonner.ToasterState
import org.jetbrains.jewel.foundation.theme.JewelTheme

/**
 * App-wide toaster overlay styled to match Jewel/IDEA look & feel.
 *
 * Use this composable anywhere you need to render toast notifications with the
 * same visual style. Create/remember the `ToasterState` using Sonner's
 * `rememberToasterState()` in your screen and pass it here.
 */
@Composable
fun AppToaster(state: ToasterState) {
    Toaster(
        state = state,
        darkTheme = JewelTheme.isDark,
        alignment = Alignment.BottomEnd,
        expanded = true,
        showCloseButton = true,
        contentPadding = { PaddingValues(horizontal = 12.dp, vertical = 10.dp) },
        containerPadding = PaddingValues(horizontal = 64.dp, vertical = 40.dp),
        widthPolicy = { ToastWidthPolicy(max = 420.dp) },
        elevation = 6.dp,
        shadowAmbientColor = Color.Black.copy(alpha = 0.18f),
        shadowSpotColor = Color.Black.copy(alpha = 0.24f),
        contentColor = { JewelTheme.globalColors.text.normal },
        border = { BorderStroke(1.dp, JewelTheme.globalColors.borders.disabled) },
        background = { SolidColor(JewelTheme.globalColors.panelBackground) },
        shape = { RectangleShape },
        offset = IntOffset(0, 0),
    )
}
