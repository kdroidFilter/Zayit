package io.github.kdroidfilter.seforimapp.features.home.widgets.temple

import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.HebrewMonth
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishDate
import androidx.compose.ui.text.LinkAnnotation
import io.github.kdroidfilter.seforimapp.features.home.widgets.measures.Source
import kotlin.test.Test
import kotlin.test.assertEquals

class TempleCountdownTest {
    private fun years(
        year: Int,
        month: HebrewMonth,
        day: Int,
        count: TempleCount,
    ) = templeCountdownAt(JewishDate(year, month, day), count).years

    @Test
    fun rashiCountsTheYearOfTheDestruction() {
        assertEquals(1958, years(5786, HebrewMonth.AV, 10, TempleCount.RASHI))
        assertEquals(1958, years(5787, HebrewMonth.CHESHVAN, 9, TempleCount.RASHI))
        assertEquals(1959, years(5787, HebrewMonth.AV, 10, TempleCount.RASHI))
    }

    @Test
    fun rambamCountsFromTheYearAfter() {
        assertEquals(1957, years(5786, HebrewMonth.AV, 10, TempleCount.RAMBAM))
        assertEquals(1957, years(5787, HebrewMonth.CHESHVAN, 9, TempleCount.RAMBAM))
    }

    @Test
    fun sourceLinksShowTheirTextAndOpenTheirLine() {
        var opened: Source? = null
        val text = withSourceLinks("רש״י ([ע״ז ט:](רש\"י על עבודה זרה#333)) כתב", open = { opened = it })
        assertEquals("רש״י (ע״ז ט:) כתב", text.text)
        val link = text.getLinkAnnotations(0, text.length).single().item as LinkAnnotation.Clickable
        link.linkInteractionListener!!.onClick(link)
        assertEquals(Source("ע״ז ט:", "רש\"י על עבודה זרה", 333), opened)
    }
}
