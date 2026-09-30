package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import io.github.kdroidfilter.seforimapp.features.home.widgets.calendar.CalendarWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.earth.EarthWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.sky.SkyWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.solarsystem.SolarSystemWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.temple.TempleCountdownWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.zmanim.ZmanimWidget
import org.jetbrains.compose.resources.StringResource
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_widget_size_large
import seforimapp.seforimapp.generated.resources.home_widget_size_medium
import seforimapp.seforimapp.generated.resources.home_widget_size_small

/**
 * A Home widget, placed by [HomeWidgetsGrid] on a grid of [HOME_GRID_COLUMNS] columns. Widgets never talk to each
 * other: they read and write the shared [HomeWidgetsState], so any of them can be moved or removed.
 */
interface HomeWidget {
    /** Stable key, for persisting the user's widget order. */
    val id: String

    /** Its name in the widget gallery. */
    val title: StringResource

    /** The sizes it can be given, as on iOS: a few fixed ones rather than a free resize. */
    val sizes: Map<WidgetSize, GridSize>

    val defaultSize: WidgetSize

    /**
     * Its height at [width] when its content decides it (null: its [GridSize.rows]); a taller widget makes its whole
     * row taller, known before composing so every widget of the row can match it.
     */
    fun heightAt(width: Dp): Dp? = null

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

enum class WidgetSize(
    val label: StringResource,
) {
    SMALL(Res.string.home_widget_size_small),
    MEDIUM(Res.string.home_widget_size_medium),
    LARGE(Res.string.home_widget_size_large),
}

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

/** Every widget the user can place, shown by default or not. */
val availableHomeWidgets: List<HomeWidget> =
    listOf(ZmanimWidget, EarthWidget, TempleCountdownWidget, SolarSystemWidget, SkyWidget, CalendarWidget)

/** The default Home layout. */
val homeWidgets: List<WidgetPlacement> =
    listOf(ZmanimWidget, EarthWidget, TempleCountdownWidget, SolarSystemWidget, SkyWidget).map(::WidgetPlacement)
