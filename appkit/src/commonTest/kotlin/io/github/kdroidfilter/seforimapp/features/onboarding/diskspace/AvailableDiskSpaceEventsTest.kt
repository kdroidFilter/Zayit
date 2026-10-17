package io.github.kdroidfilter.seforimapp.features.onboarding.diskspace

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame

class AvailableDiskSpaceEventsTest {
    @Test
    fun `Refresh event is singleton`() {
        val event1 = AvailableDiskSpaceEvents.Refresh
        val event2 = AvailableDiskSpaceEvents.Refresh
        assertSame(event1, event2)
    }

    @Test
    fun `Refresh implements AvailableDiskSpaceEvents`() {
        val event: AvailableDiskSpaceEvents = AvailableDiskSpaceEvents.Refresh
        assertIs<AvailableDiskSpaceEvents.Refresh>(event)
    }
}
