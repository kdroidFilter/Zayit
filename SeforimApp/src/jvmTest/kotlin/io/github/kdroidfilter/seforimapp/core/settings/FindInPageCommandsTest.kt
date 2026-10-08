package io.github.kdroidfilter.seforimapp.core.settings

import io.github.kdroidfilter.seforimapp.testAppSettings
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Find-in-page commands behaving as Chromium's find bar. */
class FindInPageCommandsTest {
    private val settings = testAppSettings()
    private val tab = "tab"

    @Test
    fun `Ctrl+F shows the bar and focuses it again instead of closing it`() {
        settings.showFindBar(tab)
        val firstFocus = settings.findFocusRequestFlow(tab).value
        settings.showFindBar(tab)

        assertTrue(settings.findBarOpenFlow(tab).value)
        assertEquals(firstFocus + 1, settings.findFocusRequestFlow(tab).value)
    }

    @Test
    fun `a new session starts from the selection, a running one keeps its query`() {
        settings.showFindBar(tab, selection = "  בראשית ברא  ")
        assertEquals("בראשית ברא", settings.findQueryFlow(tab).value)

        settings.showFindBar(tab, selection = "אלהים")
        assertEquals("בראשית ברא", settings.findQueryFlow(tab).value)
    }

    @Test
    fun `a selection too long to be a query isn't used`() {
        settings.setFindQuery(tab, "ברא")
        settings.showFindBar(tab, selection = "א".repeat(251))
        assertEquals("ברא", settings.findQueryFlow(tab).value)
    }

    @Test
    fun `Ctrl+G shows the bar and requests the next match`() =
        runTest {
            val request = async(start = CoroutineStart.UNDISPATCHED) { settings.findStepRequests(tab).first() }
            settings.findNext(tab, forward = false)

            assertFalse(request.await())
            assertTrue(settings.findBarOpenFlow(tab).value)
        }
}
