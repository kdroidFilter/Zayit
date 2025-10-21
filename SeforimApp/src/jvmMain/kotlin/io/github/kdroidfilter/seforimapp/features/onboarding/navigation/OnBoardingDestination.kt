package io.github.kdroidfilter.seforimapp.features.onboarding.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface OnBoardingDestination {
    @Serializable
    data object InitScreen : OnBoardingDestination

    @Serializable
    data object LicenceScreen : OnBoardingDestination

    @Serializable
    data object AvailableDiskSpaceScreen : OnBoardingDestination

    @Serializable
    data object TypeOfInstallationScreen : OnBoardingDestination

    @Serializable
    data object DatabaseOnlineInstallerScreen : OnBoardingDestination

    @Serializable
    data object ExtractScreen : OnBoardingDestination

    @Serializable
    data object UserProfilScreen : OnBoardingDestination

    @Serializable
    data object RegionConfigScreen : OnBoardingDestination

    @Serializable
    data object FinishScreen : OnBoardingDestination
}
