package io.github.kdroidfilter.seforimapp.features.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class ConditionsTextTest {
    private val markdown =
        """
        |intro
        |
        |<!-- if:self-update -->
        |updates
        |
        |<!-- endif -->
        |<!-- if:update-helper -->
        |polkit
        |
        |<!-- endif -->
        |outro
        |
        """.trimMargin()

    @Test
    fun `keeps the blocks that apply, without their markers`() {
        assertEquals("intro\n\nupdates\n\npolkit\n\noutro\n", ConditionsText.filter(markdown) { true })
    }

    @Test
    fun `drops the blocks that do not apply`() {
        assertEquals("intro\n\nupdates\n\noutro\n", ConditionsText.filter(markdown) { it == "self-update" })
        assertEquals("intro\n\noutro\n", ConditionsText.filter(markdown) { false })
    }
}
