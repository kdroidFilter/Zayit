package io.github.kdroidfilter.seforimapp.features.onboarding.ui

sealed class OnBoardingEvents {
    object onFinish : OnBoardingEvents()
    object StartDownload : OnBoardingEvents()
    data class ImportFromZst(val path: String) : OnBoardingEvents()
}
