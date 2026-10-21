package io.github.kdroidfilter.seforimapp.features.home.widgets.solarsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.earthwidget.PLAY_DAYS_PER_SECOND
import io.github.kdroidfilter.seforimapp.earthwidget.PLAY_SPIN_SECONDS_PER_TURN
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.features.home.widgets.WidgetSettings
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_solar_options_advanced
import seforimapp.seforimapp.generated.resources.home_solar_options_proportional_spin
import seforimapp.seforimapp.generated.resources.home_solar_options_proportional_spin_hint
import seforimapp.seforimapp.generated.resources.home_solar_options_show_earth
import seforimapp.seforimapp.generated.resources.home_solar_options_show_fps
import seforimapp.seforimapp.generated.resources.home_solar_options_show_sky
import seforimapp.seforimapp.generated.resources.home_solar_options_speed
import seforimapp.seforimapp.generated.resources.home_solar_options_to_scale
import seforimapp.seforimapp.generated.resources.home_solar_options_to_scale_hint
import seforimapp.seforimapp.generated.resources.home_solar_options_window
import seforimapp.seforimapp.generated.resources.home_solar_speed_days
import seforimapp.seforimapp.generated.resources.home_solar_speed_fraction
import seforimapp.seforimapp.generated.resources.home_solar_speed_one_day

/** The play speeds offered, in simulated days a second. */
internal val PLAY_SPEEDS = listOf(0.25f, 0.5f, 1f, 2f, PLAY_DAYS_PER_SECOND, 15f, 30f)

/** A card floating over the full window, in dp from its top left corner (absolute: the same in RTL). */
@Serializable
internal data class CardFrame(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
)

/** The solar system widget's settings; a null frame is the card's default place. */
internal object SolarSystemSettings : WidgetSettings(SolarSystemWidget.id) {
    val showEarth by boolean(true)
    val showSky by boolean(true)
    val daysPerSecond by float(PLAY_DAYS_PER_SECOND)

    /** The solar system's Earth turns once per simulated day (true to life), else at a fixed watchable pace. */
    val proportionalSpin by boolean(true)

    /** The full window only: sizes and distances true to each other, with a deep zoom on the Earth. */
    val toScale by boolean(false)
    val earth by json<CardFrame?>(null)
    val sky by json<CardFrame?>(null)

    /** Advanced: a frame-rate readout in the full window. */
    val showFps by boolean(false)
}

/** The Earth's seconds a turn while playing; null: once per simulated day. */
@Composable
internal fun spinSecondsPerTurn(state: HomeWidgetsState): Float? =
    if (state[SolarSystemSettings.proportionalSpin]) null else PLAY_SPIN_SECONDS_PER_TURN

/** The cards shown in the full window, and the Earth's spin while playing; each change saved at once. */
@Composable
internal fun SolarSystemOptionsPage(
    state: HomeWidgetsState,
    modifier: Modifier = Modifier,
) {
    val settings = SolarSystemSettings
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(Res.string.home_solar_options_speed), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        val speedLabels = PLAY_SPEEDS.map { speedLabel(it) }
        ListComboBox(
            items = speedLabels,
            // A speed saved that's no longer offered shows the nearest
            selectedIndex = PLAY_SPEEDS.indices.minBy { kotlin.math.abs(PLAY_SPEEDS[it] - state[settings.daysPerSecond]) },
            onSelectedItemChange = { state[settings.daysPerSecond] = PLAY_SPEEDS[it] },
            modifier = Modifier.fillMaxWidth(),
        )
        CheckboxRow(
            text = stringResource(Res.string.home_solar_options_proportional_spin),
            checked = state[settings.proportionalSpin],
            onCheckedChange = { state[settings.proportionalSpin] = it },
        )
        Text(
            stringResource(Res.string.home_solar_options_proportional_spin_hint),
            fontSize = 12.sp,
            color = JewelTheme.globalColors.text.info,
        )
        Text(
            stringResource(Res.string.home_solar_options_window),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 10.dp),
        )
        CheckboxRow(
            text = stringResource(Res.string.home_solar_options_show_earth),
            checked = state[settings.showEarth],
            onCheckedChange = { state[settings.showEarth] = it },
        )
        CheckboxRow(
            text = stringResource(Res.string.home_solar_options_show_sky),
            checked = state[settings.showSky],
            onCheckedChange = { state[settings.showSky] = it },
        )
        CheckboxRow(
            text = stringResource(Res.string.home_solar_options_to_scale),
            checked = state[settings.toScale],
            onCheckedChange = { state[settings.toScale] = it },
        )
        Text(
            stringResource(Res.string.home_solar_options_to_scale_hint),
            fontSize = 12.sp,
            color = JewelTheme.globalColors.text.info,
        )
        // Folded until asked for: tuning, not everyday settings
        var advancedShown by remember { mutableStateOf(false) }
        Row(
            Modifier
                .padding(top = 10.dp)
                .clickable { advancedShown = !advancedShown }
                .pointerHoverIcon(PointerIcon.Hand),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            val folded = if (rtl) AllIconsKeys.General.ChevronLeft else AllIconsKeys.General.ChevronRight
            Icon(if (advancedShown) AllIconsKeys.General.ChevronDown else folded, contentDescription = null)
            Text(stringResource(Res.string.home_solar_options_advanced), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        if (advancedShown) {
            CheckboxRow(
                text = stringResource(Res.string.home_solar_options_show_fps),
                checked = state[settings.showFps],
                onCheckedChange = { state[settings.showFps] = it },
            )
        }
    }
}

/** "1 day a second", "¼ day a second", "6 days a second"… */
@Composable
private fun speedLabel(days: Float): String =
    when (days) {
        0.25f -> stringResource(Res.string.home_solar_speed_fraction, "¼")
        0.5f -> stringResource(Res.string.home_solar_speed_fraction, "½")
        1f -> stringResource(Res.string.home_solar_speed_one_day)
        else -> stringResource(Res.string.home_solar_speed_days, days.toInt())
    }
