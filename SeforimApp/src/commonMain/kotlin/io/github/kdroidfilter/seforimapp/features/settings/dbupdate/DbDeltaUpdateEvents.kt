package io.github.kdroidfilter.seforimapp.features.settings.dbupdate

sealed interface DbDeltaUpdateEvents {
    /** User pressed the "Check for updates" button. */
    data object CheckAndApplyClicked : DbDeltaUpdateEvents

    /** Dismiss the current message / error after the user has acknowledged it. */
    data object ClearMessage : DbDeltaUpdateEvents
}
