package io.github.kdroidfilter.seforimapp.features.search.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class SearchKeyTest {
    @Test
    fun quotesNikudAndSpacesDoNotCount() {
        assertEquals("שוע", "שו\"ע".searchKey())
        assertEquals("חפץ חיים", "  חָפֵץ   חַיִּים ".searchKey())
    }

    @Test
    fun aMaqafIsASpace() {
        assertEquals("שולחן ערוך".searchKey(), "שולחן־ערוך".searchKey())
    }
}
