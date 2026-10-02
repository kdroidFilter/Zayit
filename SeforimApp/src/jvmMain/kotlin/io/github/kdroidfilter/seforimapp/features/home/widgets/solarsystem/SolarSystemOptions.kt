package io.github.kdroidfilter.seforimapp.features.home.widgets.solarsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.earthwidget.PLAY_DAYS_PER_SECOND
import io.github.kdroidfilter.seforimapp.earthwidget.PLAY_SPIN_SECONDS_PER_TURN
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_solar_options_proportional_spin
import seforimapp.seforimapp.generated.resources.home_solar_options_proportional_spin_hint
import seforimapp.seforimapp.generated.resources.home_solar_options_show_earth
import seforimapp.seforimapp.generated.resources.home_solar_options_show_sky
import seforimapp.seforimapp.generated.resources.home_solar_options_speed
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

/** The solar system widget's options, saved as JSON in its widget options; a null frame is the default place. */
@Serializable
internal data class SolarSystemOptions(
    val showEarth: Boolean = true,
    val showSky: Boolean = true,
    val daysPerSecond: Float = PLAY_DAYS_PER_SECOND,
    /** The solar system's Earth turns once per simulated day (true to life), else at a fixed watchable pace. */
    val proportionalSpin: Boolean = true,
    val earth: CardFrame? = null,
    val sky: CardFrame? = null,
) {
    val spinSecondsPerTurn: Float? get() = if (proportionalSpin) null else PLAY_SPIN_SECONDS_PER_TURN

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        // ponytail: an unreadable value (older format, hand edit) falls back to the defaults
        fun decode(options: String?): SolarSystemOptions =
            options?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() } ?: SolarSystemOptions()
    }
}

@Composable
internal fun solarSystemOptions(state: HomeWidgetsState) = SolarSystemOptions.decode(state.optionsOf(SolarSystemWidget))

internal fun HomeWidgetsState.setSolarSystemOptions(options: SolarSystemOptions) = setOptions(SolarSystemWidget, options.encode())

/** The cards shown in the full window, and the Earth's spin while playing; each change saved at once. */
@Composable
internal fun SolarSystemOptionsPage(
    state: HomeWidgetsState,
    modifier: Modifier = Modifier,
) {
    val options = solarSystemOptions(state)
    val save = { new: SolarSystemOptions -> state.setSolarSystemOptions(new) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(Res.string.home_solar_options_speed), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        val speedLabels = PLAY_SPEEDS.map { speedLabel(it) }
        ListComboBox(
            items = speedLabels,
            // A speed saved that's no longer offered shows the nearest
            selectedIndex = PLAY_SPEEDS.indices.minBy { kotlin.math.abs(PLAY_SPEEDS[it] - options.daysPerSecond) },
            onSelectedItemChange = { save(options.copy(daysPerSecond = PLAY_SPEEDS[it])) },
            modifier = Modifier.fillMaxWidth(),
        )
        CheckboxRow(
            text = stringResource(Res.string.home_solar_options_proportional_spin),
            checked = options.proportionalSpin,
            onCheckedChange = { save(options.copy(proportionalSpin = it)) },
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
            checked = options.showEarth,
            onCheckedChange = { save(options.copy(showEarth = it)) },
        )
        CheckboxRow(
            text = stringResource(Res.string.home_solar_options_show_sky),
            checked = options.showSky,
            onCheckedChange = { save(options.copy(showSky = it)) },
        )
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
