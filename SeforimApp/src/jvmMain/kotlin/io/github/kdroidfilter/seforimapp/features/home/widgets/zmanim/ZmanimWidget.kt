package io.github.kdroidfilter.seforimapp.features.home.widgets.zmanim

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.HebrewDateFormatter
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import io.github.kdroidfilter.seforimapp.earthwidget.EarthWidgetLocation
import io.github.kdroidfilter.seforimapp.earthwidget.ZmanimOpinion
import io.github.kdroidfilter.seforimapp.earthwidget.computeZmanimTimes
import io.github.kdroidfilter.seforimapp.earthwidget.ohrHaChaimSunset
import io.github.kdroidfilter.seforimapp.earthwidget.toDate
import io.github.kdroidfilter.seforimapp.earthwidget.zmanimCalendar
import io.github.kdroidfilter.seforimapp.features.home.widgets.CellSpan
import io.github.kdroidfilter.seforimapp.features.home.widgets.HOME_GRID_COLUMNS
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.features.home.widgets.WidgetCardLoading
import io.github.kdroidfilter.seforimapp.features.home.widgets.rememberAccentColor
import io.github.kdroidfilter.seforimapp.features.home.widgets.rememberOffMain
import io.github.kdroidfilter.seforimapp.features.home.widgets.shownDateHere
import io.github.kdroidfilter.seforimapp.features.zmanim.data.ITIM_LABINA_ABROAD_CANDLES
import io.github.kdroidfilter.seforimapp.features.zmanim.data.ITIM_LABINA_ISRAEL_CANDLES
import io.github.kdroidfilter.seforimapp.features.zmanim.data.itimLabinaCandleLighting
import io.github.kdroidfilter.seforimapp.features.zmanim.data.worldPlaces
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_widget_card_first_light_abbrev
import seforimapp.seforimapp.generated.resources.home_widget_card_first_light_title
import seforimapp.seforimapp.generated.resources.home_widget_card_sunrise_abbrev
import seforimapp.seforimapp.generated.resources.home_widget_card_sunrise_title
import seforimapp.seforimapp.generated.resources.home_widget_card_sunset_abbrev
import seforimapp.seforimapp.generated.resources.home_widget_card_sunset_title
import seforimapp.seforimapp.generated.resources.home_widget_chatzot_day_label
import seforimapp.seforimapp.generated.resources.home_widget_chatzot_night_label
import seforimapp.seforimapp.generated.resources.home_widget_chatzot_title
import seforimapp.seforimapp.generated.resources.home_widget_mincha_gedola_label
import seforimapp.seforimapp.generated.resources.home_widget_mincha_ketana_label
import seforimapp.seforimapp.generated.resources.home_widget_mincha_plag_label
import seforimapp.seforimapp.generated.resources.home_widget_mincha_title
import seforimapp.seforimapp.generated.resources.home_widget_name_zmanim
import seforimapp.seforimapp.generated.resources.home_widget_shabbat_entry_label
import seforimapp.seforimapp.generated.resources.home_widget_shabbat_exit_label
import seforimapp.seforimapp.generated.resources.home_widget_shema_gra_label
import seforimapp.seforimapp.generated.resources.home_widget_shema_mga_label
import seforimapp.seforimapp.generated.resources.home_widget_shema_title
import seforimapp.seforimapp.generated.resources.home_widget_shema_title_abbrev
import seforimapp.seforimapp.generated.resources.home_widget_tefila_title
import seforimapp.seforimapp.generated.resources.home_widget_tefila_title_abbrev
import seforimapp.seforimapp.generated.resources.home_widget_tzais_geonim_label
import seforimapp.seforimapp.generated.resources.home_widget_tzais_geonim_label_abbrev
import seforimapp.seforimapp.generated.resources.home_widget_tzais_rabbeinu_tam_label
import seforimapp.seforimapp.generated.resources.home_widget_visible_stars_title
import seforimapp.seforimapp.generated.resources.home_widget_visible_stars_title_abbrev
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Date
import kotlin.math.abs
import kotlin.time.Duration.Companion.minutes

private const val ZMANIM_LAYOUT_SCALE = 1.5f
internal val ZMANIM_CARD_HEIGHT = 90.dp * ZMANIM_LAYOUT_SCALE
private val ZMANIM_VERTICAL_SPACING = 12.dp * ZMANIM_LAYOUT_SCALE
internal val ZMANIM_HORIZONTAL_SPACING = 12.dp
private val MIN_ZMANIM_CARD_WIDTH = 99.dp
private val MIN_TITLE_WIDTH_WITH_DOT = 72.dp
private val MIN_WIDTH_FOR_EXTRA_CARDS = 443.dp

@Immutable
private data class DayMomentCardData(
    val title: StringResource,
    val titleAbbrev: StringResource? = null,
    val time: String,
    val timeValue: Date?,
    val accentStart: Color,
    val accentEnd: Color,
)

@Immutable
private data class ShabbatTimes(
    val parashaName: String,
    val entryTime: Date?,
    val exitTime: Date?,
)

@Immutable
private sealed class ZmanimGridItem {
    @Immutable
    data class Moment(
        val data: DayMomentCardData,
        val onClick: (() -> Unit)?,
    ) : ZmanimGridItem()

    @Immutable
    data class Shema(
        val title: StringResource,
        val titleAbbrev: StringResource? = null,
        val graLabel: StringResource,
        val graTime: String,
        val graTimeValue: Date?,
        val mgaLabel: StringResource,
        val mgaTime: String,
        val mgaTimeValue: Date?,
        val onGraClick: (() -> Unit)?,
        val onMgaClick: (() -> Unit)?,
    ) : ZmanimGridItem()

    @Immutable
    data class Tefila(
        val title: StringResource,
        val titleAbbrev: StringResource? = null,
        val graLabel: StringResource,
        val graTime: String,
        val graTimeValue: Date?,
        val mgaLabel: StringResource,
        val mgaTime: String,
        val mgaTimeValue: Date?,
        val onGraClick: (() -> Unit)?,
        val onMgaClick: (() -> Unit)?,
    ) : ZmanimGridItem()

