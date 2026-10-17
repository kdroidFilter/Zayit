package io.github.kdroidfilter.seforimapp.features.onboarding.typeofinstall

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TypeOfInstallationEventsTest {
    @Test
    fun `OfflineFileChosen stores path`() {
        val event = TypeOfInstallationEvents.OfflineFileChosen("/path/to/file.zip")
        assertEquals("/path/to/file.zip", event.path)
    }

    @Test
    fun `OfflineFileChosen implements TypeOfInstallationEvents`() {
        val event: TypeOfInstallationEvents = TypeOfInstallationEvents.OfflineFileChosen("/path")
        assertIs<TypeOfInstallationEvents.OfflineFileChosen>(event)
    }

    @Test
    fun `OfflineFileChosen equality works`() {
        val event1 = TypeOfInstallationEvents.OfflineFileChosen("/path")
        val event2 = TypeOfInstallationEvents.OfflineFileChosen("/path")
        assertEquals(event1, event2)
    }

    @Test
    fun `OfflineFileChosen copy works`() {
        val original = TypeOfInstallationEvents.OfflineFileChosen("/original")
        val copied = original.copy(path = "/modified")
        assertEquals("/modified", copied.path)
    }
}
