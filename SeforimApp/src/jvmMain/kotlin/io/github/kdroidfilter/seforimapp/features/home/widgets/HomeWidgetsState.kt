package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.earthwidget.EarthWidgetLocation
import io.github.kdroidfilter.seforimapp.earthwidget.KiddushLevanaEarliestOpinion
import io.github.kdroidfilter.seforimapp.earthwidget.KiddushLevanaLatestOpinion
import io.github.kdroidfilter.seforimapp.earthwidget.ZmanimOpinion
import io.github.kdroidfilter.seforimapp.earthwidget.timeZoneForLocation
import io.github.kdroidfilter.seforimapp.features.onboarding.userprofile.Community
import io.github.kdroidfilter.seforimapp.features.zmanim.data.ISRAEL_COUNTRY_NAME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.util.Date

/**
 * What the Home widgets share: the day and moment they show, and where. A zman card sets [targetTime], the Earth
 * widget [selectDate] / [selectLocation], and every widget follows.
 */
@Stable
class HomeWidgetsState internal constructor(
    private val user: HomeUserLocation,
    community: Community?,
    internal val layout: HomeWidgetsLayout,
) {
    private val sephardi = community == Community.SEPHARADE

    // עדות המזרח read the אור החיים, everyone else עתים לבינה
    val zmanimOpinion = if (sephardi) ZmanimOpinion.OHR_HACHAIM else ZmanimOpinion.ITIM_LABINA
    val kiddushLevanaEarliest = if (sephardi) KiddushLevanaEarliestOpinion.DAYS_7 else KiddushLevanaEarliestOpinion.DAYS_3
    val kiddushLevanaLatest = if (sephardi) KiddushLevanaLatestOpinion.DAYS_15 else KiddushLevanaLatestOpinion.BETWEEN_MOLDOS

    /** The user's own region, whatever location is picked on the globe. */
    val userInIsrael get() = user.inIsrael

    private val userLocation =
        EarthWidgetLocation(
            latitude = user.userPlace.lat,
            longitude = user.userPlace.lng,
            elevationMeters = user.userPlace.elevation,
            timeZone = timeZoneForLocation(user.userPlace.lat, user.userPlace.lng),
        )

    private class PickedLocation(
        val location: EarthWidgetLocation,
        val city: String,
        val inIsrael: Boolean,
    )

    // Picked on the globe for a look around; never written to the user settings
    private var picked by mutableStateOf<PickedLocation?>(null)

    val location: EarthWidgetLocation get() = picked?.location ?: userLocation
    val cityLabel: String? get() = picked?.city ?: user.userCityLabel
    val inIsrael: Boolean get() = picked?.inIsrael ?: user.inIsrael

    private var today by mutableStateOf(todayAt(userLocation))
    var selectedDate by mutableStateOf(today)
        private set

    /** The moment picked on a zman card; null shows [selectedDate] at noon (or now, today). */
    var targetTime by mutableStateOf<Date?>(null)

    /**
     * The instant being played, the one clock every widget reads (the solar system, the Earth, the sky, the luach):
     * advanced by [runPlayClock], frame by frame; null when not playing.
     */
    var playingMillis by mutableStateOf<Long?>(null)
        private set

    // Its own state: the day widgets recompose when the day changes, not at each frame of the play
    private var playingDay by mutableStateOf<LocalDate?>(null)

    val playing: Boolean get() = playingMillis != null

    private fun setPlaying(millis: Long?) {
        playingMillis = millis
        playingDay = millis?.let { Instant.ofEpochMilli(it).atZone(location.timeZone.toZoneId()).toLocalDate() }
    }

    /** Plays from the moment shown, or stops on the moment reached: it stays shown, to the minute, everywhere. */
    fun togglePlay() {
        val reached = playingMillis
        if (reached == null) {
            setPlaying(skyTimeMillis ?: System.currentTimeMillis())
        } else {
            setPlaying(null)
            selectDate(Instant.ofEpochMilli(reached).atZone(location.timeZone.toZoneId()).toLocalDate())
            targetTime = Date(reached)
        }
    }

    /** Stops playing where it is (its window closing). */
    fun stopPlay() {
        if (playing) togglePlay()
    }

    /** Runs the clock while playing, at [daysPerSecond] simulated days a real second (read at each frame). */
    suspend fun runPlayClock(daysPerSecond: () -> Float) {
        var last = 0L
        while (playing) {
            withFrameNanos { now ->
                if (last != 0L) {
                    playingMillis?.let { setPlaying(it + ((now - last) / 1e9 * daysPerSecond() * DAY_MILLIS).toLong()) }
                }
                last = now
            }
        }
    }

    /** The day shown: the one played at while the solar system plays, else [selectedDate]. */
    val shownDate: LocalDate get() = playingDay ?: selectedDate

    /** The moment the sky and the solar system show; null means now. */
    val skyTimeMillis: Long? get() = playingMillis ?: restingSkyMillis

    /** [skyTimeMillis] when not playing. */
    internal val restingSkyMillis: Long?
        get() =
            targetTime?.time ?: if (selectedDate == today) {
                null
            } else {
                selectedDate
                    .atTime(12, 0)
                    .atZone(location.timeZone.toZoneId())
                    .toInstant()
                    .toEpochMilli()
            }

    var solarSystemFullscreen by mutableStateOf(false)

    /** Opens a destination in a new tab of the Home's window; set by the Home, a no-op where there's no window. */
    var openTab: (TabsDestination) -> Unit = {}

    /** The Home's edit mode, as on macOS: widgets can be moved and removed, and the gallery adds new ones. */
    var editingWidgets by mutableStateOf(false)

    /** The widget whose resize frame shows, in edit mode. */
    var selectedWidget by mutableStateOf<String?>(null)

    /** A widget being dragged, from the grid or the gallery. */
    internal val drag = WidgetDrag()

    /**
     * Whether the page around the widgets must hold still: in edit mode, and while a widget is dragged, whose drop
     * area may make the grid taller or shorter (a centred page would jump under the pointer).
     */
    val pageHeld: Boolean
        get() = editingWidgets || drag.movingId != null || drag.resizing != null || drag.newWidget != null

    /** The widget just removed, while its undo is offered. */
    internal var lastRemoved by mutableStateOf<RemovedWidget?>(null)
        private set

    internal fun removeWidget(widget: HomeWidget) {
        lastRemoved = layout.remove(widget)
    }

    internal fun undoRemove() {
        lastRemoved?.let(layout::restore)
        lastRemoved = null
    }

    internal fun forgetRemoved(removed: RemovedWidget) {
        if (lastRemoved === removed) lastRemoved = null
    }

    /** The widget whose [HomeWidget.Options] page is open in a dialog, from an item of its menu. */
    var optionsOpen by mutableStateOf<HomeWidget?>(null)

    /** [widget]'s own options, set in its options dialog; null while at its defaults. */
    @Composable
    fun optionsOf(widget: HomeWidget): String? = layout.options.collectAsState().value[widget.id]

    /** Null goes back to [widget]'s defaults. */
    fun setOptions(
        widget: HomeWidget,
        options: String?,
    ) = layout.setOptions(widget, options)

    fun selectDate(date: LocalDate) {
        // A day picked while playing: the play stops there, rather than going on and dropping it at the pause
        if (playingMillis != null) setPlaying(null)
        selectedDate = date
        targetTime = null
    }

    fun selectLocation(
        country: String,
        city: String,
        location: EarthWidgetLocation,
    ) {
        picked = PickedLocation(location, city, country == ISRAEL_COUNTRY_NAME)
        targetTime = null
        // Another time zone may already be on another day
        val newToday = todayAt(location)
        if (newToday != today) {
            today = newToday
            selectedDate = newToday
        }
    }

    private fun todayAt(location: EarthWidgetLocation) = LocalDate.now(location.timeZone.toZoneId())
}