    @Immutable
    data class VisibleStars(
        val title: StringResource,
        val titleAbbrev: StringResource? = null,
        val geonimLabel: StringResource,
        val geonimLabelAbbrev: StringResource? = null,
        val geonimTime: String,
        val geonimTimeValue: Date?,
        val rabbeinuTamLabel: StringResource,
        val rabbeinuTamLabelAbbrev: StringResource? = null,
        val rabbeinuTamTime: String,
        val rabbeinuTamTimeValue: Date?,
        val onGeonimClick: (() -> Unit)?,
        val onRabbeinuTamClick: (() -> Unit)?,
    ) : ZmanimGridItem()

    @Immutable
    data class Mincha(
        val gedolaTime: String,
        val gedolaTimeValue: Date?,
        val ketanaTime: String,
        val ketanaTimeValue: Date?,
        val plagTime: String,
        val plagTimeValue: Date?,
        val onGedolaClick: (() -> Unit)?,
        val onKetanaClick: (() -> Unit)?,
        val onPlagClick: (() -> Unit)?,
    ) : ZmanimGridItem()

    @Immutable
    data class Shabbat(
        val title: String,
        val entryLabel: StringResource,
        val entryTime: String,
        val entryTimeValue: Date?,
        val exitLabel: StringResource,
        val exitTime: String,
        val exitTimeValue: Date?,
        val onEntryClick: (() -> Unit)?,
        val onExitClick: (() -> Unit)?,
    ) : ZmanimGridItem()
}

/** The zmanim cards of the selected day; clicking one points the Earth, the sky and the solar system at that zman. */
internal object ZmanimWidget : HomeWidget {
    override val id = "zmanim"
    override val title = Res.string.home_widget_name_zmanim
    override val defaultSpan = CellSpan(13, 4)
    override val minSpan = CellSpan(8, 4)
    override val toolWindowSize = DpSize(700.dp, 360.dp)
    override val toolSymbol = "clock"

    // Its cards are a fixed height: the grid gives it the rows they need at each width (minRows), never more
    override val maxSpan = CellSpan(HOME_GRID_COLUMNS, 4)

    override fun heightAt(width: Dp): Dp = zmanimGridHeight(width)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        val location = state.location
        val timeZone = location.timeZone
        val selectedDate = state.shownDateHere()
        val opinion = state.zmanimOpinion
        val inIsrael = state.inIsrael
        val cityLabel = state.cityLabel
        val zmanimTimes =
            rememberOffMain(selectedDate, location, opinion, inIsrael) {
                computeZmanimTimes(selectedDate, location, opinion, inIsrael)
            }
        val shabbatTimes =
            rememberOffMain(selectedDate, location, opinion, cityLabel, inIsrael) {
                computeShabbatTimes(selectedDate, location, opinion, cityLabel, inIsrael)
            }
        if (zmanimTimes == null || shabbatTimes == null) return WidgetCardLoading(modifier)
        val timeFormatter =
            remember(timeZone) {
                SimpleDateFormat("HH:mm").apply { this.timeZone = timeZone }
            }

        fun formatTime(date: Date?): String = date?.let { "⁦${timeFormatter.format(it)}⁩" } ?: ""

        val onZmanimClick: (Date?) -> Unit = { date ->
            date?.let { state.targetTime = Date(it.time) }
        }

        val momentCards =
            listOf(
                DayMomentCardData(
                    title = Res.string.home_widget_card_first_light_title,
                    titleAbbrev = Res.string.home_widget_card_first_light_abbrev,
                    time = formatTime(zmanimTimes.alosHashachar),
                    timeValue = zmanimTimes.alosHashachar,
                    accentStart = Color(0xFF8AB4F8),
                    accentEnd = Color(0xFFC3DAFE),
                ),
                DayMomentCardData(
                    title = Res.string.home_widget_card_sunrise_title,
                    titleAbbrev = Res.string.home_widget_card_sunrise_abbrev,
                    time = formatTime(zmanimTimes.sunrise),
                    timeValue = zmanimTimes.sunrise,
                    accentStart = Color(0xFFFFCA7A),
                    accentEnd = Color(0xFFFFE0A3),
                ),
                DayMomentCardData(
                    title = Res.string.home_widget_card_sunset_title,
                    titleAbbrev = Res.string.home_widget_card_sunset_abbrev,
                    time = formatTime(zmanimTimes.sunset),
                    timeValue = zmanimTimes.sunset,
                    accentStart = Color(0xFF9CB9FF),
                    accentEnd = Color(0xFFB6D4FF),
                ),
            )

        BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
            val horizontalSpacing = ZMANIM_HORIZONTAL_SPACING
            val verticalSpacing = ZMANIM_VERTICAL_SPACING
            val showExtraCards = maxWidth >= MIN_WIDTH_FOR_EXTRA_CARDS
            val zmanimItems =
                buildList {
                    momentCards.forEachIndexed { index, card ->
                        val onClick =
                            if (card.timeValue != null) {
                                { onZmanimClick(card.timeValue) }
                            } else {
                                null
                            }
                        add(ZmanimGridItem.Moment(card, onClick))
                        if (index == 1) {
                            val graTimeValue = zmanimTimes.sofZmanShmaGra
                            val mgaTimeValue = zmanimTimes.sofZmanShmaMga
                            add(
                                ZmanimGridItem.Shema(
                                    title = Res.string.home_widget_shema_title,
                                    titleAbbrev = Res.string.home_widget_shema_title_abbrev,
                                    graLabel = Res.string.home_widget_shema_gra_label,
                                    graTime = formatTime(graTimeValue),
                                    graTimeValue = graTimeValue,
                                    mgaLabel = Res.string.home_widget_shema_mga_label,
                                    mgaTime = formatTime(mgaTimeValue),
                                    mgaTimeValue = mgaTimeValue,
                                    onGraClick = graTimeValue?.let { { onZmanimClick(it) } },
                                    onMgaClick = mgaTimeValue?.let { { onZmanimClick(it) } },
                                ),
                            )
                            val tefilaGraTime = zmanimTimes.sofZmanTfilaGra
                            val tefilaMgaTime = zmanimTimes.sofZmanTfilaMga
                            add(
                                ZmanimGridItem.Tefila(
                                    title = Res.string.home_widget_tefila_title,
                                    titleAbbrev = Res.string.home_widget_tefila_title_abbrev,
                                    graLabel = Res.string.home_widget_shema_gra_label,
                                    graTime = formatTime(tefilaGraTime),
                                    graTimeValue = tefilaGraTime,
                                    mgaLabel = Res.string.home_widget_shema_mga_label,
                                    mgaTime = formatTime(tefilaMgaTime),
                                    mgaTimeValue = tefilaMgaTime,
                                    onGraClick = tefilaGraTime?.let { { onZmanimClick(it) } },
                                    onMgaClick = tefilaMgaTime?.let { { onZmanimClick(it) } },
                                ),
                            )
                            val chatzosHayom = zmanimTimes.chatzosHayom
                            val chatzosLayla = zmanimTimes.chatzosLayla
                            // ponytail: reuses the Tefila dual card, day in the start (mga) slot, night in the end (gra) slot
                            add(
                                ZmanimGridItem.Tefila(
                                    title = Res.string.home_widget_chatzot_title,
                                    mgaLabel = Res.string.home_widget_chatzot_day_label,
                                    mgaTime = formatTime(chatzosHayom),
                                    mgaTimeValue = chatzosHayom,
                                    graLabel = Res.string.home_widget_chatzot_night_label,
                                    graTime = formatTime(chatzosLayla),
                                    graTimeValue = chatzosLayla,
                                    onMgaClick = chatzosHayom?.let { { onZmanimClick(it) } },
                                    onGraClick = chatzosLayla?.let { { onZmanimClick(it) } },
                                ),
                            )
                            if (showExtraCards) {
                                val gedola = zmanimTimes.minchaGedola
                                val ketana = zmanimTimes.minchaKetana
                                val plag = zmanimTimes.plagHamincha
                                add(
                                    ZmanimGridItem.Mincha(
                                        gedolaTime = formatTime(gedola),
                                        gedolaTimeValue = gedola,
                                        ketanaTime = formatTime(ketana),
                                        ketanaTimeValue = ketana,
                                        plagTime = formatTime(plag),
                                        plagTimeValue = plag,
                                        onGedolaClick = gedola?.let { { onZmanimClick(it) } },
                                        onKetanaClick = ketana?.let { { onZmanimClick(it) } },
                                        onPlagClick = plag?.let { { onZmanimClick(it) } },
                                    ),
                                )
                            }
                        }
                    }
                    val tzaisGeonim = zmanimTimes.tzais
                    val tzaisRabbeinuTam = zmanimTimes.tzaisRabbeinuTam
                    add(
                        ZmanimGridItem.VisibleStars(
                            title = Res.string.home_widget_visible_stars_title,
                            titleAbbrev = Res.string.home_widget_visible_stars_title_abbrev,
                            geonimLabel = Res.string.home_widget_tzais_geonim_label,
                            geonimLabelAbbrev = Res.string.home_widget_tzais_geonim_label_abbrev,
                            geonimTime = formatTime(tzaisGeonim),
                            geonimTimeValue = tzaisGeonim,
                            rabbeinuTamLabel = Res.string.home_widget_tzais_rabbeinu_tam_label,
                            rabbeinuTamTime = formatTime(tzaisRabbeinuTam),
                            rabbeinuTamTimeValue = tzaisRabbeinuTam,
                            onGeonimClick = tzaisGeonim?.let { { onZmanimClick(it) } },
                            onRabbeinuTamClick = tzaisRabbeinuTam?.let { { onZmanimClick(it) } },
                        ),
                    )
                    val shabbatEntryTime = shabbatTimes.entryTime
                    val shabbatExitTime = shabbatTimes.exitTime
                    add(
                        ZmanimGridItem.Shabbat(
                            title = shabbatTimes.parashaName,
                            entryLabel = Res.string.home_widget_shabbat_entry_label,
                            entryTime = formatTime(shabbatEntryTime),
                            entryTimeValue = shabbatEntryTime,
                            exitLabel = Res.string.home_widget_shabbat_exit_label,
                            exitTime = formatTime(shabbatExitTime),
                            exitTimeValue = shabbatExitTime,
                            onEntryClick = shabbatEntryTime?.let { { onZmanimClick(it) } },
                            onExitClick = shabbatExitTime?.let { { onZmanimClick(it) } },
                        ),
                    )
                }.toImmutableList()
            val columns = zmanimColumns(maxWidth, zmanimItems.size)
            val gridHeight = zmanimGridHeight(maxWidth)

            ZmanimCardsGrid(
                items = zmanimItems,
                columns = columns,
                horizontalSpacing = horizontalSpacing,
                verticalSpacing = verticalSpacing,
                selectedTimeMillis = state.targetTime?.time,
                compactMode = !showExtraCards,
                modifier = Modifier.fillMaxWidth().height(gridHeight),
            )
        }
    }
}

private val ZmanimGridItem.span: Float
    get() =
        when (this) {
            is ZmanimGridItem.Mincha -> 1.5f
            is ZmanimGridItem.Moment -> 0.5f
            else -> 1f
        }

/**
 * Row width in slots: the first row takes items until it reaches [columns], so it is always full,
 * and every row gets that width (5.5 when the Mincha card overflows 5 columns). Leftover space ends the last row.
 */
private fun <T> List<T>.rowCapacity(
    columns: Int,
    span: (T) -> Float,
): Float {
    var filled = 0f
    for (item in this) {
        filled += span(item)
        if (filled >= columns) return filled
    }
    return columns.toFloat()
}

