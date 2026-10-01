package io.github.kdroidfilter.seforimapp.features.home.widgets.luach

import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.HebrewMonth
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishDate
import kotlinx.datetime.toJavaLocalDate
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// The readings hebcal-learning's own tests expect, in the card's Hebrew
class LimudCyclesTest {
    private fun hebrew(
        year: Long,
        month: HebrewMonth,
        day: Int,
    ): LocalDate = JewishDate(year, month, day).gregorianLocalDate.toJavaLocalDate()

    @Test
    fun `hebrew numerals are spelled as the library's references`() {
        assertEquals("טו", hebrewNumeral(15))
        assertEquals("טז", hebrewNumeral(16))
        assertEquals("קיט", hebrewNumeral(119))
        assertEquals("רע", hebrewNumeral(270))
        assertEquals("שמד", hebrewNumeral(344))
        assertEquals("תכז", hebrewNumeral(427))
    }

    @Test
    fun `mishnah yomis reads two mishnayos from Berachos on 20 May 1947`() {
        val first = mishnahYomis(LocalDate.of(1947, 5, 20))
        assertEquals("ברכות א, א-ב", first.value)
        assertEquals(LibraryPlace("משנה ברכות", listOf("פרק א"), "משנה ברכות א, א", endRefs = listOf("משנה ברכות א, ב")), first.place)
        assertEquals("ברכות ב, ח – ג, א", mishnahYomis(LocalDate.of(1947, 5, 26)).value)
    }

    @Test
    fun `perek mishnah`() {
        assertEquals("ברכות א", perekMishnah(LocalDate.of(2025, 2, 8)).value)
        assertEquals("פאה ג", perekMishnah(LocalDate.of(2025, 2, 19)).value)
        assertEquals("נדה א", perekMishnah(LocalDate.of(2025, 1, 7)).value)
    }

    @Test
    fun `rambam, one perek and three`() {
        assertEquals("מלכים ומלחמות יב", rambam1(LocalDate.of(2020, 7, 9)).value)
        assertEquals("פרה אדומה ח", rambam1(LocalDate.of(1986, 1, 1)).value)
        assertEquals("מלכים ומלחמות י-יב", rambam3(LocalDate.of(2020, 7, 9)).value)
        val split = rambam3(LocalDate.of(1984, 5, 6))
        assertEquals("יסודי התורה י · דעות א-ב", split.value)
        // Marked to its first book's last perek of the day
        assertEquals(LibraryPlace("משנה תורה, הלכות יסודי התורה", listOf("פרק י"), endTocs = listOf("פרק י")), split.place)
        assertEquals(listOf("פרק יב"), rambam3(LocalDate.of(2020, 7, 9)).place?.endTocs)
        // Its first day, the paragraphs of מסירת תורה שבעל פה
        assertEquals("מסירת תורה שבעל פה א-מה", rambam3(LocalDate.of(1984, 4, 29)).value)
    }

    @Test
    fun `sefer hamitzvos`() {
        val day = seferHamitzvos(hebrew(5783, HebrewMonth.AV, 3))
        assertEquals("ל״ת שמח, ל״ת שמט, ל״ת שנ, ל״ת שנא", day.value)
        assertEquals(
            LibraryPlace(
                "ספר המצוות",
                listOf("מצוות לא תעשה"),
                "ספר המצוות, מצוות לא תעשה, שמח",
                endRefs = listOf("ספר המצוות, מצוות לא תעשה, שנא"),
            ),
            day.place,
        )
        // Its mitzvos far apart: nothing to mark
        assertEquals(emptyList(), seferHamitzvos(LocalDate.of(2025, 7, 18)).place?.endRefs)
        assertEquals("ל״ת קמט, עשה קלב", seferHamitzvos(LocalDate.of(2025, 7, 18)).value)
    }

    @Test
    fun `tehillim by the month and the week`() {
        assertEquals(listOf("פרק ט"), tehillimOfMonth(hebrew(5786, HebrewMonth.TISHREI, 1)).place?.endTocs)
        assertEquals("א-ט", tehillimOfMonth(hebrew(5786, HebrewMonth.TISHREI, 1)).value)
        val ps119 = tehillimOfMonth(hebrew(5786, HebrewMonth.TISHREI, 26))
        assertEquals("קיט, צז-קעו", ps119.value)
        assertEquals("תהילים קיט, צז", ps119.place?.ref)
        // Cheshvan 5786 has 29 days
        assertEquals("קמ-קנ", tehillimOfMonth(hebrew(5786, HebrewMonth.CHESHVAN, 29)).value)
        assertEquals("קכ-קנ", tehillimOfWeek(LocalDate.of(2026, 10, 3)).value)
    }

    @Test
    fun `nach yomi`() {
        assertEquals("שופטים יד", nachYomi(LocalDate.of(2022, 2, 26)).value)
        assertEquals("תהילים מ", nachYomi(LocalDate.of(2023, 3, 15)).value)
        assertEquals("דברי הימים ב לו", nachYomi(LocalDate.of(2024, 1, 31)).value)
    }

