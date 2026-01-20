package io.github.kdroidfilter.seforimapp.features.onboarding

import io.github.kdroidfilter.seforimapp.features.onboarding.diskspace.AvailableDiskSpaceEvents
import io.github.kdroidfilter.seforimapp.features.onboarding.download.DownloadEvents
import io.github.kdroidfilter.seforimapp.features.onboarding.extract.ExtractEvents
import io.github.kdroidfilter.seforimapp.features.onboarding.region.RegionConfigEvents
import io.github.kdroidfilter.seforimapp.features.onboarding.typeofinstall.TypeOfInstallationEvents
import io.github.kdroidfilter.seforimapp.features.onboarding.userprofile.UserProfileEvents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OnboardingEventsTest {
    // AvailableDiskSpaceEvents
    @Test
    fun `AvailableDiskSpaceEvents Refresh is singleton`() {
        val event1 = AvailableDiskSpaceEvents.Refresh
        val event2 = AvailableDiskSpaceEvents.Refresh
        assertEquals(event1, event2)
        assertIs<AvailableDiskSpaceEvents>(event1)
    }

    // DownloadEvents
    @Test
    fun `DownloadEvents Start is singleton`() {
        val event1 = DownloadEvents.Start
        val event2 = DownloadEvents.Start
        assertEquals(event1, event2)
        assertIs<DownloadEvents>(event1)
    }

    // ExtractEvents
    @Test
    fun `ExtractEvents StartIfPending is singleton`() {
        val event1 = ExtractEvents.StartIfPending
        val event2 = ExtractEvents.StartIfPending
        assertEquals(event1, event2)
        assertIs<ExtractEvents>(event1)
    }

    // RegionConfigEvents
    @Test
    fun `RegionConfigEvents SelectCountry stores index`() {
        val event = RegionConfigEvents.SelectCountry(index = 5)
        assertEquals(5, event.index)
        assertIs<RegionConfigEvents>(event)
    }

    @Test
    fun `RegionConfigEvents SelectCity stores index`() {
        val event = RegionConfigEvents.SelectCity(index = 10)
        assertEquals(10, event.index)
        assertIs<RegionConfigEvents>(event)
    }

    @Test
    fun `RegionConfigEvents SelectCountry equals works`() {
        val event1 = RegionConfigEvents.SelectCountry(3)
        val event2 = RegionConfigEvents.SelectCountry(3)
        val event3 = RegionConfigEvents.SelectCountry(4)
        assertEquals(event1, event2)
        assert(event1 != event3)
    }

    // TypeOfInstallationEvents
    @Test
    fun `TypeOfInstallationEvents OfflineFileChosen stores path`() {
        val event = TypeOfInstallationEvents.OfflineFileChosen(path = "/path/to/file.zst")
        assertEquals("/path/to/file.zst", event.path)
        assertIs<TypeOfInstallationEvents>(event)
    }

    @Test
    fun `TypeOfInstallationEvents OfflineFileChosen equals works`() {
        val event1 = TypeOfInstallationEvents.OfflineFileChosen("/a/b")
        val event2 = TypeOfInstallationEvents.OfflineFileChosen("/a/b")
        assertEquals(event1, event2)
    }

    // UserProfileEvents
    @Test
    fun `UserProfileEvents FirstNameChanged stores value`() {
        val event = UserProfileEvents.FirstNameChanged(value = "John")
        assertEquals("John", event.value)
        assertIs<UserProfileEvents>(event)
    }

    @Test
    fun `UserProfileEvents LastNameChanged stores value`() {
        val event = UserProfileEvents.LastNameChanged(value = "Doe")
        assertEquals("Doe", event.value)
        assertIs<UserProfileEvents>(event)
    }

    @Test
    fun `UserProfileEvents SelectCommunity stores index`() {
        val event = UserProfileEvents.SelectCommunity(index = 2)
        assertEquals(2, event.index)
        assertIs<UserProfileEvents>(event)
    }

    @Test
    fun `UserProfileEvents FirstNameChanged equals works`() {
        val event1 = UserProfileEvents.FirstNameChanged("Test")
        val event2 = UserProfileEvents.FirstNameChanged("Test")
        assertEquals(event1, event2)
    }
}
