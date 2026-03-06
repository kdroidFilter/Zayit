package io.github.kdroidfilter.seforimapp.core.presentation.theme

import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.intui.standalone.styling.dark
import org.jetbrains.jewel.intui.standalone.styling.light
import org.jetbrains.jewel.intui.standalone.theme.dark
import org.jetbrains.jewel.intui.standalone.theme.light
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.ui.component.styling.TooltipColors
import org.jetbrains.jewel.ui.component.styling.TooltipStyle

/**
 * Builds a [ComponentStyling] for the Classic theme with default Jewel styling
 * and custom tooltip colors for the light variant.
 */
fun classicComponentStyling(isDark: Boolean): ComponentStyling =
    if (isDark) {
        ComponentStyling.dark()
    } else {
        ComponentStyling.light(
            tooltipStyle =
                TooltipStyle.light(
                    intUiTooltipColors =
                        TooltipColors.light(
                            backgroundColor = IntUiLightTheme.colors.grayOrNull(13) ?: Color.White,
                            contentColor = IntUiLightTheme.colors.grayOrNull(2) ?: Color.Black,
                            borderColor = IntUiLightTheme.colors.grayOrNull(9) ?: Color.Gray,
                        ),
                ),
        )
    }
