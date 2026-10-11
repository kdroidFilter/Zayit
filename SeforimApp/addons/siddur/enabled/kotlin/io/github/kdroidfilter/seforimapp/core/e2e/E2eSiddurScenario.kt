package io.github.kdroidfilter.seforimapp.core.e2e

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.HebrewMonth
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.siddur.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.toJavaLocalDate
import java.io.File
import java.lang.management.ManagementFactory
import java.time.LocalDate
import java.util.UUID

/** The smart siddur on a few days that change it: a Monday, Rosh Chodesh with Hallel, a fast's Mincha, the Omer. */
object E2eSiddurScenario {
    suspend fun run(sc: E2eScenario) {
        if (E2e.scenario == "siddurperf") return E2eSiddurPerf.run(sc)
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

/**
 * The smart siddur's scroll and memory (`ZAYIT_E2E_SCENARIO=siddurperf`): resident and heap memory on the Home, with
 * the siddur open, after scrolling it down and up; the scroll's frame times; captures of the top and the end. Linux.
 */
object E2eSiddurPerf {
    @Volatile
    var list: LazyListState? = null

    /** The first line marked as the day's addition, for a capture of its mark. */
    @Volatile
    var firstSpecial: Int = -1

    private const val STEP_PX = 300f
    private const val FRAMES = 900
    private const val FRAME_MS = 16L
    private const val SLOW_FRAME_MS = 20.0

    suspend fun run(sc: E2eScenario) {
        val report = StringBuilder()
        report.appendLine("home: ${memory()}")
        val tabs =
            sc
                .graph()
                .desktopManager.windows.value
                .first()
                .tabsViewModel
        // A weekday's Shacharit (24 Tishrei 5787, a Monday), then a Shabbat's: the longest books
        for ((name, part, date) in listOf(
            Triple("weekday", "SHACHARIT", JewishCalendar(5787, HebrewMonth.TISHREI, 24)),
            Triple("shabbat", "SHACHARIT_SHABBAT", JewishCalendar(5787, HebrewMonth.CHESHVAN, 6)),
        )) {
            list = null
            val t0 = System.nanoTime()
            tabs.openTab(
                TabsDestination.Siddur(
                    tabId = UUID.randomUUID().toString(),
                    part = part,
                    epochDay = date.gregorianLocalDate.toJavaLocalDate().toEpochDay(),
                    heading = null,
                ),
            )
            withTimeoutOrNull(20_000) { while ((list?.layoutInfo?.totalItemsCount ?: 0) == 0) delay(5) }
            val openMs = (System.nanoTime() - t0) / 1_000_000
            delay(3000)
            val state = list ?: error("no siddur list")
            report.appendLine("$name open=${openMs}ms items=${state.layoutInfo.totalItemsCount}: ${memory()}")
            sc.step("sp-$name-top", 300)
            if (firstSpecial >= 0) {
                state.scrollToItem((firstSpecial - 3).coerceAtLeast(0))
                sc.step("sp-$name-special", 500)
                state.scrollToItem(0)
                delay(500)
            }
            for ((round, dir) in listOf(1f, -1f, 1f, -1f, 1f, -1f).withIndex()) {
                val gaps = ArrayList<Double>()
                var last = System.nanoTime()
                val perf =
                    E2ePerf.measure(if (dir > 0) "$name down" else "$name up") {
                        for (f in 0 until FRAMES) {
                            state.scrollBy(dir * STEP_PX)
                            delay(FRAME_MS)
                            val now = System.nanoTime()
                            gaps += (now - last) / 1e6
                            last = now
                            if (if (dir > 0) !state.canScrollForward else !state.canScrollBackward) break
                        }
                    }
                val slow = gaps.count { it > SLOW_FRAME_MS }
                report.appendLine("$perf steps=${gaps.size} slowSteps(>${SLOW_FRAME_MS}ms)=$slow")
                if (round == 0) sc.step("sp-$name-end", 300)
                if (dir < 0) report.appendLine("$name after round trip ${round / 2}: ${memory()}")
            }
        }
        sc.note("SIDDURPERF\n$report")
        E2e.outDir?.let { File(it, "siddurperf.txt").writeText(report.toString()) }
    }

    /** Resident memory (anonymous part) from /proc, and the Java heap in use after a GC, in MB. */
    private suspend fun memory(): String =
        withContext(Dispatchers.IO) {
            repeat(3) {
                System.gc()
                Thread.sleep(200)
            }
            val status =
                File("/proc/self/status").readLines().associate { line ->
                    line.substringBefore(':') to
                        line.substringAfter(':').trim()
                }
            val heap = ManagementFactory.getMemoryMXBean().heapMemoryUsage
            // Memory freed but kept by malloc shows up as the gap a trim gives back
            val trimmed =
                runCatching {
                    ManagementFactory.getPlatformMBeanServer().invoke(
                        javax.management.ObjectName("com.sun.management:type=DiagnosticCommand"),
                        "systemTrimNativeHeap",
                        arrayOf<Any?>(null),
                        arrayOf("[Ljava.lang.String;"),
                    )
                    File("/proc/self/status")
                        .readLines()
                        .first { it.startsWith("RssAnon") }
                        .substringAfter(':')
                        .trim()
                }.getOrElse { "trim failed: $it" }
            "rss=${status["VmRSS"]} anon=${status["RssAnon"]} anonAfterTrim=$trimmed heapUsed=${heap.used / MB}MB heapCommitted=${heap.committed / MB}MB"
        }

    private const val MB = 1024 * 1024
}
