package io.github.kdroidfilter.seforimapp.features.onboarding.download

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame

class DownloadEventsTest {
    @Test
    fun `Start is singleton`() {
        val event1 = DownloadEvents.Start
        val event2 = DownloadEvents.Start
        assertSame(event1, event2)
    }

    @Test
    fun `Start implements DownloadEvents`() {
        val event: DownloadEvents = DownloadEvents.Start
        assertIs<DownloadEvents.Start>(event)
    }
}
