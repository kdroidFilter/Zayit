package io.github.kdroidfilter.seforimapp.features.home.widgets.measures

import kotlin.test.Test
import kotlin.test.assertEquals

class MeasuresTest {
    @Test
    fun `an amma and a mil after each opinion`() {
        assertEquals(listOf("58 ס״מ", "54 ס״מ"), AMMA_OPINIONS.map { length(it.cm) })
        val mil = LengthUnit.MIL.etzbaos / LengthUnit.AMMA.etzbaos
        assertEquals(listOf("1.16 ק״מ", "1.08 ק״מ"), AMMA_OPINIONS.map { length(mil * it.cm) })
        assertEquals(listOf("18 דקות", "22.5 דקות", "24 דקות"), MIL_OPINIONS.map { duration(it.minutes * 60) })
        // A parsah is four mil: 72 minutes after the Shulchan Aruch
        assertEquals("1.2 שעות", duration(LengthUnit.PARSAH.etzbaos / LengthUnit.MIL.etzbaos * 18 * 60))
    }

    @Test
    fun `the measures convert into one another`() {
        assertEquals("6 טפחים", quantity(LengthUnit.AMMA.etzbaos / LengthUnit.TEFACH.etzbaos, LengthUnit.TEFACH))
        assertEquals("8,000 אמות", quantity(LengthUnit.PARSAH.etzbaos / LengthUnit.AMMA.etzbaos, LengthUnit.AMMA))
        assertEquals("1 מיל", quantity(LengthUnit.MIL.etzbaos / LengthUnit.MIL.etzbaos, LengthUnit.MIL))
        assertEquals("0.25 פרסאות", quantity(LengthUnit.MIL.etzbaos / LengthUnit.PARSAH.etzbaos, LengthUnit.PARSAH))
    }

    @Test
    fun `a conversion cites each unit it goes through`() {
        assertEquals(listOf("רמב״ם שבת יז, לו"), conversionSources(LengthUnit.AMMA, LengthUnit.TEFACH).map { it.ref })
        assertEquals(
            listOf("רמב״ם שבת יז, לו", "רמב״ם תפילה ד, ב", "משנה ברורה קי, לא"),
            conversionSources(LengthUnit.PARSAH, LengthUnit.ETZBA).map { it.ref },
        )
        assertEquals(emptyList(), conversionSources(LengthUnit.MIL, LengthUnit.MIL))
    }

    @Test
    fun `every opinion is shown until the user picks`() {
        assertEquals(setOf("chazon_ish", "igros_moshe", "mil_18", "mil_22_5", "mil_24"), shownOpinions(null))
        assertEquals(setOf("igros_moshe"), shownOpinions("igros_moshe"))
        // All unticked: none
        assertEquals(emptySet(), shownOpinions(""))
    }
}
