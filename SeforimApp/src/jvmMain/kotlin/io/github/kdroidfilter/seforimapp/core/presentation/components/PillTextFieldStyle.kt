package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.TextFieldMetrics
import org.jetbrains.jewel.ui.component.styling.TextFieldStyle
import org.jetbrains.jewel.ui.theme.textFieldStyle

/** The theme's text field, pill-shaped, as the search bars (Google's). */
@Composable
fun rememberPillTextFieldStyle(): TextFieldStyle {
    val base = JewelTheme.textFieldStyle
    return remember(base) {
        TextFieldStyle(
            colors = base.colors,
            metrics =
                TextFieldMetrics(
                    borderWidth = base.metrics.borderWidth,
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    cornerSize = CornerSize(50),
                    minSize = base.metrics.minSize,
                ),
            iconButtonStyle = base.iconButtonStyle,
        )
    }
}
