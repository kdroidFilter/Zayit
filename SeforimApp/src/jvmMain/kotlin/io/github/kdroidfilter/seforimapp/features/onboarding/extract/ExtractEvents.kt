package io.github.kdroidfilter.seforimapp.features.onboarding.extract

sealed interface ExtractEvents {
    /** Start extraction if a pending .zst path has been provided. */
    data object StartIfPending : ExtractEvents
}
