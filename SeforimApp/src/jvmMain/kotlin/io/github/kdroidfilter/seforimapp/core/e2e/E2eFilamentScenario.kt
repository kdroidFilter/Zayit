package io.github.kdroidfilter.seforimapp.core.e2e

import io.github.kdroidfilter.seforim.tabs.TabsEvents
import io.github.kdroidfilter.seforimapp.features.home.widgets.CellRect
import io.github.kdroidfilter.seforimapp.features.home.widgets.CellSpan
import io.github.kdroidfilter.seforimapp.features.home.widgets.HOME_GRID_COLUMNS
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.WidgetPlacement
import io.github.kdroidfilter.seforimapp.features.home.widgets.earth.EarthWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.encodeLayout
import io.github.kdroidfilter.seforimapp.features.home.widgets.sky.SkyWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.solarsystem.SolarSystemWidget

/**
 * The Filament widgets (the Earth, the sky, the solar system), which only render in a real window: each alone on the
 * Home at its sizes, one export per size. Their smallest, default and largest sizes, or every size the grid allows
 * with `ZAYIT_E2E_ALL_SIZES`, or the ones `ZAYIT_E2E_SPANS` lists (`solar_system:12x5,earth:7x4`); each settles
 * `ZAYIT_E2E_SETTLE_MS` before its export. The user's widget layout is put back afterwards.
 */
object E2eFilamentScenario {
    suspend fun run(sc: E2eScenario) {
        if (E2e.scenario != "filament") return
        val settings = sc.graph().appSettings
        val window =
            sc
                .graph()
                .desktopManager.windows.value
                .first()
        window.tabsViewModel.onEvent(TabsEvents.OnAdd)
        sc.step("f0-home", 6000)
        val saved = settings.homeWidgetsLayoutFlow.value
        val all = System.getenv("ZAYIT_E2E_ALL_SIZES") != null
        val settle = System.getenv("ZAYIT_E2E_SETTLE_MS")?.toLongOrNull() ?: 2500L
        val widgets = listOf(EarthWidget, SkyWidget, SolarSystemWidget)
        val shots =
            System.getenv("ZAYIT_E2E_SPANS")?.split(",")?.map { entry ->
                val (id, size) = entry.trim().split(":")
                val (w, h) = size.split("x").map(String::toInt)
                widgets.first { it.id == id } to CellSpan(w, h)
            } ?: widgets.flatMap { widget -> widget.spans(all).map { widget to it } }
        try {
            shots.forEachIndexed { i, (widget, span) ->
                settings.setHomeWidgetsLayout(encodeLayout(listOf(WidgetPlacement(widget, CellRect(0, 0, span.w, span.h)))))
                // Numbered: a size listed twice (to watch it settle) keeps every export
                sc.step("f${i.toString().padStart(2, '0')}-${widget.id}-${span.w}x${span.h}", settle)
            }
        } finally {
            settings.setHomeWidgetsLayout(saved)
        }
    }

    private fun HomeWidget.spans(all: Boolean): List<CellSpan> {
        val maxW = minOf(maxSpan.w, HOME_GRID_COLUMNS)
        if (!all) return listOf(minSpan, defaultSpan, CellSpan(maxW, maxSpan.h)).distinct()
        return (minSpan.w..maxW).flatMap { w -> (minSpan.h..maxSpan.h).map { h -> CellSpan(w, h) } }
    }
}
