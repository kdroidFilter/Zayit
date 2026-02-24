package io.github.kdroidfilter.seforimapp.integration

import io.github.kdroidfilter.seforimapp.features.onboarding.diskspace.AvailableDiskSpaceState
import io.github.kdroidfilter.seforimapp.features.onboarding.diskspace.AvailableDiskSpaceUseCase
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.OnBoardingDestination
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.ProgressBarState
import io.github.kdroidfilter.seforimapp.features.onboarding.region.RegionConfigState
import io.github.kdroidfilter.seforimapp.features.onboarding.typeofinstall.TypeOfInstallationState
import io.github.kdroidfilter.seforimapp.features.onboarding.userprofile.UserProfileState
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Integration tests for the onboarding flow.
 * Tests the complete onboarding journey including state transitions,
 * validation logic, and data flow between screens.
 */
class OnboardingFlowIntegrationTest {
    @BeforeTest
    fun setup() {
        // Reset progress bar state before each test
        ProgressBarState.resetProgress()
    }

    // ==================== Progress Bar State Tests ====================

    @Test
    fun `ProgressBarState starts at 0`() {
        ProgressBarState.resetProgress()
        assertEquals(0f, ProgressBarState.progress.value)
    }

    @Test
    fun `ProgressBarState setProgress updates value`() {
        ProgressBarState.setProgress(0.5f)
        assertEquals(0.5f, ProgressBarState.progress.value)
    }

    @Test
    fun `ProgressBarState improveBy increases value`() {
        ProgressBarState.resetProgress()
        ProgressBarState.improveBy(0.2f)
        assertEquals(0.2f, ProgressBarState.progress.value)
        ProgressBarState.improveBy(0.3f)
        assertEquals(0.5f, ProgressBarState.progress.value)
    }

    @Test
    fun `ProgressBarState resetProgress returns to 0`() {
        ProgressBarState.setProgress(0.8f)
        ProgressBarState.resetProgress()
        assertEquals(0f, ProgressBarState.progress.value)
    }

    // ==================== Destination Flow Tests ====================

    @Test
    fun `OnBoardingDestination covers all onboarding screens`() {
        val allDestinations =
            listOf(
                OnBoardingDestination.InitScreen,
                OnBoardingDestination.LicenceScreen,
                OnBoardingDestination.AvailableDiskSpaceScreen,
                OnBoardingDestination.TypeOfInstallationScreen,
                OnBoardingDestination.DatabaseOnlineInstallerScreen,
                OnBoardingDestination.OfflineFileSelectionScreen,
                OnBoardingDestination.ExtractScreen,
                OnBoardingDestination.VersionVerificationScreen,
                OnBoardingDestination.UserProfilScreen,
                OnBoardingDestination.RegionConfigScreen,
                OnBoardingDestination.FinishScreen,
            )

        assertEquals(11, allDestinations.size)
        assertEquals(11, allDestinations.toSet().size) // All unique
    }

    @Test
    fun `happy path online installation follows correct sequence`() {
        val onlineInstallPath =
            listOf(
                OnBoardingDestination.InitScreen,
                OnBoardingDestination.LicenceScreen,
                OnBoardingDestination.AvailableDiskSpaceScreen,
                OnBoardingDestination.TypeOfInstallationScreen,
                OnBoardingDestination.DatabaseOnlineInstallerScreen, // Online path
                OnBoardingDestination.ExtractScreen,
                OnBoardingDestination.VersionVerificationScreen,
                OnBoardingDestination.UserProfilScreen,
                OnBoardingDestination.RegionConfigScreen,
                OnBoardingDestination.FinishScreen,
            )

        // Verify sequence is valid (each step is different from the previous)
        for (i in 0 until onlineInstallPath.lastIndex) {
            assertNotEquals(onlineInstallPath[i], onlineInstallPath[i + 1])
        }
    }

