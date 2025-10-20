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
    data object InitScreen : OnBoardingDestination

    @Serializable
    data object LicenceScreen : OnBoardingDestination

    @Serializable
    data object TypeOfInstallationScreen : OnBoardingDestination

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