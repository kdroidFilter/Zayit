package io.github.kdroidfilter.seforimapp.features.onboarding.ui.extract

sealed interface ExtractEvents {
    /** Start extraction if a pending .zst path has been provided. */
    data object StartIfPending : ExtractEvents
}

