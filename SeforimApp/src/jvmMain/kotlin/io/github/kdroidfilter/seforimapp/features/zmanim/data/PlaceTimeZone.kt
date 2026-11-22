package io.github.kdroidfilter.seforimapp.features.zmanim.data

import java.util.TimeZone

/** The city's civil time zone, daylight saving time included. */
val Place.timeZone: TimeZone get() = TimeZone.getTimeZone(zoneId)