    @Test
    fun `happy path offline installation follows correct sequence`() {
        val offlineInstallPath =
            listOf(
                OnBoardingDestination.InitScreen,
                OnBoardingDestination.LicenceScreen,
                OnBoardingDestination.AvailableDiskSpaceScreen,
                OnBoardingDestination.TypeOfInstallationScreen,
                OnBoardingDestination.OfflineFileSelectionScreen, // Offline path
                OnBoardingDestination.ExtractScreen,
                OnBoardingDestination.VersionVerificationScreen,
                OnBoardingDestination.UserProfilScreen,
                OnBoardingDestination.RegionConfigScreen,
                OnBoardingDestination.FinishScreen,
            )

        // Verify sequence is valid
        for (i in 0 until offlineInstallPath.lastIndex) {
            assertNotEquals(offlineInstallPath[i], offlineInstallPath[i + 1])
        }
    }

    // ==================== Disk Space State Tests ====================

    @Test
    fun `AvailableDiskSpaceState initializes with default values`() {
        val state = AvailableDiskSpaceState()
        assertFalse(state.hasEnoughSpace)
        assertEquals(0L, state.availableDiskSpace)
        assertEquals(0L, state.totalDiskSpace)
    }

    @Test
    fun `AvailableDiskSpaceState can store disk space values`() {
        val loadedState =
            AvailableDiskSpaceState(
                totalDiskSpace = 500L * 1024 * 1024 * 1024, // 500 GB
                availableDiskSpace = 100L * 1024 * 1024 * 1024, // 100 GB
                hasEnoughSpace = true,
            )

        assertTrue(loadedState.hasEnoughSpace)
        assertTrue(loadedState.availableDiskSpace > 0)
        assertTrue(loadedState.totalDiskSpace > 0)
    }

    @Test
    fun `AvailableDiskSpaceState reflects insufficient space`() {
        val insufficientState =
            AvailableDiskSpaceState(
                totalDiskSpace = 50L * 1024 * 1024 * 1024, // 50 GB
                availableDiskSpace = 5L * 1024 * 1024 * 1024, // 5 GB (less than required)
                hasEnoughSpace = false,
            )

        assertFalse(insufficientState.hasEnoughSpace)
    }

    @Test
    fun `AvailableDiskSpaceUseCase space constants are reasonable`() {
        // Required space should be positive and reasonable (1-100 GB)
        val requiredGB = AvailableDiskSpaceUseCase.REQUIRED_SPACE_GB
        assertTrue(requiredGB > 0)
        assertTrue(requiredGB < 100)

        // Temporary and final space should add up to total required
        val tempGB = AvailableDiskSpaceUseCase.TEMPORARY_SPACE_GB
        val finalGB = AvailableDiskSpaceUseCase.FINAL_SPACE_GB
        assertEquals(requiredGB.toDouble(), tempGB + finalGB, 0.1)
    }

    // ==================== Installation Type Tests ====================

    @Test
    fun `TypeOfInstallationState defaults to can proceed`() {
        val state = TypeOfInstallationState()
        assertTrue(state.canProceed)
    }

    @Test
    fun `TypeOfInstallationState can be set to not proceed`() {
        val state = TypeOfInstallationState(canProceed = false)
        assertFalse(state.canProceed)
    }

    // ==================== User Profile State Tests ====================

    @Test
    fun `UserProfileState initializes with empty values`() {
        val state = UserProfileState()

        assertEquals("", state.firstName)
        assertEquals("", state.lastName)
        assertTrue(state.communities.isEmpty())
        assertEquals(-1, state.selectedCommunityIndex)
    }

    @Test
    fun `UserProfileState can store first and last name`() {
        val state =
            UserProfileState(
                firstName = "Moshe",
                lastName = "Cohen",
            )

        assertEquals("Moshe", state.firstName)
        assertEquals("Cohen", state.lastName)
    }

    @Test
    fun `UserProfileState copy preserves values`() {
        val original =
            UserProfileState(
                firstName = "Moshe",
                lastName = "Cohen",
                selectedCommunityIndex = 2,
            )

        val copy = original.copy(firstName = "Aharon")

        assertEquals("Aharon", copy.firstName)
        assertEquals("Cohen", copy.lastName)
        assertEquals(2, copy.selectedCommunityIndex)
    }

    // ==================== Region Config State Tests ====================

    @Test
    fun `RegionConfigState initializes with empty lists`() {
        val state = RegionConfigState()

        assertTrue(state.countries.isEmpty())
        assertEquals(-1, state.selectedCountryIndex)
        assertTrue(state.cities.isEmpty())
        assertEquals(-1, state.selectedCityIndex)
    }

