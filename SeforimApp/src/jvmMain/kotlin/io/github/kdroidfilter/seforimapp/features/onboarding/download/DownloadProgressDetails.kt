package io.github.kdroidfilter.seforimapp.features.onboarding.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.core.presentation.utils.formatBytes
import io.github.kdroidfilter.seforimapp.core.presentation.utils.formatBytesPerSec
import io.github.kdroidfilter.seforimapp.core.presentation.utils.formatEta
import io.github.kdroidfilter.seforimapp.icons.FileArrowDown
import io.github.kdroidfilter.seforimapp.icons.Speed
import io.github.kdroidfilter.seforimapp.icons.Timer
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.onboarding_download_progress

/**
 * Download metrics rows (downloaded/total, speed, ETA) shared by the onboarding download
 * and database-update download screens. Renders nothing until [totalBytes] is known.
 */
@Composable
fun ColumnScope.DownloadProgressDetails(
    downloadedBytes: Long,
    totalBytes: Long?,
    speedBytesPerSec: Long,
) {
    val totalText = totalBytes?.let { formatBytes(it) } ?: return
    val downloadedText = formatBytes(downloadedBytes)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            FileArrowDown,
            contentDescription = null,
            tint = JewelTheme.globalColors.text.normal,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(Res.string.onboarding_download_progress, downloadedText, totalText),
            modifier = Modifier.width(175.dp),
            textAlign = TextAlign.End,
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(Speed, contentDescription = null, tint = JewelTheme.globalColors.text.normal, modifier = Modifier.size(16.dp))
        Text(
            text = formatBytesPerSec(speedBytesPerSec),
            modifier = Modifier.width(175.dp),
            textAlign = TextAlign.End,
        )
    }

    val etaSeconds =
        if (speedBytesPerSec > 0L) {
            val remaining = (totalBytes - downloadedBytes).coerceAtLeast(0)
            ((remaining + speedBytesPerSec - 1) / speedBytesPerSec)
        } else {
            null
        }
    etaSeconds?.let { secs ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(Timer, contentDescription = null, tint = JewelTheme.globalColors.text.normal, modifier = Modifier.size(15.dp))
            Text(
                text = formatEta(secs),
                modifier = Modifier.width(175.dp),
                textAlign = TextAlign.End,
            )
        }
    }
}
