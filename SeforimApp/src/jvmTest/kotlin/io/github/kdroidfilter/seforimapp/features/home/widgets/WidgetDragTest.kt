package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.home.widgets.calendar.CalendarWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.temple.TempleCountdownWidget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WidgetDragTest {
    @Test
    fun `a gallery drag targets the widget under it, and holds over its own placeholder`() {
        val drag =
            WidgetDrag().apply {
                newWidget = WidgetPlacement(CalendarWidget)
                galleryBounds = Rect(0f, 500f, 1000f, 800f)
                bounds["zmanim"] = Rect(0f, 0f, 100f, 100f)
                bounds["earth"] = Rect(110f, 0f, 210f, 100f)
            }
        drag.moveTo(Offset(50f, 50f))
        assertEquals("zmanim", drag.targetId)
        assertTrue(drag.overGrid)

        // The placeholder took the zmanim's place and pushed them on, under the pointer now
        drag.placeholderBounds = Rect(0f, 0f, 100f, 100f)
        drag.bounds["zmanim"] = Rect(110f, 0f, 210f, 100f)
        drag.moveTo(Offset(60f, 50f))
        assertEquals("zmanim", drag.targetId)

        // Back on the gallery: nowhere
        drag.moveTo(Offset(500f, 600f))
        assertFalse(drag.overGrid)
        assertNull(drag.targetId)

        drag.end()
        assertNull(drag.newWidget)
    }

    @Test
    fun `a removed widget comes back where it was`() {
        val saved = AppSettings.homeWidgetsLayoutFlow.value
        try {
            AppSettings.setHomeWidgetsLayout(encodeLayout(homeWidgets))
            val removed = HomeWidgetsLayout.remove(TempleCountdownWidget)!!
            assertTrue(HomeWidgetsLayout.current().none { it.widget.id == "temple_countdown" })
            HomeWidgetsLayout.restore(removed)
            assertEquals(homeWidgets, HomeWidgetsLayout.current())
        } finally {
            AppSettings.setHomeWidgetsLayout(saved)
        }
    }
}
