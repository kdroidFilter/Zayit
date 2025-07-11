package io.github.kdroidfilter.seforimapp.core.presentation.theme

import androidx.compose.runtime.Composable
import io.github.kdroidfilter.platformtools.darkmodedetector.isSystemInDarkMode

enum class IntUiThemes {
    Light,
    Dark,
    System;

    /**
     * Determines if the current theme corresponds to a dark mode.
     * 
     * This function checks if a theme is in dark mode considering the following cases:
     * - For [Light]: always returns `false`
     * - For [Dark]: always returns `true`
     * - For [System]: checks if the device's system is in dark mode
     *
     * @return `true` if the current theme is in dark mode, `false` otherwise
     */
    @Composable
    fun isDark(isSystemInDarkMode : Boolean): Boolean = (if (this == System) isSystemInDarkMode else this) == Dark

}