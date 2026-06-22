package io.github.kdroidfilter.seforimapp.features.settings.general

import androidx.compose.runtime.Immutable

@Immutable
data class GeneralSettingsState(
    val databasePath: String? = null,
    val closeTreeOnNewBook: Boolean = false,
    val persistSession: Boolean = true,
    val keepScreenAwakeOnBook: Boolean = true,
) {
    companion object {
        val preview =
            GeneralSettingsState(
                databasePath = "/Users/you/.zayit/seforim.db",
                closeTreeOnNewBook = true,
                persistSession = true,
                keepScreenAwakeOnBook = true,
            )
    }
}
