package io.github.kdroidfilter.seforimapp.core.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import org.jetbrains.jewel.intui.standalone.styling.Default
import org.jetbrains.jewel.intui.standalone.styling.Undecorated
import org.jetbrains.jewel.intui.standalone.styling.dark
import org.jetbrains.jewel.intui.standalone.styling.light
import org.jetbrains.jewel.ui.component.styling.ButtonColors
import org.jetbrains.jewel.ui.component.styling.ButtonStyle
import org.jetbrains.jewel.ui.component.styling.DropdownColors
import org.jetbrains.jewel.ui.component.styling.DropdownStyle
import org.jetbrains.jewel.ui.component.styling.MenuColors
import org.jetbrains.jewel.ui.component.styling.MenuItemColors
import org.jetbrains.jewel.ui.component.styling.MenuStyle

/** Builds a [MenuStyle] with the accent color used as the selection highlight. */
fun accentMenuStyleLight(accent: Color): MenuStyle =
    MenuStyle.light(
        colors =
            MenuColors.light(
                itemColors =
                    MenuItemColors.light(
                        backgroundFocused = accent.copy(alpha = 0.12f),
                        backgroundHovered = accent.copy(alpha = 0.12f),
                    ),
            ),
    )

fun accentMenuStyleDark(accent: Color): MenuStyle =
    MenuStyle.dark(
        colors =
            MenuColors.dark(
                itemColors =
                    MenuItemColors.dark(
                        backgroundFocused = accent.copy(alpha = 0.15f),
                        backgroundHovered = accent.copy(alpha = 0.15f),
                    ),
            ),
    )

fun accentDropdownStyleLight(accent: Color): DropdownStyle =
    DropdownStyle.Default.light(
        colors = DropdownColors.Default.light(borderFocused = accent),
        menuStyle = accentMenuStyleLight(accent),
    )

fun accentDropdownStyleDark(accent: Color): DropdownStyle =
    DropdownStyle.Default.dark(
        colors = DropdownColors.Default.dark(borderFocused = accent),
        menuStyle = accentMenuStyleDark(accent),
    )

fun accentUndecoratedDropdownStyleLight(accent: Color): DropdownStyle =
    DropdownStyle.Undecorated.light(menuStyle = accentMenuStyleLight(accent))

fun accentUndecoratedDropdownStyleDark(accent: Color): DropdownStyle =
    DropdownStyle.Undecorated.dark(menuStyle = accentMenuStyleDark(accent))

/** Darkens a color by the given [factor] (0f = unchanged, 1f = black). */
private fun Color.darken(factor: Float): Color =
    Color(
        red = red * (1f - factor),
        green = green * (1f - factor),
        blue = blue * (1f - factor),
        alpha = alpha,
    )

/** Lightens a color by the given [factor] (0f = unchanged, 1f = white). */
private fun Color.lighten(factor: Float): Color =
    Color(
        red = red + (1f - red) * factor,
        green = green + (1f - green) * factor,
        blue = blue + (1f - blue) * factor,
        alpha = alpha,
    )

fun accentDefaultButtonStyleLight(accent: Color): ButtonStyle =
    ButtonStyle.Default.light(
        colors =
            ButtonColors.Default.light(
                background = SolidColor(accent),
                backgroundFocused = SolidColor(accent),
                backgroundPressed = SolidColor(accent.darken(0.15f)),
                backgroundHovered = SolidColor(accent.lighten(0.10f)),
                border = SolidColor(accent),
            ),
    )

fun accentDefaultButtonStyleDark(accent: Color): ButtonStyle =
    ButtonStyle.Default.dark(
        colors =
            ButtonColors.Default.dark(
                background = SolidColor(accent),
                backgroundFocused = SolidColor(accent),
                backgroundPressed = SolidColor(accent.darken(0.20f)),
                backgroundHovered = SolidColor(accent.lighten(0.10f)),
                border = SolidColor(accent),
            ),
    )
