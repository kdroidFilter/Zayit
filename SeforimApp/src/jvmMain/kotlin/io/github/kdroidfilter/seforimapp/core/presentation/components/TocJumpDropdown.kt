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
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import io.github.kdroidfilter.seforimapp.catalog.TocQuickLink as PresetTocQuickLink

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
    onEvent: (BookContentEvent) -> Unit,
    modifier: Modifier = Modifier,
    items: ImmutableList<TocQuickLink> = persistentListOf(),
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
                    links = runSuspendCatching { loader() }.getOrNull().orEmpty()
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
fun TocJumpDropdownForBook(
    bookId: Long,
    links: ImmutableList<PresetTocQuickLink>,
    onEvent: (BookContentEvent) -> Unit,
    modifier: Modifier = Modifier,
    popupWidthMultiplier: Float = 1.5f,
    minPopupHeight: Dp = Dp.Unspecified,
    maxPopupHeight: Dp = 360.dp,
) {
    val catalogAccess = LocalAppGraph.current.catalogAccess
    val title = catalogAccess.bookTitle(bookId) ?: return
    val items = links.map { TocQuickLink(it.label, it.tocEntryId, it.firstLineId) }.toImmutableList()

    TocJumpDropdown(
        title = title,
        bookId = bookId,
        onEvent = onEvent,
        modifier = modifier,
        items = items,
        popupWidthMultiplier = popupWidthMultiplier,
        minPopupHeight = minPopupHeight,
        maxPopupHeight = maxPopupHeight,
    )
}
