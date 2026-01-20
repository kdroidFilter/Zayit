package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.catalog.PrecomputedCatalog
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/** Quick TOC jump menu for a specific book. */
data class TocQuickLink(
    val label: String,
    val tocId: Long,
    val firstLineId: Long?,
)

@Composable
fun TocJumpDropdown(
    title: String,
    bookId: Long,
    items: List<TocQuickLink> = emptyList(),
    onEvent: (BookContentEvent) -> Unit,
    modifier: Modifier = Modifier,
    popupWidthMultiplier: Float = 1.5f,
    minPopupHeight: Dp = Dp.Unspecified,
    maxPopupHeight: Dp = 360.dp,
    prepareItems: (suspend () -> List<TocQuickLink>)? = null,
) {
    val repo = LocalAppGraph.current.repository
    val scope = rememberCoroutineScope()

    DropdownButton(
        modifier = modifier,
        popupWidthMultiplier = popupWidthMultiplier,
        minPopupHeight = minPopupHeight,
        maxPopupHeight = maxPopupHeight,
        content = { Text(title) },
        popupContent = { close ->
            // Lazy: prepare items only when popup opens if not provided
            var links by androidx.compose.runtime.remember { mutableStateOf<List<TocQuickLink>?>(if (items.isNotEmpty()) items else null) }
            val loader = prepareItems
            if (links == null && loader != null) {
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    links = runCatching { loader() }.getOrNull().orEmpty()
                }
            }
            val render = links.orEmpty()
            render.forEach { quick ->
                val hoverSource = remember { MutableInteractionSource() }
                val isHovered by hoverSource.collectIsHoveredAsState()
                val backgroundColor by animateColorAsState(
                    targetValue =
                        if (isHovered) {
                            JewelTheme.globalColors.outlines.focused
                                .copy(alpha = 0.12f)
                        } else {
                            Color.Transparent
                        },
                    animationSpec = tween(durationMillis = 150),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(backgroundColor)
                        .clickable(
                            indication = null,
                            interactionSource = hoverSource,
                        ) {
                            close()
                            val lineId = quick.firstLineId
                            if (lineId != null) {
                                onEvent(BookContentEvent.OpenBookAtLine(bookId = bookId, lineId = lineId))
                            } else {
                                onEvent(BookContentEvent.OpenBookById(bookId))
                            }
                        }.padding(horizontal = 12.dp, vertical = 8.dp)
                        .pointerHoverIcon(PointerIcon.Hand),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = quick.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 13.sp,
                    )
                }
            }
        },
    )
}

@Composable
fun TocJumpDropdownByIds(
    title: String,
    bookId: Long,
    tocTextIds: List<Long>,
    onEvent: (BookContentEvent) -> Unit,
    modifier: Modifier = Modifier,
    popupWidthMultiplier: Float = 1.5f,
    minPopupHeight: Dp = Dp.Unspecified,
    maxPopupHeight: Dp = 360.dp,
) {
    val loader: suspend () -> List<TocQuickLink> = {
        val bookMap = PrecomputedCatalog.TOC_BY_TOC_TEXT_ID[bookId].orEmpty()
        tocTextIds.mapNotNull { tx ->
            val ql = bookMap[tx] ?: return@mapNotNull null
            TocQuickLink(ql.label, ql.tocEntryId, ql.firstLineId)
        }
    }

    TocJumpDropdown(
        title = title,
        bookId = bookId,
        items = emptyList(),
        onEvent = onEvent,
        modifier = modifier,
        popupWidthMultiplier = popupWidthMultiplier,
        minPopupHeight = minPopupHeight,
        maxPopupHeight = maxPopupHeight,
        prepareItems = loader,
    )
}

@Composable
fun TocJumpDropdownByIds(
    bookId: Long,
    tocTextIds: List<Long>,
    onEvent: (BookContentEvent) -> Unit,
    modifier: Modifier = Modifier,
    popupWidthMultiplier: Float = 1.5f,
    minPopupHeight: Dp = Dp.Unspecified,
    maxPopupHeight: Dp = 360.dp,
) {
    val t = PrecomputedCatalog.BOOK_TITLES[bookId]
    if (t != null) {
        TocJumpDropdownByIds(
            title = t,
            bookId = bookId,
            tocTextIds = tocTextIds,
            onEvent = onEvent,
            modifier = modifier,
            popupWidthMultiplier = popupWidthMultiplier,
            minPopupHeight = minPopupHeight,
            maxPopupHeight = maxPopupHeight,
        )
    }
}
