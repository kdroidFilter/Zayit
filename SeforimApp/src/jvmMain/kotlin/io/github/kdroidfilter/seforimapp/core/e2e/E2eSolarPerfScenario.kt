package io.github.kdroidfilter.seforimapp.core.e2e

import io.github.kdroidfilter.seforim.tabs.TabsEvents
import io.github.kdroidfilter.seforimapp.features.home.widgets.CellRect
import io.github.kdroidfilter.seforimapp.features.home.widgets.WidgetPlacement
import io.github.kdroidfilter.seforimapp.features.home.widgets.decodeLayout
import io.github.kdroidfilter.seforimapp.features.home.widgets.encodeLayout
import io.github.kdroidfilter.seforimapp.features.home.widgets.solarsystem.SolarSystemOptions
import io.github.kdroidfilter.seforimapp.features.home.widgets.solarsystem.SolarSystemWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.solarsystem.setSolarSystemOptions
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import kotlinx.coroutines.delay
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/**
 * The solar system's full window under load (`ZAYIT_E2E_SCENARIO=solarperf`): with the Earth and sky cards, idle then
 * playing at 1 day a second, its frame times written to `perf.txt`; and a screenshot at a fixed instant
 * (`solar-fixed.png`), to compare the rendering before and after a change; with `ZAYIT_E2E_SAMPLE=idle|play`, native stacks
 * of that part (`sample.txt`). The user's layout and options are put back.
 */
object E2eSolarPerfScenario {
    private const val MEASURE_MS = 10_000L

    suspend fun run(sc: E2eScenario) {
        if (E2e.scenario != "solarperf") return
        val settings = sc.graph().appSettings
        val window =
            sc
                .graph()
                .desktopManager.windows.value
                .first()
        window.tabsViewModel.onEvent(TabsEvents.OnAdd)
        delay(6000)
        val savedLayout = settings.homeWidgetsLayoutFlow.value
        val savedOptions = settings.homeWidgetOptionsFlow.value[SolarSystemWidget.id]
        try {
            // The user's own Home, as it runs behind the full window; the solar system alone if it isn't on it
            if (decodeLayout(savedLayout).none { it.widget == SolarSystemWidget }) {
                settings.setHomeWidgetsLayout(encodeLayout(listOf(WidgetPlacement(SolarSystemWidget, CellRect(0, 0, 9, 3)))))
            }
            delay(3000)
            val state = E2e.homeWidgets ?: error("no Home widgets")
            state.setSolarSystemOptions(SolarSystemOptions(daysPerSecond = 1f))
            // A fixed instant: the same picture on every run
            state.selectDate(LocalDate.of(2026, 3, 20))
            state.targetTime =
                Date.from(
                    LocalDate
                        .of(2026, 3, 20)
                        .atTime(9, 0)
                        .atZone(ZoneId.of("Asia/Jerusalem"))
                        .toInstant(),
                )
            state.solarSystemFullscreen = true
            delay(6000)
            screenshot(sc, "solar-fixed")
            val report = StringBuilder()
            val sampled = System.getenv("ZAYIT_E2E_SAMPLE")
            report.appendLine(E2ePerf.measure("idle") { sampling(sampled == "idle") })
            state.togglePlay()
            report.appendLine(E2ePerf.measure("playing") { sampling(sampled == "play") })
            state.stopPlay()
            delay(1000)
            screenshot(sc, "solar-after-play")
            state.solarSystemFullscreen = false
            sc.note(report.toString().trim())
            E2e.outDir?.let { File(it, "perf.txt").writeText(report.toString()) }
        } finally {
            settings.setHomeWidgetOptions(SolarSystemWidget.id, savedOptions)
            settings.setHomeWidgetsLayout(savedLayout)
        }
    }

    /** Waits a measure, natively sampled from its 3rd second when [sample]: Skia, Filament, Metal, which JFR doesn't see. */
    private suspend fun sampling(sample: Boolean) {
        delay(2000)
        if (sample) {
            E2e.outDir?.let { dir ->
                runCatching {
                    ProcessBuilder("sample", ProcessHandle.current().pid().toString(), "6", "-file", File(dir, "sample.txt").path)
                        .redirectErrorStream(true)
                        .start()
                }
            }
        }
        delay(MEASURE_MS - 2000)
    }

    /**
     * The screen (or the full window alone, see below), through macOS' own `screencapture`; elsewhere the first
     * window's own export.
     */
    private suspend fun screenshot(
        sc: E2eScenario,
        name: String,
    ) {
        val dir = E2e.outDir ?: return
        if (!PlatformInfo.isMacOS) {
            val window =
                sc
                    .graph()
                    .desktopManager.windows.value
                    .first()
            if (!E2e.capture(window.id, name)) sc.note("$name: no capture")
            return
        }
        dir.mkdirs()
        // With ZAYIT_E2E_WINID (a tool printing a process' frontmost largest window id), that window alone, even under
        // others: the comparison then doesn't depend on what is on screen
        val windowId =
            System.getenv("ZAYIT_E2E_WINID")?.let { tool ->
                runCatching {
                    ProcessBuilder(tool, ProcessHandle.current().pid().toString())
                        .start()
                        .inputStream
                        .bufferedReader()
                        .readText()
                        .trim()
                }.getOrNull()
            }
        val args = listOf("screencapture", "-x", "-o") + (windowId?.takeIf { it.isNotEmpty() }?.let { listOf("-l", it) } ?: emptyList())
        val code =
            runCatching {
                ProcessBuilder(args + File(dir, "$name.png").path).start().waitFor()
            }.getOrDefault(-1)
        if (code != 0) sc.note("$name: screencapture failed ($code)")
    }
}
