package io.github.kdroidfilter.seforimapp.core.e2e

import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.HebrewMonth
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.siddur.*
import kotlinx.datetime.toJavaLocalDate
import java.time.LocalDate
import java.util.UUID

/** The smart siddur on a few days that change it: a Monday, Rosh Chodesh with Hallel, a fast's Mincha, the Omer. */
object E2eSiddurScenario {
    suspend fun run(sc: E2eScenario) {
        if (E2e.scenario != "siddur") return
        val tabs =
            sc
                .graph()
                .desktopManager.windows.value
                .first()
                .tabsViewModel

        suspend fun open(
            name: String,
            part: String,
            date: LocalDate,
            heading: String? = null,
        ) {
            tabs.openTab(
                TabsDestination.Siddur(
                    tabId = UUID.randomUUID().toString(),
                    part = part,
                    epochDay = date.toEpochDay(),
                    heading = heading,
                ),
            )
            sc.step(name, 3000)
        }

        fun hebrew(
            month: HebrewMonth,
            day: Int,
        ): LocalDate = JewishCalendar(5787, month, day).gregorianLocalDate.toJavaLocalDate()
        // 24 Tishrei 5787 is a Monday
        open("s1-monday-shacharit", "SHACHARIT", hebrew(HebrewMonth.TISHREI, 24))
        open("s2-monday-tachanun", "SHACHARIT", hebrew(HebrewMonth.TISHREI, 24), heading = "תחנון")
        open("s3-rosh-chodesh-yaale", "SHACHARIT", hebrew(HebrewMonth.CHESHVAN, 1), heading = "עבודה")
        open("s4-rosh-chodesh-hallel", "SHACHARIT", hebrew(HebrewMonth.CHESHVAN, 1), heading = "הלל")
        open("s5-fast-mincha", "MINCHA", hebrew(HebrewMonth.TEVES, 10), heading = "שומע תפילה")
        open("s6-omer", "SFIRAT_HAOMER", hebrew(HebrewMonth.NISSAN, 17))
        open("s7-meein-shalosh", "MEEIN_SHALOSH", hebrew(HebrewMonth.TISHREI, 24))
        open("s8-chanuka-candles", "NEROT_CHANUKA", hebrew(HebrewMonth.KISLEV, 27))
        open("s9-shabbat", "SHACHARIT_SHABBAT", hebrew(HebrewMonth.CHESHVAN, 6))
        open("s10-shabbat-reading", "SHACHARIT_SHABBAT", hebrew(HebrewMonth.CHESHVAN, 6), heading = "קריאת התורה")
        open("s11-rosh-hashana", "SHACHARIT", JewishCalendar(5788, HebrewMonth.TISHREI, 1).gregorianLocalDate.toJavaLocalDate())
        open("s12-kol-nidre", "MAARIV", JewishCalendar(5788, HebrewMonth.TISHREI, 10).gregorianLocalDate.toJavaLocalDate())
        open(
            "s13-sukkot-hallel",
            "SHACHARIT",
            JewishCalendar(5788, HebrewMonth.TISHREI, 15).gregorianLocalDate.toJavaLocalDate(),
            heading = "הלל",
        )
    }
}
