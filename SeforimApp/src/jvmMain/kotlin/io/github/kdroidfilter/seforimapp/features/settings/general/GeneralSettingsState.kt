package io.github.kdroidfilter.seforimapp.features.settings.general

import androidx.compose.runtime.Immutable

@Immutable
data class GeneralSettingsState(
    val databasePath: String? = null,
    val closeTreeOnNewBook: Boolean = false,
    val persistSession: Boolean = true,
    val showZmanimWidgets: Boolean = true,
    val useOpenGl: Boolean = false,
    val resetDone: Boolean = false,
) {
    companion object {
        val preview =
            GeneralSettingsState(
                databasePath = "/Users/you/.zayit/seforim.db",
                closeTreeOnNewBook = true,
                persistSession = true,
                showZmanimWidgets = true,
                useOpenGl = false,
                resetDone = false,
            )
    }
}
