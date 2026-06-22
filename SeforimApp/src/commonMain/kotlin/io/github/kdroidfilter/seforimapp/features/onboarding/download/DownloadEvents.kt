package io.github.kdroidfilter.seforimapp.features.onboarding.download

sealed interface DownloadEvents {
    data object Start : DownloadEvents
}
