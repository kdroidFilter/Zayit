package io.github.kdroidfilter.seforimapp.earthwidget

import androidx.compose.runtime.Immutable

/**
 * Opinion for the earliest time to recite Kiddush Levana (Tchilas Zman).
 */
enum class KiddushLevanaEarliestOpinion {
    /** 3 days after the molad - strict opinion */
    DAYS_3,

    /** 7 days after the molad - Mechaber (Shulchan Aruch) opinion */
    DAYS_7,
}

/**
 * Opinion for the latest time to recite Kiddush Levana (Sof Zman).
 */
enum class KiddushLevanaLatestOpinion {
    /** Approximately 14 days, 18 hours, 22 minutes after molad - Maharil opinion */
    BETWEEN_MOLDOS,

    /** 15 days after the molad - Shulchan Aruch opinion */
    DAYS_15,
}

/**
 * Data representing the Kiddush Levana period for display on the moon orbit.
 *
 * @property startDegrees Position on the orbit where Kiddush Levana period begins (0-360)
 * @property endDegrees Position on the orbit where Kiddush Levana period ends (0-360)
 * @property isCurrentlyAllowed Whether Kiddush Levana can be recited at the current time
 * @property startTimeMillis Start time of the period in milliseconds since epoch
 * @property endTimeMillis End time of the period in milliseconds since epoch
 */
@Immutable
data class KiddushLevanaData(
    val startDegrees: Float,
    val endDegrees: Float,
    val isCurrentlyAllowed: Boolean,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
) {
    companion object {
        val EMPTY =
            KiddushLevanaData(
                startDegrees = 0f,
                endDegrees = 0f,
                isCurrentlyAllowed = false,
                startTimeMillis = 0L,
                endTimeMillis = 0L,
            )
    }
}
