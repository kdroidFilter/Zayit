package io.github.kdroidfilter.seforimapp.features.home.widgets.measures

import io.github.kdroidfilter.seforimapp.features.home.widgets.CellPitch
import io.github.kdroidfilter.seforimapp.features.home.widgets.MAX_GRID_WIDTH
import io.github.kdroidfilter.seforimapp.features.home.widgets.minRows
import io.github.kdroidfilter.seforimapp.testAppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeasuresTest {
    @Test
    fun `an amma and a mil after each opinion`() {
        assertEquals(listOf("48 ס״מ", "54 ס״מ", "58 ס״מ"), AMMA_OPINIONS.map { length(it.cm) })
        val mil = LengthUnit.MIL.etzbaos / LengthUnit.AMMA.etzbaos
        assertEquals(listOf("960 מטר", "1.08 ק״מ", "1.16 ק״מ"), AMMA_OPINIONS.map { length(mil * it.cm) })
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
        val settings = testAppSettings()
        assertEquals(
            setOf("naeh", "chazon_ish", "igros_moshe", "mil_18", "mil_22_5", "mil_24"),
            shownOpinions(settings[MeasuresSettings.shown], settings[MeasuresSettings.offered]),
        )
    }

    @Test
    fun `the card is as tall as the opinions shown`() {
        val pitch = CellPitch(MAX_GRID_WIDTH)
        val settings = testAppSettings()
        try {
            MeasuresWidget.applySettings(settings)
            // Three amma opinions and three mil ones
            assertEquals(5, MeasuresWidget.minRows(7, pitch))
            // Picked since every opinion was offered: only the ticks count
            settings[MeasuresSettings.offered] = ALL_OPINION_IDS
            settings[MeasuresSettings.shown] = setOf("chazon_ish")
            MeasuresWidget.applySettings(settings)
            assertEquals(3, MeasuresWidget.minRows(7, pitch))
            // All unticked: none
            settings[MeasuresSettings.shown] = emptySet()
            MeasuresWidget.applySettings(settings)
            assertEquals(2, MeasuresWidget.minRows(7, pitch))
        } finally {
            MeasuresWidget.applySettings(testAppSettings())
        }
    }

    @Test
    fun `an opinion added after the user picked is shown until unticked`() {
        // Saved before גר״ח נאה: offered still its default, the opinions of then
        assertEquals(setOf("chazon_ish", "naeh"), shownOpinions(setOf("chazon_ish"), MeasuresSettings.offered.default))
        // Picked since: unticked stays unticked
        assertEquals(setOf("chazon_ish"), shownOpinions(setOf("chazon_ish"), ALL_OPINION_IDS))
    }

    @Test
    fun `the volumes convert into one another`() {
        assertEquals(30.0, convert(1.0, VolumeUnit.KOR, VolumeUnit.SEAH, null))
        assertEquals(2880.0, convert(1.0, VolumeUnit.KOR, VolumeUnit.REVIIS, null))
        assertEquals(10.0, convert(1.0, VolumeUnit.KOR, VolumeUnit.EIFAH, null))
        assertEquals("3 סאין", quantity(convert(1.0, VolumeUnit.EIFAH, VolumeUnit.SEAH, null)!!, VolumeUnit.SEAH))
        assertEquals("6 קבין", quantity(convert(1.0, VolumeUnit.SEAH, VolumeUnit.KAV, null)!!, VolumeUnit.KAV))
        assertEquals("4 רביעיות", quantity(convert(1.0, VolumeUnit.LOG, VolumeUnit.REVIIS, null)!!, VolumeUnit.REVIIS))
        assertEquals(listOf("רמב״ם עירובין א, יג", "רמב״ם ערכין ד, ד"), conversionSources(VolumeUnit.KOR, VolumeUnit.LOG).map { it.ref })
    }

    @Test
    fun `a revi'is is its opinion's etzba cubed`() {
        val naeh = AMMA_OPINIONS.single { it.id == "naeh" }
        val chazonIsh = AMMA_OPINIONS.single { it.id == "chazon_ish" }
        assertEquals(86.4, naeh.reviisMl, 1e-9)
        assertEquals(152.4, chazonIsh.reviisMl, 0.05)
        assertEquals("86.4 מ״ל", volume(naeh.reviisMl))
        assertEquals("152 מ״ל", volume(chazonIsh.reviisMl))
        // A kor after the Chazon Ish
        assertEquals("439 ליטר", opinionValue(1.0, VolumeUnit.KOR, VolumeUnit.REVIIS, chazonIsh))
    }

    @Test
    fun `the Igros Moshe is left out of volumes`() {
        assertEquals(listOf("naeh", "igros_moshe", "chazon_ish"), opinionsFor(Kind.LENGTH, ALL_OPINION_IDS).map { it.id })
        assertEquals(listOf("naeh", "chazon_ish"), opinionsFor(Kind.VOLUME, ALL_OPINION_IDS).map { it.id })
    }

    @Test
    fun `metric units convert after an opinion`() {
        val naeh = AMMA_OPINIONS.single { it.id == "naeh" }
        assertTrue(needsOpinion(MetricUnit.METER, LengthUnit.AMMA))
        assertFalse(needsOpinion(MetricUnit.METER, MetricUnit.CM))
        assertFalse(needsOpinion(LengthUnit.MIL, LengthUnit.AMMA))
        // Without an opinion there is no answer
        assertNull(convert(1.0, MetricUnit.METER, LengthUnit.AMMA, null))
        assertEquals(100.0 / 48, convert(1.0, MetricUnit.METER, LengthUnit.AMMA, naeh)!!, 1e-9)
        assertEquals("2.08 אמות", opinionValue(1.0, MetricUnit.METER, LengthUnit.AMMA, naeh))
        assertEquals("48 ס״מ", opinionValue(1.0, LengthUnit.AMMA, MetricUnit.CM, naeh))
        assertEquals(20.0, convert(1.0, LengthUnit.ETZBA, MetricUnit.MM, naeh)!!, 1e-9)
        assertEquals(10.0, convert(1.0, MetricUnit.CM, MetricUnit.MM, null)!!, 1e-9)
        assertEquals("1 ליטר", quantity(convert(1.0, MetricUnit.LITER, MetricUnit.ML, null)!! / 1000, MetricUnit.LITER))
        assertEquals("86.4 מ״ל", opinionValue(1.0, VolumeUnit.REVIIS, MetricUnit.ML, naeh))
        // Metric to metric: the Torah measure it makes
        assertEquals("2.5 טפחים", opinionValue(20.0, MetricUnit.CM, MetricUnit.METER, naeh))
        // The range over the opinions shown
        assertEquals("1.72–2.08 אמות", range(100.0 / 58, 100.0 / 48, LengthUnit.AMMA))
        // A metric unit cites the Torah units it goes through to the amma
        assertEquals(listOf("רמב״ם שבת יז, לו"), conversionSources(MetricUnit.METER, LengthUnit.ETZBA).map { it.ref })
        assertEquals(listOf("רמב״ם תפילה ד, ב"), conversionSources(MetricUnit.KM, LengthUnit.MIL).map { it.ref })
        assertEquals(emptyList(), conversionSources(MetricUnit.ML, MetricUnit.LITER))
    }

    @Test
    fun `each amount in the metric unit that suits it`() {
        assertEquals("5 מ״מ", length(0.5))
        assertEquals("48 ס״מ", length(48.0))
        assertEquals("2 מטר", length(200.0))
        assertEquals("1.5 ק״מ", length(150_000.0))
        assertEquals("86.4 מ״ל", volume(86.4))
        assertEquals("1 ליטר", volume(1000.0))
        assertEquals("438 ליטר", volume(438_000.0))
        assertEquals("1 כור", halachic(2880.0, Kind.VOLUME))
        assertEquals("0.5 רביעיות", halachic(0.5, Kind.VOLUME))
    }

    @Test
    fun `the card is as tall in either kind`() {
        val settings = testAppSettings()
        try {
            MeasuresWidget.applySettings(settings)
            // No walking time and no Igros Moshe: volume needs less room, length sets the card's height
            assertTrue(MeasuresWidget.heightOf(Kind.VOLUME) < MeasuresWidget.heightOf(Kind.LENGTH))
            assertEquals(MeasuresWidget.heightOf(Kind.LENGTH), MeasuresWidget.heightAt(MAX_GRID_WIDTH))
        } finally {
            MeasuresWidget.applySettings(testAppSettings())
        }
    }
}