    @Test
    fun `pirkei avos on the summer's shabbosos`() {
        assertEquals(listOf(1), pirkeiAvos(hebrew(5784, HebrewMonth.NISSAN, 26), inIsrael = true))
        assertEquals(listOf(1), pirkeiAvos(hebrew(5784, HebrewMonth.SIVAN, 9), inIsrael = true))
        assertEquals(listOf(3), pirkeiAvos(hebrew(5784, HebrewMonth.AV, 6), inIsrael = true))
        assertEquals(listOf(3, 4), pirkeiAvos(hebrew(5784, HebrewMonth.ELUL, 18), inIsrael = true))
        assertEquals(listOf(5, 6), pirkeiAvos(hebrew(5784, HebrewMonth.ELUL, 25), inIsrael = true))
        assertNull(pirkeiAvos(hebrew(5785, HebrewMonth.CHESHVAN, 2), inIsrael = true))
    }

    @Test
    fun `kitzur shulchan aruch`() {
        assertEquals("קכב, ז-יא", kitzur(hebrew(5783, HebrewMonth.AV, 3))?.value)
        // Adar II follows the simanim of the year's start
        assertEquals("טו, א-ו", kitzur(hebrew(5787, HebrewMonth.ADAR_II, 5))?.value)
        assertNull(kitzur(hebrew(5787, HebrewMonth.ADAR, 30)))
        // Fixed: hebcal's "133:27-4:1"
        assertEquals("קלג, כז – קלד, א", kitzur(hebrew(5786, HebrewMonth.TISHREI, 3))?.value)
    }

    @Test
    fun `aruch hashulchan`() {
        val first = aruchHashulchan(LocalDate.of(2020, 5, 29))
        assertEquals("או״ח א, א-ח", first.value)
        assertEquals(
            LibraryPlace(
                "ערוך השולחן",
                listOf("אורח חיים", "סימן א"),
                "ערוך השולחן, אורח חיים, א, א",
                endRefs = listOf("ערוך השולחן, אורח חיים, א, ח"),
            ),
            first.place,
        )
        assertEquals("או״ח ב, ט – ג, ה", aruchHashulchan(LocalDate.of(2020, 6, 3)).value)
        assertEquals("חו״מ תכז, י-יא", aruchHashulchan(LocalDate.of(2025, 2, 10)).value)
    }

    @Test
    fun `chofetz chaim`() {
        assertEquals("לאוין יד-טו", chofetzChaim(LocalDate.of(2023, 10, 1))?.value)
        assertEquals("ארורין", chofetzChaim(LocalDate.of(2023, 10, 10))?.value)
        val lashonHara = chofetzChaim(LocalDate.of(2023, 10, 11))
        assertEquals("לשון הרע כלל א, א-ב", lashonHara?.value)
        assertEquals("חפץ חיים, חלק ראשון: הלכות איסורי לשון הרע, כלל א, א", lashonHara?.place?.ref)
        assertEquals("לשון הרע כלל ב, יא", chofetzChaim(LocalDate.of(2023, 10, 20))?.value)
        // Its last paragraph, then the ones before: the library may hold two in one line
        assertEquals(
            listOf("$PSICHAH, לאוין, טו", "$PSICHAH, לאוין, יד"),
            chofetzChaim(LocalDate.of(2023, 10, 1))?.place?.endRefs,
        )
    }

    @Test
    fun `shmiras halashon`() {
        val partTwo = shmirasHalashon(hebrew(5783, HebrewMonth.AV, 3))
        assertEquals("ח״ב טז, יד-טו", partTwo?.value)
        assertEquals("שמירת הלשון, חלק שני, טז, יד", partTwo?.place?.ref)
        assertEquals(listOf("שמירת הלשון, חלק שני, טז, טו"), partTwo?.place?.endRefs)
        assertEquals("חתימת הספר ז, י", shmirasHalashon(LocalDate.of(2023, 5, 20))?.value)
        assertEquals("ח״ב א, א-ב", shmirasHalashon(LocalDate.of(2023, 5, 23))?.value)
    }

    @Test
    fun `the card shows the limudim picked, in the menu's order`() {
        val date = LocalDate.of(2026, 10, 1)
        assertEquals(Limud.defaults.size, limudOfDay(date, inIsrael = true).size)
        val picked = limudOfDay(date, inIsrael = true, shown = setOf(Limud.SHMIRAS_HALASHON, Limud.TEHILLIM))
        assertEquals(listOf("תהילים", "שמה״ל"), picked.map { it.kicker })
        assertEquals(setOf(Limud.KITZUR, Limud.BAVLI), Limud.decode(Limud.encode(setOf(Limud.KITZUR, Limud.BAVLI))))
        assertEquals(emptySet(), Limud.decode(""))
    }

    private companion object {
        const val PSICHAH = "חפץ חיים, פתיחה להלכות לשון הרע ורכילות"
    }
}
