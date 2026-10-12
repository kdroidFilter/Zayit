package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.home.widgets.temple.TempleCountdownWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.zmanim.ZmanimWidget
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.jewel.ui.icon.IconKey

/**
 * A Home widget, placed by [HomeWidgetsGrid] on an area of a grid of [HOME_GRID_COLUMNS] columns, as on Android's home
 * screen. Widgets never talk to each other: they read and write the shared [HomeWidgetsState], so any of them can be
 * moved, resized or removed.
 */
interface HomeWidget {
    /** Stable key, for persisting the user's widget order. */
    val id: String

    /** Its name in the widget gallery. */
    val title: StringResource

    /** Its size when added, in cells. */
    val defaultSpan: CellSpan

    /** How small and how large the user can resize it, in cells (Android's minResizeWidth, maxResizeWidth…). */
    val minSpan: CellSpan get() = defaultSpan
    val maxSpan: CellSpan get() = CellSpan(HOME_GRID_COLUMNS, defaultSpan.h * 2)

    /** The height its content needs at [width], when it decides it: it can't be made shorter. */
    fun heightAt(width: Dp): Dp? = null

    /** Its own settings, read and written through [HomeWidgetsState]; reset together from its options dialog. */
    val settings: WidgetSettings? get() = null

    /**
     * Its settings as saved, given at the start and on each change, for a widget whose size follows them (its [heightAt]):
     * the grid reads that size where no composition is (laying out, arranging).
     */
    fun applySettings(appSettings: AppSettings) {}

    val isSupported: Boolean get() = true

    /**
     * Its window's size when it's also a tool, opened alone from the app's Tools menu (see [availableTools]); null for a
     * widget only.
     */
    val toolWindowSize: DpSize? get() = null

    /** How small the user can make its tool window; its [toolWindowSize] by default, so its content is never cut. */
    val toolWindowMinSize: DpSize? get() = toolWindowSize

    /** Its icon in the macOS Tools menu: an SF Symbol's name. */
    val toolSymbol: String? get() = null

    /** Fills the size the host gives it through [modifier]. */
    @Composable
    fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    )

    /** Its picture in the gallery; the live content by default, a drawing for the ones a thumbnail can't run. */
    @Composable
    fun Preview(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) = Content(state, modifier)

    /**
     * Its own items atop its right-click menu: actions, check marks, submenus; none by default. Read in composition, so
     * they follow its settings (see [HomeWidgetsState.get]).
     */
    @Composable
    fun menuItems(state: HomeWidgetsState): List<WidgetMenuItem> = emptyList()

    /** Its settings page, for an item of its menu to open in a dialog (see [HomeWidgetsState.optionsOpen]). */
    @Composable
    fun Options(state: HomeWidgetsState) {}

    /** Its window as a tool, opened from the app's Tools menu: its [Content] alone in a window of [toolWindowSize]. */
    @Composable
    fun ToolWindow(
        state: HomeWidgetsState,
        onClose: () -> Unit,
    ) = DefaultToolWindow(this, state, onClose)

    /** Composed outside the scrolling Home list, for windows that must outlive the card scrolling away. */
    @Composable
    fun Detached(state: HomeWidgetsState) {}
}

/**
 * An item of a widget's own menu, with [icon]: [checked] shows a check mark instead, null a plain action; with
 * [children], it opens them as a submenu.
 */
@Immutable
data class WidgetMenuItem(
    val label: String,
    val icon: IconKey? = null,
    val checked: Boolean? = null,
    val children: List<WidgetMenuItem> = emptyList(),
    val onClick: () -> Unit = {},
)

/** A widget on the Home grid, on [cell]. */
@Immutable
data class WidgetPlacement(
    val widget: HomeWidget,
    val cell: CellRect,
)

/**
 * The default Home layout: the zmanim and the Temple, both shown everywhere. The heavy 3D widgets (the Earth, the solar
 * system, the sky) are left to the user to add from the gallery, where the machine can run them.
 */
val homeWidgets: List<WidgetPlacement> =
    listOf(
        WidgetPlacement(ZmanimWidget, CellRect(0, 0, 12, 4)),
        WidgetPlacement(TempleCountdownWidget, CellRect(12, 0, 7, 4)),
    )