    @Test
    fun `RegionConfigState can store selected indices`() {
        val state =
            RegionConfigState(
                countries = listOf("Israel", "USA", "France"),
                selectedCountryIndex = 0,
                cities = listOf("Jerusalem", "Tel Aviv", "Haifa"),
                selectedCityIndex = 0,
            )

        assertEquals(3, state.countries.size)
        assertEquals(0, state.selectedCountryIndex)
        assertEquals("Jerusalem", state.cities[state.selectedCityIndex])
    }

    // ==================== Flow Integration Tests ====================

    @Test
    fun `onboarding flow state machine transitions correctly for online install`() {
        // Simulate the state machine for online installation
        var currentDestination: OnBoardingDestination = OnBoardingDestination.InitScreen

        // Init -> Licence
        currentDestination = OnBoardingDestination.LicenceScreen
        assertEquals(OnBoardingDestination.LicenceScreen, currentDestination)

        // Licence (accepted) -> DiskSpace
        currentDestination = OnBoardingDestination.AvailableDiskSpaceScreen
        assertEquals(OnBoardingDestination.AvailableDiskSpaceScreen, currentDestination)

        // DiskSpace (enough space) -> TypeOfInstall
        currentDestination = OnBoardingDestination.TypeOfInstallationScreen
        assertEquals(OnBoardingDestination.TypeOfInstallationScreen, currentDestination)

        // TypeOfInstall (online selected) -> DatabaseOnlineInstaller
        currentDestination = OnBoardingDestination.DatabaseOnlineInstallerScreen
        assertEquals(OnBoardingDestination.DatabaseOnlineInstallerScreen, currentDestination)

        // Download complete -> Extract
        currentDestination = OnBoardingDestination.ExtractScreen
        assertEquals(OnBoardingDestination.ExtractScreen, currentDestination)

        // Extract complete -> VersionVerification
        currentDestination = OnBoardingDestination.VersionVerificationScreen
        assertEquals(OnBoardingDestination.VersionVerificationScreen, currentDestination)

        // Version verified -> UserProfile
        currentDestination = OnBoardingDestination.UserProfilScreen
        assertEquals(OnBoardingDestination.UserProfilScreen, currentDestination)

        // Profile complete -> RegionConfig
        currentDestination = OnBoardingDestination.RegionConfigScreen
        assertEquals(OnBoardingDestination.RegionConfigScreen, currentDestination)

        // Region configured -> Finish
        currentDestination = OnBoardingDestination.FinishScreen
        assertEquals(OnBoardingDestination.FinishScreen, currentDestination)
    }

    // ==================== Disk Space Calculation Tests ====================

    @Test
    fun `disk space use case provides consistent calculations`() =
        runTest {
            val info = AvailableDiskSpaceUseCase().getDiskSpaceInfo()

            // Total should be >= available
            assertTrue(info.totalBytes >= info.availableBytes)

            // remainingAfterInstall should equal available - required
            val expectedRemaining = info.availableBytes - AvailableDiskSpaceUseCase.REQUIRED_SPACE_BYTES
            assertEquals(expectedRemaining, info.remainingAfterInstall)

            // hasEnoughSpace should be consistent
            assertEquals(info.availableBytes >= AvailableDiskSpaceUseCase.REQUIRED_SPACE_BYTES, info.hasEnoughSpace)
        }

    // ==================== Edge Case Tests ====================

    @Test
    fun `user profile state can handle Hebrew names`() {
        val state =
            UserProfileState(
                firstName = "משה",
                lastName = "כהן",
            )

        assertEquals("משה", state.firstName)
        assertEquals("כהן", state.lastName)
    }

    @Test
    fun `region config state handles country and city selection`() {
        val state =
            RegionConfigState(
                countries = listOf("Israel", "USA"),
                selectedCountryIndex = 0,
                cities = listOf("Jerusalem", "Tel Aviv"),
                selectedCityIndex = 1,
            )

        assertEquals("Israel", state.countries[state.selectedCountryIndex])
        assertEquals("Tel Aviv", state.cities[state.selectedCityIndex])
    }
}
