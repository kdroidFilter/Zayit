package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.kdroidfilter.seforimapp.features.home.widgets.earth.EarthWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.sky.SkyWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.solarsystem.SolarSystemWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.temple.TempleCountdownWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.zmanim.ZmanimWidget

/**
 * A Home widget, placed by [HomeWidgetsHost] on a grid of [HOME_GRID_COLUMNS] columns. Widgets never talk to each
 * other: they read and write the shared [HomeWidgetsState], so any of them can be moved or removed.
 */
interface HomeWidget {
    /** Stable key, for persisting the user's widget order. */
    val id: String

    /** The sizes it can be given, as on iOS: a few fixed ones rather than a free resize. */
    val sizes: Map<WidgetSize, GridSize>

    val defaultSize: WidgetSize

    /** Grows past its [GridSize.rows] to fit its content; the other widgets of its row then stretch to match. */
    val wrapContentHeight: Boolean get() = false

    val isSupported: Boolean get() = true

    /** Fills the size the host gives it through [modifier]. */
    @Composable
    fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    )

    /** Composed outside the scrolling Home list, for windows that must outlive the card scrolling away. */
    @Composable
    fun Detached(state: HomeWidgetsState) {}
}

enum class WidgetSize { SMALL, MEDIUM, LARGE }

/** [columns] out of [HOME_GRID_COLUMNS], [rows] in cells of [HOME_GRID_CELL_HEIGHT]. */
data class GridSize(
    val columns: Int,
    val rows: Float,
)

/** A widget on the Home grid, at one of its [HomeWidget.sizes]. */
data class WidgetPlacement(
    val widget: HomeWidget,
    val size: WidgetSize = widget.defaultSize,
) {
    init {
        require(size in widget.sizes) { "${widget.id} has no $size size" }
    }

    val grid: GridSize get() = widget.sizes.getValue(size)
}

/** The default Home layout. */
val homeWidgets: List<WidgetPlacement> =
    listOf(ZmanimWidget, EarthWidget, TempleCountdownWidget, SolarSystemWidget, SkyWidget).map(::WidgetPlacement)
