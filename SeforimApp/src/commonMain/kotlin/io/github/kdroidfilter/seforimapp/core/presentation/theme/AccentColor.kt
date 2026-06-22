package io.github.kdroidfilter.seforimapp.core.presentation.theme

/**
 * Predefined accent color presets for the application theme.
 *
 * The enum (the stored preference) is common; the actual Color resolution — which uses Jewel's
 * palette and the OS accent color — lives in the jvm theme layer as extension functions
 * (forMode / resolveColor / displayColor in AccentColorColors.kt).
 */
enum class AccentColor {
    System,
    Default,
    Teal,
    Green,
    Gold,
}
