package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.earthwidget.EarthWidgetLocation
import io.github.kdroidfilter.seforimapp.earthwidget.KiddushLevanaEarliestOpinion
import io.github.kdroidfilter.seforimapp.earthwidget.KiddushLevanaLatestOpinion
import io.github.kdroidfilter.seforimapp.earthwidget.ZmanimOpinion
import io.github.kdroidfilter.seforimapp.earthwidget.timeZoneForLocation
import io.github.kdroidfilter.seforimapp.features.onboarding.userprofile.Community
import io.github.kdroidfilter.seforimapp.features.zmanim.data.ISRAEL_COUNTRY_NAME
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

    /** The moment the sky and the solar system show; null means now. */
    val skyTimeMillis: Long?
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

    fun selectDate(date: LocalDate) {
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
