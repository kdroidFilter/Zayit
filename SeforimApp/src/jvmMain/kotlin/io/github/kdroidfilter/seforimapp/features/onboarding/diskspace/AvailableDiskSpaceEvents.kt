package io.github.kdroidfilter.seforimapp.features.onboarding.diskspace

sealed interface AvailableDiskSpaceEvents {
    data object Refresh : AvailableDiskSpaceEvents
}
