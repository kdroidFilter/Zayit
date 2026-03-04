package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CatalogRow(
    onEvent: (BookContentEvent) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
) {
    val outerPadding = if (ThemeUtils.isIslandsStyle()) 12.dp else 6.dp
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .zIndex(1f)
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
                spec = PrecomputedCatalog.Dropdowns.TANAKH,
                onEvent = onEvent,
                modifier = buttonModifier,
                popupWidthMultiplier = 1.50f,
            )
            CatalogDropdown(
                spec = PrecomputedCatalog.Dropdowns.MISHNA,
                onEvent = onEvent,
                modifier = buttonModifier,
            )
            CatalogDropdown(
                spec = PrecomputedCatalog.Dropdowns.BAVLI,
                onEvent = onEvent,
                modifier = buttonModifier,
                popupWidthMultiplier = 1.1f,
            )
            CatalogDropdown(
                spec = PrecomputedCatalog.Dropdowns.YERUSHALMI,
                onEvent = onEvent,
                modifier = buttonModifier,
                popupWidthMultiplier = 1.1f,
            )
            CatalogDropdown(
                spec = PrecomputedCatalog.Dropdowns.MISHNE_TORAH,
                onEvent = onEvent,
                modifier = buttonModifier,
                popupWidthMultiplier = 1.5f,
            )
            CatalogDropdown(
                spec = PrecomputedCatalog.Dropdowns.TUR_QUICK_LINKS,
                onEvent = onEvent,
                modifier = buttonModifier,
                maxPopupHeight = 130.dp,
            )
            CatalogDropdown(
                spec = PrecomputedCatalog.Dropdowns.SHULCHAN_ARUCH,
                onEvent = onEvent,
                modifier = buttonModifier,
                maxPopupHeight = 160.dp,
                popupWidthMultiplier = 1.1f,
            )
        }
    }
}
