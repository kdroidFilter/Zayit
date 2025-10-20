package io.github.kdroidfilter.seforimapp.features.settings

data class SettingsState(
    val closedAutomaticallyBookTreePaneOnNewBookSelected: Boolean = false,
    val persistSession: Boolean = false,
    val isVisible: Boolean = false,
    val resetDone: Boolean = false,
    val databasePath: String? = null,
)
