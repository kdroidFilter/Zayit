package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import kotlin.test.Test
import kotlin.test.assertEquals

class PinnedTabLabelTest {
    // Terms as the acronym table holds them, search variants included.
    @Test
    fun `the single-word acronym wins over search variants`() {
        assertEquals("משנ\"ב", pinnedTabLabel("משנה ברורה", listOf("משנ\"ב", "משנ\"ב.", "משנב", "משנב.")))
        assertEquals("בר\"ר", pinnedTabLabel("בראשית רבה", listOf("בר\"ר", "בראשית רבא", "ברר")))
        assertEquals("סה\"ח", pinnedTabLabel("ספר החינוך", listOf("חינוך", "ס החינוך", "סה\"ח", "סהח")))
    }

    @Test
    fun `without a single-word acronym the shortest marked one is taken`() {
        assertEquals("משנ\"ת שבת", pinnedTabLabel("משנה תורה, הלכות שבת", listOf("משנ\"ת הל' שבת", "משנ\"ת שבת", "משנה תורה", "רמבם שבת")))
    }

    @Test
    fun `a book without an acronym shows its title`() {
        assertEquals("בראשית", pinnedTabLabel("בראשית", emptyList()))
        assertEquals("ספר הישר", pinnedTabLabel("ספר הישר", listOf("ספר-הישר")))
    }
}
