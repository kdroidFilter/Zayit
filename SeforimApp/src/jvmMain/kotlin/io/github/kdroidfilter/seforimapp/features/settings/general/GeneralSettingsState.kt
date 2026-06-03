package io.github.kdroidfilter.seforimapp.features.settings.general

import androidx.compose.runtime.Immutable

@Immutable
data class GeneralSettingsState(
    val databasePath: String? = null,
    val closeTreeOnNewBook: Boolean = false,
    val persistSession: Boolean = true,
    val keepScreenAwakeOnBook: Boolean = true,
    val resetDone: Boolean = false,
) {
    companion object {
        val preview =
            GeneralSettingsState(
                databasePath = "/Users/you/.zayit/seforim.db",
                closeTreeOnNewBook = true,
                persistSession = true,
                keepScreenAwakeOnBook = true,
                resetDone = false,
            )
    }
}
