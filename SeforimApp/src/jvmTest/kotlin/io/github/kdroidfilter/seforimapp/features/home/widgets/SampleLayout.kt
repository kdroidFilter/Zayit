package io.github.kdroidfilter.seforimapp.features.home.widgets

import io.github.kdroidfilter.seforimapp.features.home.widgets.earth.EarthWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.sky.SkyWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.solarsystem.SolarSystemWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.temple.TempleCountdownWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.zmanim.ZmanimWidget

/** A full grid for the layout tests: the zmanim and the Earth, then the Temple, the solar system and the sky. */
internal val sampleLayout: List<WidgetPlacement> =
    listOf(
        WidgetPlacement(ZmanimWidget, CellRect(0, 0, 13, 4)),
        WidgetPlacement(EarthWidget, CellRect(13, 0, 7, 4)),
        WidgetPlacement(TempleCountdownWidget, CellRect(0, 4, 6, 3)),
        WidgetPlacement(SolarSystemWidget, CellRect(6, 4, 9, 3)),
        WidgetPlacement(SkyWidget, CellRect(15, 4, 5, 3)),
    )