/** Greedily fills rows of [capacity] slots, each item taking its [span]. */
private fun <T> List<T>.toZmanimRows(
    capacity: Float,
    span: (T) -> Float,
): List<List<T>> =
    fold(mutableListOf<MutableList<T>>()) { rows, item ->
        val last = rows.lastOrNull()
        if (last != null && last.sumOf { span(it).toDouble() } + span(item) <= capacity) {
            last += item
        } else {
            rows += mutableListOf(item)
        }
        rows
    }

/** [rowCount] rows of about the same total span, in order. */
internal fun <T> List<T>.balancedRows(
    rowCount: Int,
    span: (T) -> Float,
): List<List<T>> {
    val target = sumOf { span(it).toDouble() } / rowCount
    val rows = mutableListOf(mutableListOf<T>())
    var filled = 0.0
    for (item in this) {
        // A quarter slot of slack keeps a row from closing just short of its share
        if (filled >= target - 0.25 && rows.size < rowCount) {
            rows += mutableListOf<T>()
            filled = 0.0
        }
        rows.last() += item
        filled += span(item)
    }
    return rows
}

/** Five columns while a card keeps [MIN_ZMANIM_CARD_WIDTH], else four. */
private fun zmanimColumns(
    width: Dp,
    itemCount: Int,
): Int {
    val baseColumns = 5.coerceAtMost(itemCount).coerceAtLeast(1)
    if (baseColumns != 5) return baseColumns
    val cardWidth = (width - ZMANIM_HORIZONTAL_SPACING * (baseColumns - 1)) / baseColumns
    return if (cardWidth < MIN_ZMANIM_CARD_WIDTH) 4 else baseColumns
}

/**
 * The spans of the cards [ZmanimWidget] lays out at [width], in order: first light, sunrise, Shema, Tefila, Chatzot,
 * Mincha (only where it fits), sunset, stars, Shabbat. Only the spans count for the height.
 */
internal fun zmanimCardSpans(width: Dp): List<Float> =
    listOfNotNull(0.5f, 0.5f, 1f, 1f, 1f, 1.5f.takeIf { width >= MIN_WIDTH_FOR_EXTRA_CARDS }, 0.5f, 1f, 1f)

/** The height of the zmanim cards at [width], known before composing them so the widgets beside can match it. */
internal fun zmanimGridHeight(width: Dp): Dp {
    val spans = zmanimCardSpans(width)
    val columns = zmanimColumns(width, spans.size)
    val rows = spans.toZmanimRows(spans.rowCapacity(columns) { it }) { it }.size.coerceAtLeast(1)
    return ZMANIM_CARD_HEIGHT * rows + ZMANIM_VERTICAL_SPACING * (rows - 1)
}

