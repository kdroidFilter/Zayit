package io.github.kdroidfilter.seforimapp.core.presentation.theme

import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.intui.standalone.styling.Default
import org.jetbrains.jewel.intui.standalone.styling.Editor
import org.jetbrains.jewel.intui.standalone.styling.dark
import org.jetbrains.jewel.intui.standalone.styling.light
import org.jetbrains.jewel.intui.standalone.theme.dark
import org.jetbrains.jewel.intui.standalone.theme.light
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.ui.component.styling.ComboBoxColors
import org.jetbrains.jewel.ui.component.styling.ComboBoxStyle
import org.jetbrains.jewel.ui.component.styling.SimpleListItemColors
import org.jetbrains.jewel.ui.component.styling.SimpleListItemStyle
import org.jetbrains.jewel.ui.component.styling.TabColors
import org.jetbrains.jewel.ui.component.styling.TabStyle
import org.jetbrains.jewel.ui.component.styling.TooltipColors
import org.jetbrains.jewel.ui.component.styling.TooltipStyle

/**
 * Builds a [ComponentStyling] for the Classic theme with default Jewel styling
 * and custom tooltip colors for the light variant.
 */
fun classicComponentStyling(
    isDark: Boolean,
    accent: Color,
): ComponentStyling =
    if (isDark) {
        ComponentStyling.dark(
            defaultButtonStyle = accentDefaultButtonStyleDark(accent),
            menuStyle = accentMenuStyleDark(accent),
            dropdownStyle = accentDropdownStyleDark(accent),
            undecoratedDropdownStyle = accentUndecoratedDropdownStyleDark(accent),
            comboBoxStyle =
                ComboBoxStyle.Default.dark(
                    colors = ComboBoxColors.Default.dark(borderFocused = accent),
                ),
            simpleListItemStyle =
                SimpleListItemStyle.dark(
                    colors = SimpleListItemColors.dark(backgroundSelectedActive = accent.copy(alpha = 0.15f)),
                ),
            defaultTabStyle =
                TabStyle.Default.dark(
                    colors = TabColors.Default.dark(underlineSelected = accent),
                ),
            editorTabStyle =
                TabStyle.Editor.dark(
                    colors = TabColors.Editor.dark(underlineSelected = accent),
                ),
        )
    } else {
        ComponentStyling.light(
            defaultButtonStyle = accentDefaultButtonStyleLight(accent),
            menuStyle = accentMenuStyleLight(accent),
            dropdownStyle = accentDropdownStyleLight(accent),
            undecoratedDropdownStyle = accentUndecoratedDropdownStyleLight(accent),
            comboBoxStyle =
                ComboBoxStyle.Default.light(
                    colors = ComboBoxColors.Default.light(borderFocused = accent),
                ),
            simpleListItemStyle =
                SimpleListItemStyle.light(
                    colors = SimpleListItemColors.light(backgroundSelectedActive = accent.copy(alpha = 0.12f)),
                ),
            defaultTabStyle =
                TabStyle.Default.light(
                    colors = TabColors.Default.light(underlineSelected = accent),
                ),
            editorTabStyle =
                TabStyle.Editor.light(
                    colors = TabColors.Editor.light(underlineSelected = accent),
                ),
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
