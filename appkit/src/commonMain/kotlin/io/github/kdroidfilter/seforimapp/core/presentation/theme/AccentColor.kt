package io.github.kdroidfilter.seforimapp.core.presentation.theme

/**
 * Predefined accent color presets for the application theme.
 * [System] uses the OS accent color (falls back to the theme's blue when unavailable).
 * [Default] uses the theme's built-in blue; other entries provide light/dark variants. Each platform resolves them
 * to colors (the desktop's Jewel theme in AccentColors.kt).
 */
enum class AccentColor {
    System,
    Default,
    Teal,
    Green,
    Gold,
}
