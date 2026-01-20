package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.kdroidfilter.seforimapp.catalog.PrecomputedCatalog
import io.github.kdroidfilter.seforimapp.core.presentation.components.CatalogDropdown
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent

@Composable
fun CatalogRow(
    onEvent: (BookContentEvent) -> Unit,
    modifier: Modifier = Modifier,
    buttonWidth: Dp = 130.dp,
    spacing: Dp = 8.dp,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .zIndex(1f)
                .padding(6.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val totalButtons = 7
            // Compute how many catalog buttons we can show fully without overflow.
            val maxVisible =
                run {
                    var count = totalButtons
                    while (count > 1) {
                        val required = buttonWidth * count + spacing * (count - 1)
                        if (required <= maxWidth) break
                        count--
                    }
                    count
                }

            var rendered = 0

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
            ) {
                if (rendered < maxVisible) {
                    CatalogDropdown(
                        spec = PrecomputedCatalog.Dropdowns.TANAKH,
                        onEvent = onEvent,
                        modifier = Modifier.widthIn(max = buttonWidth),
                        popupWidthMultiplier = 1.50f,
                    )
                    rendered++
                }
                if (rendered < maxVisible) {
                    CatalogDropdown(
                        spec = PrecomputedCatalog.Dropdowns.MISHNA,
                        onEvent = onEvent,
                        modifier = Modifier.widthIn(max = buttonWidth),
                    )
                    rendered++
                }
                if (rendered < maxVisible) {
                    CatalogDropdown(
                        spec = PrecomputedCatalog.Dropdowns.BAVLI,
                        onEvent = onEvent,
                        modifier = Modifier.widthIn(max = buttonWidth),
                        popupWidthMultiplier = 1.1f,
                    )
                    rendered++
                }
                if (rendered < maxVisible) {
                    CatalogDropdown(
                        spec = PrecomputedCatalog.Dropdowns.YERUSHALMI,
                        onEvent = onEvent,
                        modifier = Modifier.widthIn(max = buttonWidth),
                        popupWidthMultiplier = 1.1f,
                    )
                    rendered++
                }
                if (rendered < maxVisible) {
                    CatalogDropdown(
                        spec = PrecomputedCatalog.Dropdowns.MISHNE_TORAH,
                        onEvent = onEvent,
                        modifier = Modifier.widthIn(max = buttonWidth),
                        popupWidthMultiplier = 1.5f,
                    )
                    rendered++
                }
                if (rendered < maxVisible) {
                    CatalogDropdown(
                        spec = PrecomputedCatalog.Dropdowns.TUR_QUICK_LINKS,
                        onEvent = onEvent,
                        modifier = Modifier.widthIn(max = buttonWidth),
                        maxPopupHeight = 130.dp,
                    )
                    rendered++
                }
                if (rendered < maxVisible) {
                    CatalogDropdown(
                        spec = PrecomputedCatalog.Dropdowns.SHULCHAN_ARUCH,
                        onEvent = onEvent,
                        modifier = Modifier.widthIn(max = buttonWidth),
                        maxPopupHeight = 160.dp,
                        popupWidthMultiplier = 1.1f,
                    )
                }
            }
        }
    }
}
