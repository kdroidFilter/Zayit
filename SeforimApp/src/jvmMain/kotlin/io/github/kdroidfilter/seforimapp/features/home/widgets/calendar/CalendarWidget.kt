package io.github.kdroidfilter.seforimapp.features.home.widgets.calendar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.features.home.widgets.CellSpan
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.features.home.widgets.PanelCard
import io.github.kdroidfilter.seforimapp.features.home.widgets.rememberAccentColor
import io.github.kdroidfilter.seforimapp.features.home.widgets.shownDateHere
import io.github.kdroidfilter.seforimapp.luach.LuachMonth
import kotlinx.datetime.plus
import org.jetbrains.jewel.foundation.theme.JewelTheme
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_widget_name_calendar
import java.time.LocalDate

/**
 * The Hebrew month, as in the KosherKotlin demo's luach, or the civil one, swapped by the arrow of its header; clicking
 * a day moves every widget to it.
 */
internal object CalendarWidget : HomeWidget {
    override val id = "calendar"
    override val title = Res.string.home_widget_name_calendar
    override val defaultSpan = CellSpan(7, 5)
    override val toolWindowSize = DpSize(420.dp, 460.dp)
    override val toolWindowMinSize = DpSize(360.dp, 400.dp)
    override val toolSymbol = "calendar"
    override val minSpan = CellSpan(7, 5)

    // Six week rows hold their two lines of text; taller would only part them
    override val maxSpan = CellSpan(14, 6)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        val selected = state.shownDateHere()
        val today = remember(selected, state.location) { LocalDate.now(state.location.timeZone.toZoneId()) }
        val civil = state.optionsOf(this) == CIVIL_OPTION
        PanelCard(modifier) {
            LuachMonth(
                selected = selected,
                today = today,
                inIsrael = state.inIsrael,
                civil = civil,
                onSwap = { state.setOptions(this@CalendarWidget, if (civil) null else CIVIL_OPTION) },
                onSelect = state::selectDate,
                accent = rememberAccentColor(JewelTheme.isDark),
                modifier = Modifier.fillMaxSize().padding(12.dp),
            )
        }
    }
}

private const val CIVIL_OPTION = "civil"
