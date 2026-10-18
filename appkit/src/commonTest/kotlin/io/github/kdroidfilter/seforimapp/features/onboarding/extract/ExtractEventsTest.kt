package io.github.kdroidfilter.seforimapp.features.onboarding.extract

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame

class ExtractEventsTest {
    @Test
    fun `StartIfPending is singleton`() {
        val event1 = ExtractEvents.StartIfPending
        val event2 = ExtractEvents.StartIfPending
        assertSame(event1, event2)
    }

    @Test
    fun `StartIfPending implements ExtractEvents`() {
        val event: ExtractEvents = ExtractEvents.StartIfPending
        assertIs<ExtractEvents.StartIfPending>(event)
    }
}
