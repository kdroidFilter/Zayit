package io.github.kdroidfilter.seforimapp.features.onboarding.ui.download

sealed interface DownloadEvents {
    data object Start : DownloadEvents
}

