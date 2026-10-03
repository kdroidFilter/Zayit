package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.texteffects.TextZoom
import io.github.kdroidfilter.seforimapp.texteffects.rememberTextZoom

/** The reading text's pointer zoom, on the app's text size setting; animated in the selected tab only. */
@Composable
fun rememberAppTextZoom(onZoomingChange: (Boolean) -> Unit = {}): TextZoom {
    val appSettings = LocalAppGraph.current.appSettings
    val textSize by appSettings.textSizeFlow.collectAsState()
    return rememberTextZoom(
        textSize = textSize,
        minTextSize = AppSettings.MIN_TEXT_SIZE,
        maxTextSize = AppSettings.MAX_TEXT_SIZE,
        onTextSize = appSettings::setTextSize,
        animate = LocalTabSelected.current,
        onZoomingChange = onZoomingChange,
    )
}
