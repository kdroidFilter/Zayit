package io.github.kdroidfilter.seforimapp.core.presentation.utils

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.bytes_per_second_pattern
import seforimapp.seforimapp.generated.resources.bytes_unit_b
import seforimapp.seforimapp.generated.resources.bytes_unit_gb
import seforimapp.seforimapp.generated.resources.bytes_unit_kb
import seforimapp.seforimapp.generated.resources.bytes_unit_mb
import seforimapp.seforimapp.generated.resources.bytes_unit_tb
import seforimapp.seforimapp.generated.resources.eta_hours_unit_plural
import seforimapp.seforimapp.generated.resources.eta_hours_unit_singular
import seforimapp.seforimapp.generated.resources.eta_minutes_unit_plural
import seforimapp.seforimapp.generated.resources.eta_minutes_unit_singular
import seforimapp.seforimapp.generated.resources.eta_seconds_unit_plural
import seforimapp.seforimapp.generated.resources.eta_seconds_unit_singular

@Composable
fun formatBytes(bytes: Long): String {
    val units =
        listOf(
            stringResource(Res.string.bytes_unit_b),
            stringResource(Res.string.bytes_unit_kb),
            stringResource(Res.string.bytes_unit_mb),
            stringResource(Res.string.bytes_unit_gb),
            stringResource(Res.string.bytes_unit_tb),
        )
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return String.format(java.util.Locale.US, "%.2f %s", value, units[unitIndex])
}

@Composable
fun formatBytesPerSec(bps: Long): String {
    val bytesText = formatBytes(bps)
    return stringResource(Res.string.bytes_per_second_pattern, bytesText)
}

@Composable
fun formatEta(totalSeconds: Long): String {
    val secs = totalSeconds.coerceAtLeast(0)
    val hours = secs / 3600
    val minutes = (secs % 3600) / 60
    val seconds = secs % 60

    val parts = mutableListOf<String>()

    if (hours > 0) {
        val unit =
            if (hours == 1L) {
                stringResource(Res.string.eta_hours_unit_singular)
            } else {
                stringResource(Res.string.eta_hours_unit_plural)
            }
        parts += String.format(java.util.Locale.US, "%d %s", hours, unit)
    }

    if (minutes > 0) {
        val unit =
            if (minutes == 1L) {
                stringResource(Res.string.eta_minutes_unit_singular)
            } else {
                stringResource(Res.string.eta_minutes_unit_plural)
            }
        parts += String.format(java.util.Locale.US, "%d %s", minutes, unit)
    }

    if (seconds > 0 || parts.isEmpty()) {
        val unit =
            if (seconds == 1L) {
                stringResource(Res.string.eta_seconds_unit_singular)
            } else {
                stringResource(Res.string.eta_seconds_unit_plural)
            }
        parts += String.format(java.util.Locale.US, "%d %s", seconds, unit)
    }

    return parts.joinToString(separator = " ")
}
