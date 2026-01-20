package io.github.kdroidfilter.seforimapp.earthwidget

import com.kosherjava.zmanim.AstronomicalCalendar
import com.kosherjava.zmanim.ZmanimCalendar
import com.kosherjava.zmanim.util.GeoLocation
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar

/**
 * Zmanim calendar extension based on the opinions of Rabbi Ovadiah Yosef ZT"L.
 *
 * This class extends KosherJava's ZmanimCalendar to provide zmanim calculations
 * according to the "Luach Hamaor, Ohr HaChaim" calendar, which was created under
 * the guidance of Rabbi Ovadiah Yosef ZT"L.
 *
 * Key differences from standard calculations:
 * - Alos Hashachar: 72 zmaniyot minutes (1/10 of the day) before sunrise
 * - Tzais: 13.5 zmaniyot minutes after sunset (Geonim opinion)
 * - Sof Zman Shema/Tefila MGA: Based on 72 zmaniyot minutes alos/tzais
 * - Plag HaMincha: 1.25 hours before tzais (not before sunset)
 * - Tzais Rabbeinu Tam: 72 zmaniyot minutes after sunset
 *
 * Based on: https://github.com/Elyahu41/RabbiOvadiahYosefCalendarApp
 */
class ROZmanimCalendar(
    geoLocation: GeoLocation,
) : ZmanimCalendar(geoLocation) {
    companion object {
        private const val MINUTES_PER_HOUR = 60
        private const val MILLISECONDS_PER_MINUTE = 60_000L
    }

    var isUseAmudehHoraah: Boolean = false

    init {
        isUseElevation = true
    }

    /**
     * Calculates a zmanis-based offset from sunrise or sunset.
     *
     * @param hours Number of shaos zmaniyos. Negative for before sunrise, positive for after sunset.
     * @return The calculated time, or null if calculation is not possible.
     */
    private fun getZmanisBasedOffset(hours: Double): Date? {
        val shaahZmanis = shaahZmanisGra
        if (shaahZmanis == Long.MIN_VALUE || hours == 0.0) {
            return null
        }

        return if (hours > 0) {
            getTimeOffset(elevationAdjustedSunset, (shaahZmanis * hours).toLong())
        } else {
            getTimeOffset(elevationAdjustedSunrise, (shaahZmanis * hours).toLong())
        }
    }

    private fun getPercentOfShaahZmanisFromDegrees(
        degrees: Double,
        sunset: Boolean,
    ): Double {
        val seaLevelSunrise = seaLevelSunrise
        val seaLevelSunset = seaLevelSunset
        val twilight =
            if (sunset) {
                getSunsetOffsetByDegrees(AstronomicalCalendar.GEOMETRIC_ZENITH + degrees)
            } else {
                getSunriseOffsetByDegrees(AstronomicalCalendar.GEOMETRIC_ZENITH + degrees)
            }

        if (seaLevelSunrise == null || seaLevelSunset == null || twilight == null) {
            return Double.MIN_VALUE
        }

        val shaahZmanis = (seaLevelSunset.time - seaLevelSunrise.time) / 12.0
        val riseSetToTwilight =
            if (sunset) {
                twilight.time - seaLevelSunset.time
            } else {
                seaLevelSunrise.time - twilight.time
            }
        return riseSetToTwilight / shaahZmanis
    }

    private fun getEquinoxPercentage(
        degrees: Double,
        sunset: Boolean,
    ): Double? {
        val originalCalendar = calendar
        val equinoxCalendar =
            GregorianCalendar(originalCalendar.timeZone).apply {
                set(originalCalendar.get(Calendar.YEAR), Calendar.MARCH, 17, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
        calendar = equinoxCalendar
        return try {
            val percentage = getPercentOfShaahZmanisFromDegrees(degrees, sunset)
            if (percentage == Double.MIN_VALUE) null else percentage
        } finally {
            calendar = originalCalendar
        }
    }

    /**
     * Returns alos hashachar (dawn) calculated as 72 zmaniyot minutes before sunrise.
     *
     * This is 1/10th of the day before sunrise, based on the opinion that the day
     * is calculated from alos to tzais (72 minutes each way).
     *
     * This is the primary alos calculation used in the Ohr HaChaim calendar.
     */
    fun getAlotHashachar72Zmaniyot(): Date? {
        if (!isUseAmudehHoraah) {
            return getZmanisBasedOffset(-1.2) // 72/60 = 1.2 hours
        }

        val percentage = getEquinoxPercentage(16.04, false) ?: return null
        val shaahZmanit = getTemporalHour(elevationAdjustedSunrise, elevationAdjustedSunset)
        if (shaahZmanit == Long.MIN_VALUE) return null
        return getTimeOffset(elevationAdjustedSunrise, -(percentage * shaahZmanit).toLong())
    }

    /**
     * Returns misheyakir (earliest time for tallit/tefillin) calculated as 66 zmaniyot minutes
     * before sunrise.
     *
     * This is based on the Pri Chadash and should only be used in cases of great need.
     */
    fun getMisheyakir66ZmaniyotMinutes(): Date? {
        if (!isUseAmudehHoraah) {
            return getZmanisBasedOffset(-1.1) // 66/60 = 1.1 hours
        }

        val percentage = getEquinoxPercentage(16.04, false) ?: return null
        val shaahZmanit = getTemporalHour(elevationAdjustedSunrise, elevationAdjustedSunset)
        if (shaahZmanit == Long.MIN_VALUE) return null
        val offset = -(percentage * shaahZmanit) * 11.0 / 12.0
        return getTimeOffset(elevationAdjustedSunrise, offset.toLong())
    }

    /**
     * Returns misheyakir l'chatchila calculated as 60 zmaniyot minutes before sunrise.
     */
    fun getMisheyakir60ZmaniyotMinutes(): Date? {
        if (!isUseAmudehHoraah) {
            return getZmanisBasedOffset(-1.0)
        }

        val percentage = getEquinoxPercentage(16.04, false) ?: return null
        val shaahZmanit = getTemporalHour(elevationAdjustedSunrise, elevationAdjustedSunset)
        if (shaahZmanit == Long.MIN_VALUE) return null
        val offset = -(percentage * shaahZmanit) * 5.0 / 6.0
        return getTimeOffset(elevationAdjustedSunrise, offset.toLong())
    }

    /**
     * Returns the latest time to recite Shema according to the MGA,
     * using 72 zmaniyot minutes for both alos and tzais.
     *
     * The day is calculated from alos 72 zmaniyot to tzais 72 zmaniyot,
     * and sof zman shema is 3 shaos zmaniyos into this day.
     */
    fun getSofZmanShmaMGA72MinutesZmanis(): Date? = getSofZmanShma(getAlotHashachar72Zmaniyot(), getTzais72Zmanis())

    /**
     * Returns the latest time for the morning prayer (Shacharis) according to the MGA,
     * using 72 zmaniyot minutes for both alos and tzais.
     */
    fun getSofZmanTfilaMGA72MinutesZmanis(): Date? = getSofZmanTfila(getAlotHashachar72Zmaniyot(), getTzais72Zmanis())

    /**
     * Returns the latest time for burning chametz according to the MGA.
     *
     * This is 5 shaos zmaniyos into the MGA day (from alos 72 to tzais 72).
     */
    fun getSofZmanBiurChametzMGA(): Date? {
        val alos = getAlotHashachar72Zmaniyot() ?: return null
        val tzais = getTzais72Zmanis() ?: return null
        val shaahZmanit = getTemporalHour(alos, tzais)
        return getTimeOffset(alos, shaahZmanit * 5)
    }

    /**
     * Returns chatzos (midday) using elevation-adjusted sunrise and sunset.
     *
     * While this has almost no practical effect on the time, the Ohr HaChaim
     * calendar uses elevation-adjusted times throughout.
     */
    fun getChatzotHayom(): Date? =
        getSunTransit(elevationAdjustedSunrise, elevationAdjustedSunset)
            ?: chatzos

    /**
     * Returns mincha gedola, the earliest time for the afternoon prayer.
     *
     * This is the later of: 30 minutes after chatzos, or 6.5 shaos zmaniyos after sunrise.
     */
    fun getMinchaGedolaGreaterThan30(): Date? {
        val chatzot = getChatzotHayom() ?: return null
        val minchaGedola30 = getTimeOffset(chatzot, 30 * MILLISECONDS_PER_MINUTE)
        val minchaGedola = minchaGedola

        return if (minchaGedola != null && minchaGedola30 != null) {
            if (minchaGedola30.after(minchaGedola)) minchaGedola30 else minchaGedola
        } else {
            minchaGedola30
        }
    }

    /**
     * Returns plag hamincha according to the Yalkut Yosef.
     *
     * This is calculated as 1.25 shaos zmaniyos before tzais (nightfall),
     * NOT before sunset as commonly calculated.
     *
     * This is how the Ohr HaChaim calendar calculates plag hamincha.
     */
    fun getPlagHaminchaYalkutYosef(): Date? {
        val tzais = getTzeit() ?: return null
        val shaahZmanit = getTemporalHour(elevationAdjustedSunrise, elevationAdjustedSunset)
        val dakahZmanit = shaahZmanit / MINUTES_PER_HOUR
        return getTimeOffset(tzais, -(shaahZmanit + (15 * dakahZmanit)))
    }

    /**
     * Returns tzais (nightfall) according to the Geonim - 13.5 zmaniyot minutes after sunset.
     *
     * This is the time used by the Ohr HaChaim calendar for the emergence of stars
     * and the start of the night for most purposes.
     */
    fun getTzeit(): Date? {
        if (!isUseAmudehHoraah) {
            return getZmanisBasedOffset(0.225) // 13.5/60 = 0.225 hours
        }

        val percentage = getEquinoxPercentage(3.7, true) ?: return null
        val shaahZmanit = getTemporalHour(elevationAdjustedSunrise, elevationAdjustedSunset)
        if (shaahZmanit == Long.MIN_VALUE) return null
        return getTimeOffset(elevationAdjustedSunset, (percentage * shaahZmanit).toLong())
    }

    /**
     * Returns a stricter time for tzais - 20 zmaniyot minutes after sunset.
     *
     * This is used l'chumra (stringently) for certain matters.
     */
    fun getTzeitLChumra(): Date? {
        if (!isUseAmudehHoraah) {
            val shaahZmanis = shaahZmanisGra
            if (shaahZmanis == Long.MIN_VALUE) return null
            return getTimeOffset(elevationAdjustedSunset, 20 * (shaahZmanis / MINUTES_PER_HOUR))
        }

        val percentage = getEquinoxPercentage(5.075, true) ?: return null
        val shaahZmanit = getTemporalHour(elevationAdjustedSunrise, elevationAdjustedSunset)
        if (shaahZmanit == Long.MIN_VALUE) return null
        return getTimeOffset(elevationAdjustedSunset, (percentage * shaahZmanit).toLong())
    }

    /**
     * Returns tzais according to Rabbeinu Tam - 72 zmaniyot minutes after sunset.
     *
     * This represents the opinion that night begins when one could walk 4 mil
     * (about 72 minutes) after sunset.
     */
    fun getTzais72Zmanis(): Date? {
        if (!isUseAmudehHoraah) {
            return getZmanisBasedOffset(1.2) // 72/60 = 1.2 hours
        }

        val percentage = getEquinoxPercentage(16.04, true) ?: return null
        val shaahZmanit = getTemporalHour(elevationAdjustedSunrise, elevationAdjustedSunset)
        if (shaahZmanit == Long.MIN_VALUE) return null
        return getTimeOffset(elevationAdjustedSunset, (percentage * shaahZmanit).toLong())
    }

    /**
     * Returns the time for the end of Shabbat according to the Ateret Torah.
     *
     * This is a fixed number of minutes after sunset (default 40 minutes outside Israel,
     * or can be set to 30 minutes for Israel).
     *
     * @param offsetMinutes Minutes after sunset (default 40 for outside Israel).
     */
    fun getTzaisAteretTorah(offsetMinutes: Double = 40.0): Date? =
        getTimeOffset(elevationAdjustedSunset, (offsetMinutes * MILLISECONDS_PER_MINUTE).toLong())

    /**
     * Returns the earlier of the two Rabbeinu Tam times.
     *
     * This compares 72 regular minutes vs 72 zmaniyot minutes and returns
     * the earlier of the two.
     */
    fun getTzais72ZmanisLkulah(): Date? {
        val tzais72 = tzais72
        val tzais72Zmanis = getTzais72Zmanis()

        return when {
            tzais72 != null && tzais72Zmanis != null -> {
                if (tzais72.before(tzais72Zmanis)) tzais72 else tzais72Zmanis
            }
            tzais72 != null -> tzais72
            else -> tzais72Zmanis
        }
    }

    /**
     * Returns the time for the end of Shabbat according to the Amudei Horaah calendar.
     */
    fun getTzeitShabbatAmudeiHoraah(): Date? {
        val tzait = getSunsetOffsetByDegrees(AstronomicalCalendar.GEOMETRIC_ZENITH + 7.165)
        if (tzait != null) {
            val tzait20 = getTimeOffset(elevationAdjustedSunset, 20 * MILLISECONDS_PER_MINUTE)
            if (tzait20 != null && tzait20.after(tzait)) {
                return tzait20
            }
            val chatzotLayla = getChatzotLayla()
            if (chatzotLayla != null && chatzotLayla.before(tzait)) {
                return chatzotLayla
            }
        }
        return tzait
    }

    /**
     * Returns halachic midnight (chatzos layla).
     *
     * This is calculated as the midpoint between today's chatzos and tomorrow's chatzos.
     */
    fun getChatzotLayla(): Date? {
        val chatzotToday = getChatzotHayom() ?: return null

        // Calculate tomorrow's chatzos
        val originalCalendar = calendar.clone() as Calendar
        calendar.add(Calendar.DATE, 1)
        val chatzotTomorrow = getChatzotHayom()
        calendar = originalCalendar

        if (chatzotTomorrow == null) return null

        val diff = chatzotTomorrow.time - chatzotToday.time
        return getTimeOffset(chatzotToday, diff / 2)
    }

    /**
     * Returns the sha'ah zmanis (temporal hour) for the MGA day.
     *
     * The MGA day is calculated from alos 72 zmaniyot to tzais 72 zmaniyot.
     */
    fun getShaahZmanis72MinutesZmanis(): Long = getTemporalHour(getAlotHashachar72Zmaniyot(), getTzais72Zmanis())

    /**
     * Returns candle lighting time using elevation-adjusted sunset.
     *
     * @param offsetMinutes Minutes before sunset for candle lighting (typically 18-40).
     */
    fun getCandleLightingWithElevation(offsetMinutes: Double = 20.0): Date? =
        getTimeOffset(elevationAdjustedSunset, -(offsetMinutes * MILLISECONDS_PER_MINUTE).toLong())
}
