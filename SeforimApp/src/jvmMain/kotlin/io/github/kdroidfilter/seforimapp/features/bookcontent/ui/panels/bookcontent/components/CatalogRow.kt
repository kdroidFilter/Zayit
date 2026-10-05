package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.catalog.CatalogPresets
import io.github.kdroidfilter.seforimapp.core.presentation.components.CatalogDropdown
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CatalogRow(
    onEvent: (BookContentEvent) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
) {
    val outerPadding = 12.dp
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                // Its own clipped layer: a button's hover then repaints this row only. Unclipped, its damage
                // fell back to the home page's whole viewport, a full-window repaint per hover change.
                // The padding stays inside the clip, so the buttons' focus outlines are not cut.
                .graphicsLayer { clip = true }
                .padding(outerPadding),
        contentAlignment = Alignment.TopStart,
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            val buttonModifier = Modifier.widthIn(max = 130.dp)

            CatalogDropdown(
                spec = CatalogPresets.Dropdowns.TANAKH,
                onEvent = onEvent,
                modifier = buttonModifier,
                popupWidthMultiplier = 1.50f,
            )
            CatalogDropdown(
                spec = CatalogPresets.Dropdowns.MISHNA,
                onEvent = onEvent,
                modifier = buttonModifier,
            )
            CatalogDropdown(
                spec = CatalogPresets.Dropdowns.BAVLI,
                onEvent = onEvent,
                modifier = buttonModifier,
                popupWidthMultiplier = 1.1f,
            )
            CatalogDropdown(
                spec = CatalogPresets.Dropdowns.YERUSHALMI,
                onEvent = onEvent,
                modifier = buttonModifier,
                popupWidthMultiplier = 1.1f,
            )
            CatalogDropdown(
                spec = CatalogPresets.Dropdowns.MISHNE_TORAH,
                onEvent = onEvent,
                modifier = buttonModifier,
                popupWidthMultiplier = 1.5f,
            )
            CatalogDropdown(
                spec = CatalogPresets.Dropdowns.TUR_QUICK_LINKS,
                onEvent = onEvent,
                modifier = buttonModifier,
                maxPopupHeight = 130.dp,
            )
            CatalogDropdown(
                spec = CatalogPresets.Dropdowns.SHULCHAN_ARUCH,
                onEvent = onEvent,
                modifier = buttonModifier,
                maxPopupHeight = 130.dp,
            )
        }
    }
}
