package io.github.kdroidfilter.seforimapp.earthwidget

import kotlin.math.PI

// As java.lang.Math's, which has no common counterpart
internal fun toRadians(degrees: Double): Double = degrees * (PI / 180.0)

internal fun toDegrees(radians: Double): Double = radians * (180.0 / PI)
