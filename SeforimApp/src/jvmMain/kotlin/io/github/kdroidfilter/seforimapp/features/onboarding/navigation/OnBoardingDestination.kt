package io.github.kdroidfilter.seforimapp.features.onboarding.navigation

import kotlinx.serialization.Serializable

@Serializable
enum class InstallType {
    ONLINE,
    OFFLINE
}

@Serializable
sealed interface OnBoardingDestination {

    @Serializable
    data class InitScreen(val selection : InstallType) : OnBoardingDestination

    @Serializable
    data object DatabaseOnlineInstallerScreen : OnBoardingDestination

    @Serializable
    data object DatabaseOfflineInstallerScreen : OnBoardingDestination

    @Serializable
    data object UserProfilScreen : OnBoardingDestination

    @Serializable
    data object RegionConfigScreen : OnBoardingDestination

    @Serializable
    data object FinishScreen : OnBoardingDestination
}