@Composable
private fun ZmanimCardsGrid(
    items: ImmutableList<ZmanimGridItem>,
    columns: Int,
    horizontalSpacing: Dp,
    verticalSpacing: Dp,
    selectedTimeMillis: Long?,
    modifier: Modifier = Modifier,
    compactMode: Boolean = false,
) {
    val capacity = items.rowCapacity(columns.coerceAtLeast(1)) { it.span }
    // As many rows as the greedy fill needs (the height counts on it), but shared out evenly, so none ends on a hole
    val rows = items.balancedRows(items.toZmanimRows(capacity) { it.span }.size) { it.span }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val width = maxWidth
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        ) {
            rows.forEach { rowItems ->
                // Width of one slot plus its gap: an item of span n spans n pitches minus one gap, so the row fills
                // the width whatever its item count (two half cards and their gap make exactly one card)
                val pitch = (width + horizontalSpacing) / rowItems.sumOf { it.span.toDouble() }.toFloat()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
                ) {
                    rowItems.forEach { item ->
                        when (item) {
                            is ZmanimGridItem.Moment -> {
                                val isSelected =
                                    selectedTimeMillis != null &&
                                        item.data.timeValue?.time == selectedTimeMillis
                                DayMomentCard(
                                    data = item.data,
                                    isSelected = isSelected,
                                    compactMode = compactMode,
                                    modifier = Modifier.width(pitch * item.span - horizontalSpacing),
                                    onClick = item.onClick,
                                )
                            }
                            is ZmanimGridItem.Shema -> {
                                val isLeftSelected =
                                    selectedTimeMillis != null &&
                                        item.mgaTimeValue?.time == selectedTimeMillis
                                val isRightSelected =
                                    selectedTimeMillis != null &&
                                        item.graTimeValue?.time == selectedTimeMillis
                                DualTimeCard(
                                    title = item.title,
                                    titleAbbrev = item.titleAbbrev,
                                    leftLabel = item.mgaLabel,
                                    leftTime = item.mgaTime,
                                    leftTimeAvailable = item.mgaTimeValue != null,
                                    leftSelected = isLeftSelected,
                                    rightLabel = item.graLabel,
                                    rightTime = item.graTime,
                                    rightTimeAvailable = item.graTimeValue != null,
                                    rightSelected = isRightSelected,
                                    onLeftClick = item.onMgaClick,
                                    onRightClick = item.onGraClick,
                                    compactMode = compactMode,
                                    modifier = Modifier.width(pitch * item.span - horizontalSpacing),
                                )
                            }
                            is ZmanimGridItem.Tefila -> {
                                val isLeftSelected =
                                    selectedTimeMillis != null &&
                                        item.mgaTimeValue?.time == selectedTimeMillis
                                val isRightSelected =
                                    selectedTimeMillis != null &&
                                        item.graTimeValue?.time == selectedTimeMillis
                                DualTimeCard(
                                    title = item.title,
                                    titleAbbrev = item.titleAbbrev,
                                    leftLabel = item.mgaLabel,
                                    leftTime = item.mgaTime,
                                    leftTimeAvailable = item.mgaTimeValue != null,
                                    leftSelected = isLeftSelected,
                                    rightLabel = item.graLabel,
                                    rightTime = item.graTime,
                                    rightTimeAvailable = item.graTimeValue != null,
                                    rightSelected = isRightSelected,
                                    onLeftClick = item.onMgaClick,
                                    onRightClick = item.onGraClick,
                                    compactMode = compactMode,
                                    modifier = Modifier.width(pitch * item.span - horizontalSpacing),
                                )
                            }
                            is ZmanimGridItem.VisibleStars -> {
                                val isLeftSelected =
                                    selectedTimeMillis != null &&
                                        item.geonimTimeValue?.time == selectedTimeMillis
                                val isRightSelected =
                                    selectedTimeMillis != null &&
                                        item.rabbeinuTamTimeValue?.time == selectedTimeMillis
                                DualTimeCard(
                                    title = item.title,
                                    titleAbbrev = item.titleAbbrev,
                                    leftLabel = item.geonimLabel,
                                    leftLabelAbbrev = item.geonimLabelAbbrev,
                                    leftTime = item.geonimTime,
                                    leftTimeAvailable = item.geonimTimeValue != null,
                                    leftSelected = isLeftSelected,
                                    rightLabel = item.rabbeinuTamLabel,
                                    rightLabelAbbrev = item.rabbeinuTamLabelAbbrev,
                                    rightTime = item.rabbeinuTamTime,
                                    rightTimeAvailable = item.rabbeinuTamTimeValue != null,
                                    rightSelected = isRightSelected,
                                    onLeftClick = item.onGeonimClick,
                                    onRightClick = item.onRabbeinuTamClick,
                                    compactMode = compactMode,
                                    modifier = Modifier.width(pitch * item.span - horizontalSpacing),
                                )
                            }
                            is ZmanimGridItem.Mincha -> {
                                fun Date?.isSelected() = selectedTimeMillis != null && this?.time == selectedTimeMillis
                                DualTimeCardContent(
                                    title = stringResource(Res.string.home_widget_mincha_title),
                                    leftLabel = stringResource(Res.string.home_widget_mincha_gedola_label),
                                    leftTime = item.gedolaTime,
                                    leftTimeAvailable = item.gedolaTimeValue != null,
                                    leftSelected = item.gedolaTimeValue.isSelected(),
                                    middleLabel = stringResource(Res.string.home_widget_mincha_ketana_label),
                                    middleTime = item.ketanaTime,
                                    middleTimeAvailable = item.ketanaTimeValue != null,
                                    middleSelected = item.ketanaTimeValue.isSelected(),
                                    rightLabel = stringResource(Res.string.home_widget_mincha_plag_label),
                                    rightTime = item.plagTime,
                                    rightTimeAvailable = item.plagTimeValue != null,
                                    rightSelected = item.plagTimeValue.isSelected(),
                                    onLeftClick = item.onGedolaClick,
                                    onMiddleClick = item.onKetanaClick,
                                    onRightClick = item.onPlagClick,
                                    compactMode = compactMode,
                                    modifier = Modifier.width(pitch * item.span - horizontalSpacing),
                                )
                            }
                            is ZmanimGridItem.Shabbat -> {
                                val isLeftSelected =
                                    selectedTimeMillis != null &&
                                        item.entryTimeValue?.time == selectedTimeMillis
                                val isRightSelected =
                                    selectedTimeMillis != null &&
                                        item.exitTimeValue?.time == selectedTimeMillis
                                ShabbatDualTimeCard(
                                    title = item.title,
                                    entryLabel = item.entryLabel,
                                    entryTime = item.entryTime,
                                    entryTimeAvailable = item.entryTimeValue != null,
                                    entrySelected = isLeftSelected,
                                    exitLabel = item.exitLabel,
                                    exitTime = item.exitTime,
                                    exitTimeAvailable = item.exitTimeValue != null,
                                    exitSelected = isRightSelected,
                                    onEntryClick = item.onEntryClick,
                                    onExitClick = item.onExitClick,
                                    compactMode = compactMode,
                                    modifier = Modifier.width(pitch * item.span - horizontalSpacing),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayMomentCard(
    data: DayMomentCardData,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    compactMode: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val isDark = JewelTheme.isDark
    val shape = RoundedCornerShape(18.dp)
    val panelBackground = JewelTheme.globalColors.panelBackground
    val borderColor =
        if (isDark) {
            JewelTheme.globalColors.borders.disabled
        } else {
            JewelTheme.globalColors.borders.normal
        }
    val labelColor =
        JewelTheme.globalColors.text.normal
            .copy(alpha = 0.78f)
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()
    val isClickable = onClick != null
    val selectionBorder = JewelTheme.globalColors.borders.focused
    val hoverBorder =
        if (isDark) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.6f)
    val hoverModifier =
        if (isClickable) {
            Modifier.hoverable(hoverSource).pointerHoverIcon(PointerIcon.Hand)
        } else {
            Modifier
        }
    val clickModifier =
        if (onClick != null) {
            Modifier.clickable(onClick = onClick)
        } else {
            Modifier
        }
    val effectiveBorder =
        when {
            isSelected -> selectionBorder
            isClickable && isHovered -> hoverBorder
            else -> borderColor
        }
    val glassBackground =
        panelBackground.copy(alpha = if (isDark) 0.25f else 0.35f)

    Box(
        modifier =
            modifier
                .height(ZMANIM_CARD_HEIGHT)
                .clip(shape)
                .then(hoverModifier)
                .then(clickModifier)
                .background(glassBackground, shape)
                .border(1.5.dp, effectiveBorder, shape),
    ) {
        if (isSelected) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .border(2.5.dp, selectionBorder, shape),
            )
        }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    // Half-width card: a tighter inset keeps abbreviated titles whole
                    .padding(horizontal = 4.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start,
        ) {
            CardTitleRow(
                text = stringResource(data.title),
                abbreviation = data.titleAbbrev?.let { stringResource(it) },
                dotStart = data.accentStart,
                dotEnd = data.accentEnd,
                color = labelColor,
                compactMode = compactMode,
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                TimeValue(
                    time = data.time,
                    color = JewelTheme.globalColors.text.normal,
                    compactMode = compactMode,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Card title with its accent dot; the dot is dropped on cards too narrow to hold both. */
@Composable
private fun CardTitleRow(
    text: String,
    abbreviation: String?,
    dotStart: Color,
    dotEnd: Color,
    color: Color,
    compactMode: Boolean,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val showDot = maxWidth >= MIN_TITLE_WIDTH_WITH_DOT
        if (showDot) {
            GradientDot(
                colorStart = dotStart,
                colorEnd = dotEnd,
                size = 11.dp,
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
        AdaptiveCardTitle(
            text = text,
            abbreviation = abbreviation,
            color = color,
            compactMode = compactMode,
            // Symmetric inset keeps the title centered without running under the dot
            modifier = Modifier.padding(horizontal = if (showDot) 15.dp else 0.dp),
        )
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun AdaptiveCardTitle(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    abbreviation: String? = null,
    compactMode: Boolean = false,
) {
    AdaptiveSingleLineText(
        text = text,
        abbreviation = abbreviation,
        color = color,
        fontSize = if (compactMode) 11.sp else 13.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun AdaptiveSingleLineText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
    abbreviation: String? = null,
) {
    val resolvedAbbrev = abbreviation?.takeIf { it.isNotBlank() }
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val maxWidthPx = constraints.maxWidth
        val textMeasurer = rememberTextMeasurer()
        val shouldAbbreviate =
            remember(text, resolvedAbbrev, maxWidthPx) {
                if (resolvedAbbrev == null || maxWidthPx == Constraints.Infinity || maxWidthPx <= 0) {
                    false
                } else {
                    val layoutResult =
                        textMeasurer.measure(
                            text = AnnotatedString(text),
                            style =
                                TextStyle(
                                    color = color,
                                    fontSize = fontSize,
                                    fontWeight = fontWeight,
                                    textAlign = textAlign,
                                ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            constraints = Constraints(maxWidth = maxWidthPx),
                        )
                    layoutResult.didOverflowWidth || layoutResult.hasVisualOverflow
                }
            }
        val displayText = if (shouldAbbreviate) resolvedAbbrev ?: text else text

        Text(
            text = displayText,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
        )
    }
}

@Composable
private fun DualTimeCard(
    title: StringResource,
    leftLabel: StringResource,
    leftTime: String,
    leftTimeAvailable: Boolean,
    leftSelected: Boolean,
    rightLabel: StringResource,
    rightTime: String,
    rightTimeAvailable: Boolean,
    rightSelected: Boolean,
    modifier: Modifier = Modifier,
    titleAbbrev: StringResource? = null,
    leftLabelAbbrev: StringResource? = null,
    rightLabelAbbrev: StringResource? = null,
    onLeftClick: (() -> Unit)? = null,
    onRightClick: (() -> Unit)? = null,
    compactMode: Boolean = false,
) {
    DualTimeCardContent(
        title = stringResource(title),
        titleAbbrev = titleAbbrev?.let { stringResource(it) },
        leftLabel = stringResource(leftLabel),
        leftLabelAbbrev = leftLabelAbbrev?.let { stringResource(it) },
        leftTime = leftTime,
        leftTimeAvailable = leftTimeAvailable,
        leftSelected = leftSelected,
        rightLabel = stringResource(rightLabel),
        rightLabelAbbrev = rightLabelAbbrev?.let { stringResource(it) },
        rightTime = rightTime,
        rightTimeAvailable = rightTimeAvailable,
        rightSelected = rightSelected,
        modifier = modifier,
        onLeftClick = onLeftClick,
        onRightClick = onRightClick,
        compactMode = compactMode,
    )
}

@Composable
private fun DualTimeCardContent(
    title: String,
    leftLabel: String,
    leftTime: String,
    leftTimeAvailable: Boolean,
    leftSelected: Boolean,
    rightLabel: String,
    rightTime: String,
    rightTimeAvailable: Boolean,
    rightSelected: Boolean,
    modifier: Modifier = Modifier,
    titleAbbrev: String? = null,
    leftLabelAbbrev: String? = null,
    rightLabelAbbrev: String? = null,
    onLeftClick: (() -> Unit)? = null,
    onRightClick: (() -> Unit)? = null,
    middleLabel: String? = null,
    middleTime: String = "",
    middleTimeAvailable: Boolean = false,
    middleSelected: Boolean = false,
    onMiddleClick: (() -> Unit)? = null,
    backgroundOverride: Brush? = null,
    borderColorOverride: Color? = null,
    accentStartOverride: Color? = null,
    accentEndOverride: Color? = null,
    premiumOverlay: Brush? = null,
    compactMode: Boolean = false,
) {
    val isDark = JewelTheme.isDark
    val accent = rememberAccentColor(isDark)
    val shape = RoundedCornerShape(18.dp)
    val panelBackground = JewelTheme.globalColors.panelBackground
    val borderColor =
        if (isDark) {
            JewelTheme.globalColors.borders.disabled
        } else {
            JewelTheme.globalColors.borders.normal
        }
    val labelColor =
        JewelTheme.globalColors.text.normal
            .copy(alpha = 0.78f)
    val accentStart = accent.blendTowards(Color.White, 0.35f)
    val accentEnd = accent.blendTowards(Color.White, 0.55f)
    val resolvedBorderColor = borderColorOverride ?: borderColor
    val resolvedAccentStart = accentStartOverride ?: accentStart
    val resolvedAccentEnd = accentEndOverride ?: accentEnd
    val dividerColor = resolvedBorderColor.copy(alpha = 0.7f)
    val leftClick = onLeftClick
    val rightClick = onRightClick
    val leftClickable = leftClick != null && leftTimeAvailable
    val rightClickable = rightClick != null && rightTimeAvailable
    val middleClickable = middleLabel != null && onMiddleClick != null && middleTimeAvailable
    val isClickable = leftClickable || middleClickable || rightClickable
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()
    val isSelected = leftSelected || middleSelected || rightSelected
    val selectionBorder = JewelTheme.globalColors.borders.focused
    val hoverBorder =
        if (isDark) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.6f)
    val leftModifier =
        if (leftClickable) {
            Modifier
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = leftClick)
        } else {
            Modifier
        }
    val middleModifier =
        if (middleClickable) {
            Modifier
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = onMiddleClick)
        } else {
            Modifier
        }
    val rightModifier =
        if (rightClickable) {
            Modifier
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = rightClick)
        } else {
            Modifier
        }
    val effectiveBorder =
        when {
            isSelected -> selectionBorder
            isClickable && isHovered -> hoverBorder
            else -> resolvedBorderColor
        }
    val cardHoverModifier =
        if (isClickable) Modifier.hoverable(hoverSource) else Modifier
    val glassBackground =
        panelBackground.copy(alpha = if (isDark) 0.25f else 0.35f)
    val bgModifier =
        if (backgroundOverride != null) {
            Modifier.background(backgroundOverride)
        } else {
            Modifier.background(glassBackground, shape)
        }

    Box(
        modifier =
            modifier
                .height(ZMANIM_CARD_HEIGHT)
                .clip(shape)
                .then(cardHoverModifier)
                .then(bgModifier)
                .border(1.5.dp, effectiveBorder, shape),
    ) {
        if (premiumOverlay != null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(premiumOverlay),
            )
        }
        if (isSelected) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .border(2.5.dp, selectionBorder, shape),
            )
        }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start,
        ) {
            CardTitleRow(
                text = title,
                abbreviation = titleAbbrev,
                dotStart = resolvedAccentStart,
                dotEnd = resolvedAccentEnd,
                color = labelColor,
                compactMode = compactMode,
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                val labelFontSize = if (compactMode) 10.sp else 12.sp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                ) {
                    TimeSlot(
                        label = leftLabel,
                        labelAbbrev = leftLabelAbbrev,
                        time = leftTime,
                        labelColor = labelColor,
                        labelFontSize = labelFontSize,
                        compactMode = compactMode,
                    )
                    if (middleLabel != null) {
                        SlotDivider(dividerColor)
                        TimeSlot(
                            label = middleLabel,
                            labelAbbrev = null,
                            time = middleTime,
                            labelColor = labelColor,
                            labelFontSize = labelFontSize,
                            compactMode = compactMode,
                        )
                    }
                    SlotDivider(dividerColor)
                    TimeSlot(
                        label = rightLabel,
                        labelAbbrev = rightLabelAbbrev,
                        time = rightTime,
                        labelColor = labelColor,
                        labelFontSize = labelFontSize,
                        compactMode = compactMode,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(leftModifier),
            )
            if (middleLabel != null) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(middleModifier),
                )
            }
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(rightModifier),
            )
        }
    }
}

@Composable
private fun RowScope.TimeSlot(
    label: String,
    labelAbbrev: String?,
    time: String,
    labelColor: Color,
    labelFontSize: TextUnit,
    compactMode: Boolean,
) {
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AdaptiveSingleLineText(
            text = label,
            abbreviation = labelAbbrev,
            color = labelColor,
            fontSize = labelFontSize,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        TimeValue(
            time = time,
            color = JewelTheme.globalColors.text.normal,
            compactMode = compactMode,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RowScope.SlotDivider(color: Color) {
    Divider(
        orientation = Orientation.Vertical,
        modifier =
            Modifier
                .fillMaxHeight(0.5f)
                .align(Alignment.CenterVertically)
                .width(1.dp),
        color = color,
    )
}

@Composable
private fun TimeValue(
    time: String,
    color: Color,
    modifier: Modifier = Modifier,
    compactMode: Boolean = false,
) {
    val primaryFontSize = if (compactMode) 18.sp else 22.sp
    val secondaryFontSize = if (compactMode) 17.sp else 21.sp
    val parts = remember(time) { splitTimeParts(time) }
    if (parts == null) {
        Text(
            text = time,
            color = color,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            fontSize = primaryFontSize,
            modifier = modifier,
        )
        return
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = parts.first,
            color = color,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            fontSize = primaryFontSize,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = parts.second,
            color = color.copy(alpha = 0.8f),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            fontSize = secondaryFontSize,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun splitTimeParts(time: String): Pair<String, String>? {
    val sanitized = time.replace("\u2066", "").replace("\u2069", "").trim()
    if (sanitized.isEmpty()) return null
    val parts = sanitized.split(":")
    if (parts.size != 2) return null
    val hours = parts[0].trim()
    val minutes = parts[1].trim()
    if (hours.isEmpty() || minutes.isEmpty()) return null
    return hours to minutes
}

@Composable
private fun ShabbatDualTimeCard(
    title: String,
    entryLabel: StringResource,
    entryTime: String,
    entryTimeAvailable: Boolean,
    entrySelected: Boolean,
    exitLabel: StringResource,
    exitTime: String,
    exitTimeAvailable: Boolean,
    exitSelected: Boolean,
    modifier: Modifier = Modifier,
    onEntryClick: (() -> Unit)? = null,
    onExitClick: (() -> Unit)? = null,
    compactMode: Boolean = false,
) {
    val isDark = JewelTheme.isDark
    val accent = rememberAccentColor(isDark)
    val panelBackground = JewelTheme.globalColors.panelBackground
    val premiumStart =
        if (isDark) {
            panelBackground.blendTowards(accent.copy(alpha = 0.8f), 0.65f)
        } else {
            panelBackground.blendTowards(Color.White, 0.92f)
        }
    val premiumMid =
        if (isDark) {
            panelBackground.blendTowards(accent, 0.55f)
        } else {
            panelBackground.blendTowards(Color.White, 0.86f)
        }
    val premiumEnd =
        if (isDark) {
            panelBackground.blendTowards(accent.copy(alpha = 0.6f), 0.45f)
        } else {
            panelBackground.blendTowards(Color.White, 0.8f)
        }
    val background = Brush.verticalGradient(listOf(premiumStart, premiumMid, premiumEnd))
    val borderColor = accent
    val accentStart = accent.blendTowards(Color.White, 0.35f)
    val accentEnd = accent.blendTowards(Color.White, 0.55f)
    val sheen =
        if (isDark) {
            Brush.horizontalGradient(
                listOf(
                    Color.White.copy(alpha = 0.14f),
                    Color.Transparent,
                    Color.White.copy(alpha = 0.08f),
                ),
            )
        } else {
            Brush.horizontalGradient(
                listOf(
                    Color.White.copy(alpha = 0.3f),
                    Color.Transparent,
                    Color.White.copy(alpha = 0.12f),
                ),
            )
        }

    DualTimeCardContent(
        title = title,
        leftLabel = stringResource(entryLabel),
        leftTime = entryTime,
        leftTimeAvailable = entryTimeAvailable,
        leftSelected = entrySelected,
        rightLabel = stringResource(exitLabel),
        rightTime = exitTime,
        rightTimeAvailable = exitTimeAvailable,
        rightSelected = exitSelected,
        modifier = modifier,
        onLeftClick = onEntryClick,
        onRightClick = onExitClick,
        backgroundOverride = background,
        borderColorOverride = borderColor,
        accentStartOverride = accentStart,
        accentEndOverride = accentEnd,
        premiumOverlay = sheen,
        compactMode = compactMode,
    )
}

@Composable
private fun GradientDot(
    colorStart: Color,
    colorEnd: Color,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .background(
                    brush = Brush.radialGradient(listOf(colorStart, colorEnd)),
                    shape = CircleShape,
                ).border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
    )
}

private fun computeShabbatTimes(
    date: LocalDate,
    location: EarthWidgetLocation,
    opinion: ZmanimOpinion = ZmanimOpinion.ITIM_LABINA,
    cityLabel: String? = null,
    inIsrael: Boolean = false,
): ShabbatTimes {
    val shabbatDate = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
    val fridayDate = shabbatDate.minusDays(1)

    val parashaName =
        run {
            val jewishCalendar = JewishCalendar(shabbatDate.toKotlinLocalDate(), inIsrael)
            val formatter =
                HebrewDateFormatter().apply {
                    isHebrewFormat = true
                    isUseGershGershayim = false
                }
            formatter.formatParsha(jewishCalendar)?.takeIf { it.isNotBlank() }
                ?: formatter.formatSpecialParsha(jewishCalendar).orEmpty()
        }
    val parashaTitle = if (parashaName.isBlank()) "שבת" else "שבת $parashaName"

    val entryTime = candleLightingTime(fridayDate, location, opinion, cityLabel, inIsrael)
    val exitTime = havdalahTime(shabbatDate, location, opinion, inIsrael)

    return ShabbatTimes(
        parashaName = parashaTitle,
        entryTime = entryTime,
        exitTime = exitTime,
    )
}

/** Candle lighting on [eve], the day before a Shabbat or Yom Tov, as [opinion]'s luach prints it. */
internal fun candleLightingTime(
    eve: LocalDate,
    location: EarthWidgetLocation,
    opinion: ZmanimOpinion,
    cityLabel: String?,
    inIsrael: Boolean,
): Date? {
    val calendar = zmanimCalendar(eve, location, opinion, inIsrael)
    return when (opinion) {
        // אור החיים: candles 20 minutes before sunset everywhere
        ZmanimOpinion.OHR_HACHAIM -> calendar.ohrHaChaimSunset?.minus(20.minutes)?.toDate()

        // עתים לבינה: candles follow the city's own custom
        ZmanimOpinion.ITIM_LABINA -> {
            val candles =
                itimLabinaCandleLighting[cityLabel?.trim()]
                    ?: itimLabinaCandleLighting["ירושלים"]?.takeIf { isJerusalemLocation(location, cityLabel) }
                    ?: if (inIsrael) ITIM_LABINA_ISRAEL_CANDLES else ITIM_LABINA_ABROAD_CANDLES
            val sunset = if (candles.fromHeight) calendar.sunset else calendar.seaLevelSunset
            sunset?.minus(candles.minutes.minutes)?.toDate()
        }
    }
}

/** The end of the Shabbat or Yom Tov of [day], as [opinion]'s luach prints it. */
internal fun havdalahTime(
    day: LocalDate,
    location: EarthWidgetLocation,
    opinion: ZmanimOpinion,
    inIsrael: Boolean,
): Date? {
    val calendar = zmanimCalendar(day, location, opinion, inIsrael)
    return when (opinion) {
        // אור החיים: 30 minutes after sunset in Israel
        // ponytail: abroad the RO calendar defaults to Amudei Horaah (7.165°); we keep its fixed-minutes choice, 40
        ZmanimOpinion.OHR_HACHAIM -> {
            calendar.ateretTorahSunsetOffset = if (inIsrael) 30.0 else 40.0
            calendar.tzaisAteretTorah.toDate()
        }

        // עתים לבינה: 8.5° everywhere
        ZmanimOpinion.ITIM_LABINA -> calendar.tzais.toDate()
    }
}

private fun isJerusalemLocation(
    location: EarthWidgetLocation,
    cityLabel: String?,
): Boolean {
    val trimmedLabel = cityLabel?.trim().orEmpty()
    if (trimmedLabel == "ירושלים" || trimmedLabel.equals("Jerusalem", ignoreCase = true)) {
        return true
    }

    val jerusalem = worldPlaces["ישראל"]?.get("ירושלים") ?: return false
    val latDiff = abs(location.latitude - jerusalem.lat)
    val lonDiff = abs(location.longitude - jerusalem.lng)
    return latDiff < 0.1 && lonDiff < 0.1
}

private fun Color.blendTowards(
    target: Color,
    ratio: Float,
): Color {
    val clamped = ratio.coerceIn(0f, 1f)
    val inverse = 1f - clamped
    return Color(
        red = red * inverse + target.red * clamped,
        green = green * inverse + target.green * clamped,
        blue = blue * inverse + target.blue * clamped,
        alpha = alpha * inverse + target.alpha * clamped,
    )
}
