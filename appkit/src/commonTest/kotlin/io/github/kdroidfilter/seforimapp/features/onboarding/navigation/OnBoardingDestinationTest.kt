package io.github.kdroidfilter.seforimapp.features.onboarding.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class OnBoardingDestinationTest {
    @Test
    fun `InitScreen is a valid OnBoardingDestination`() {
        val destination: OnBoardingDestination = OnBoardingDestination.InitScreen
        assertIs<OnBoardingDestination>(destination)
    }

    @Test
    fun `LicenceScreen is a valid OnBoardingDestination`() {
        val destination: OnBoardingDestination = OnBoardingDestination.LicenceScreen
        assertIs<OnBoardingDestination>(destination)
    }

    @Test
    fun `AvailableDiskSpaceScreen is a valid OnBoardingDestination`() {
        val destination: OnBoardingDestination = OnBoardingDestination.AvailableDiskSpaceScreen
        assertIs<OnBoardingDestination>(destination)
    }

    @Test
    fun `TypeOfInstallationScreen is a valid OnBoardingDestination`() {
        val destination: OnBoardingDestination = OnBoardingDestination.TypeOfInstallationScreen
        assertIs<OnBoardingDestination>(destination)
    }

    @Test
    fun `DatabaseOnlineInstallerScreen is a valid OnBoardingDestination`() {
        val destination: OnBoardingDestination = OnBoardingDestination.DatabaseOnlineInstallerScreen
        assertIs<OnBoardingDestination>(destination)
    }

    @Test
    fun `OfflineFileSelectionScreen is a valid OnBoardingDestination`() {
        val destination: OnBoardingDestination = OnBoardingDestination.OfflineFileSelectionScreen
        assertIs<OnBoardingDestination>(destination)
    }

    @Test
    fun `ExtractScreen is a valid OnBoardingDestination`() {
        val destination: OnBoardingDestination = OnBoardingDestination.ExtractScreen
        assertIs<OnBoardingDestination>(destination)
    }

    @Test
    fun `VersionVerificationScreen is a valid OnBoardingDestination`() {
        val destination: OnBoardingDestination = OnBoardingDestination.VersionVerificationScreen
        assertIs<OnBoardingDestination>(destination)
    }

    @Test
    fun `UserProfilScreen is a valid OnBoardingDestination`() {
        val destination: OnBoardingDestination = OnBoardingDestination.UserProfilScreen
        assertIs<OnBoardingDestination>(destination)
    }

    @Test
    fun `RegionConfigScreen is a valid OnBoardingDestination`() {
        val destination: OnBoardingDestination = OnBoardingDestination.RegionConfigScreen
        assertIs<OnBoardingDestination>(destination)
    }

    @Test
    fun `FinishScreen is a valid OnBoardingDestination`() {
        val destination: OnBoardingDestination = OnBoardingDestination.FinishScreen
        assertIs<OnBoardingDestination>(destination)
    }

    @Test
    fun `data objects are singletons`() {
        assertEquals(OnBoardingDestination.InitScreen, OnBoardingDestination.InitScreen)
        assertEquals(OnBoardingDestination.LicenceScreen, OnBoardingDestination.LicenceScreen)
        assertEquals(OnBoardingDestination.FinishScreen, OnBoardingDestination.FinishScreen)
    }

    @Test
    fun `different destinations are not equal`() {
        assertNotEquals(
            OnBoardingDestination.InitScreen as OnBoardingDestination,
            OnBoardingDestination.FinishScreen as OnBoardingDestination,
        )
    }
}
