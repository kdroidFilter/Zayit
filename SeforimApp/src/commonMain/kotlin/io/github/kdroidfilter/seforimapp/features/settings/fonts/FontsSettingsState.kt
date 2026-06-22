package io.github.kdroidfilter.seforimapp.features.settings.fonts

import androidx.compose.runtime.Immutable

@Immutable
data class FontsSettingsState(
    val bookFontCode: String = "notoserifhebrew",
    val commentaryFontCode: String = "frankruhllibre",
    val targumFontCode: String = "taameyashkenaz",
    val sourceFontCode: String = "frankruhllibre",
) {
    companion object {
        val preview =
            FontsSettingsState(
                bookFontCode = "notoserifhebrew",
                commentaryFontCode = "frankruhllibre",
                targumFontCode = "taameyashkenaz",
                sourceFontCode = "frankruhllibre",
            )
    }
}
