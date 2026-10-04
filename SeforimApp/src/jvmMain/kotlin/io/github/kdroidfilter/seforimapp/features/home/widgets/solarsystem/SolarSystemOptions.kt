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
import io.github.kdroidfilter.seforimapp.earthwidget.WidgetAntiAliasing
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_solar_aa_default
import seforimapp.seforimapp.generated.resources.home_solar_aa_off
import seforimapp.seforimapp.generated.resources.home_solar_options_advanced
import seforimapp.seforimapp.generated.resources.home_solar_options_antialiasing
import seforimapp.seforimapp.generated.resources.home_solar_options_proportional_spin
import seforimapp.seforimapp.generated.resources.home_solar_options_proportional_spin_hint
import seforimapp.seforimapp.generated.resources.home_solar_options_show_earth
import seforimapp.seforimapp.generated.resources.home_solar_options_show_fps
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
    /** Advanced: the 3D views' anti-aliasing, and a frame-rate readout in the full window. */
    val antiAliasing: WidgetAntiAliasing = WidgetAntiAliasing.OFF,
    val showFps: Boolean = false,
) {
    val spinSecondsPerTurn: Float? get() = if (proportionalSpin) null else PLAY_SPIN_SECONDS_PER_TURN

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        // ponytail: an unreadable value (older format, hand edit) falls back to the defaults
        fun decode(options: String?): SolarSystemOptions =
            (options?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() } ?: SolarSystemOptions())
                .let { if (it.antiAliasing in AvailableAntiAliasing) it else it.copy(antiAliasing = WidgetAntiAliasing.OFF) }
    }
}

/**
 * The anti-aliasing modes offered here. Not TAA on Linux: Filament's GL backend runs its mobile shaders there, and
 * NVIDIA's GLES compiler rejects TAA's (the engine then aborts).
 */
internal val AvailableAntiAliasing: List<WidgetAntiAliasing> =
    WidgetAntiAliasing.entries.filter { it != WidgetAntiAliasing.TAA || !PlatformInfo.isLinux }

/** Decoded once per change: read several times a frame while the solar system plays. */
@Composable
internal fun solarSystemOptions(state: HomeWidgetsState): SolarSystemOptions {
    val raw = state.optionsOf(SolarSystemWidget)
    return remember(raw) { SolarSystemOptions.decode(raw) }
}

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
            Text(stringResource(Res.string.home_solar_options_antialiasing), fontSize = 13.sp)
            ListComboBox(
                items = AvailableAntiAliasing.map { antiAliasingLabel(it) },
                selectedIndex = AvailableAntiAliasing.indexOf(options.antiAliasing),
                onSelectedItemChange = { save(options.copy(antiAliasing = AvailableAntiAliasing[it])) },
                modifier = Modifier.fillMaxWidth(),
            )
            CheckboxRow(
                text = stringResource(Res.string.home_solar_options_show_fps),
                checked = options.showFps,
                onCheckedChange = { save(options.copy(showFps = it)) },
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

@Composable
private fun antiAliasingLabel(mode: WidgetAntiAliasing): String =
    when (mode) {
        WidgetAntiAliasing.OFF -> "${stringResource(Res.string.home_solar_aa_off)} (${stringResource(Res.string.home_solar_aa_default)})"
        WidgetAntiAliasing.FXAA -> "FXAA"
        WidgetAntiAliasing.MSAA_2 -> "MSAA ×2"
        WidgetAntiAliasing.MSAA_4 -> "MSAA ×4"
        WidgetAntiAliasing.MSAA_8 -> "MSAA ×8"
        WidgetAntiAliasing.TAA -> "TAA"
    }