/**
 * [compute] for [keys], run off the UI thread, the first time too: null until that first value is ready (the card shows
 * [WidgetCardLoading] meanwhile), so opening the Home never waits for its calendars. Then the last value stays shown
 * until the new one is ready, so a widget following the solar system's play (a new day 6 times a second) never stalls
 * a frame.
 */
@Composable
internal fun <T : Any> rememberOffMain(
    vararg keys: Any?,
    compute: () -> T,
): T? {
    val latest by rememberUpdatedState(compute)
    return produceState<T?>(null, *keys) { value = withContext(Dispatchers.Default) { latest() } }.value
}

private const val DAY_MILLIS = 86_400_000.0

/**
 * Whether the widgets composed here are on show while the solar system's full window plays: true in that window;
 * false on the Home, hidden behind it, whose 3D cards then hold still (see [playMillisHere]) instead of rendering a
 * picture nobody sees at every frame. They catch up when the window closes.
 */
internal val LocalFollowsPlay = compositionLocalOf { false }

/** [HomeWidgetsState.playingMillis] for the widgets composed here: held still on the Home behind the full window. */
@Composable
internal fun HomeWidgetsState.playMillisHere(): Long? {
    val follow = LocalFollowsPlay.current || !solarSystemFullscreen
    val held = remember(follow) { Snapshot.withoutReadObservation { playingMillis } }
    return if (follow) playingMillis else held
}

/** [HomeWidgetsState.shownDate] for the widgets composed here: held still on the Home behind the full window. */
@Composable
internal fun HomeWidgetsState.shownDateHere(): LocalDate {
    val follow = LocalFollowsPlay.current || !solarSystemFullscreen
    val held = remember(follow) { Snapshot.withoutReadObservation { shownDate } }
    return if (follow) shownDate else held
}

/** [HomeWidgetsState.skyTimeMillis] for the widgets composed here (see [playMillisHere]). */
@Composable
internal fun HomeWidgetsState.skyTimeMillisHere(): Long? = playMillisHere() ?: restingSkyMillis
