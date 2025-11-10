package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.presentation.theme.AppColors
import io.github.kdroidfilter.seforimapp.core.presentation.cache.BookTitleCache
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/** Quick TOC jump menu for a specific book. */
data class TocQuickLink(val label: String, val tocId: Long, val firstLineId: Long?)

@Composable
fun TocJumpDropdown(
    title: String,
    bookId: Long,
    items: List<TocQuickLink> = emptyList(),
    onEvent: (BookContentEvent) -> Unit,
    modifier: Modifier = Modifier,
    popupWidthMultiplier: Float = 1.5f,
    maxPopupHeight: Dp = 360.dp,
    prepareItems: (suspend () -> List<TocQuickLink>)? = null,
) {
    val repo = LocalAppGraph.current.repository
    val scope = rememberCoroutineScope()

    DropdownButton(
        modifier = modifier,
        popupWidthMultiplier = popupWidthMultiplier,
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isHovered) AppColors.HOVER_HIGHLIGHT else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable(
                            indication = null,
                            interactionSource = hoverSource
                        ) {
                            scope.launch {
                                val lineId = quick.firstLineId ?: repo.getLineIdsForTocEntry(quick.tocId).firstOrNull() ?: return@launch
                                close()
                                onEvent(BookContentEvent.OpenBookAtLine(bookId = bookId, lineId = lineId))
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .pointerHoverIcon(PointerIcon.Hand),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = quick.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 13.sp
                    )
                }
            }
        }
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
    maxPopupHeight: Dp = 360.dp,
) {
    val repo = LocalAppGraph.current.repository
    val loader: suspend () -> List<TocQuickLink> = {
        val bookToc = runCatching { repo.getBookToc(bookId) }.getOrNull().orEmpty()
        val byTextId = bookToc.associateBy { it.textId }
        val mappings = runCatching { repo.getLineTocMappingsForBook(bookId) }.getOrNull().orEmpty()
        val firstLineByToc = HashMap<Long, Long>()
        for (m in mappings) if (!firstLineByToc.containsKey(m.tocEntryId)) firstLineByToc[m.tocEntryId] = m.lineId
        tocTextIds.mapNotNull { textId ->
            val e = byTextId[textId]
            e?.let { if (it.text.isNotBlank()) TocQuickLink(it.text, it.id, firstLineByToc[it.id]) else null }
        }
    }

    TocJumpDropdown(
        title = title,
        bookId = bookId,
        items = emptyList(),
        onEvent = onEvent,
        modifier = modifier,
        popupWidthMultiplier = popupWidthMultiplier,
        maxPopupHeight = maxPopupHeight,
        prepareItems = loader
    )
}

@Composable
fun TocJumpDropdownByIds(
    bookId: Long,
    tocTextIds: List<Long>,
    onEvent: (BookContentEvent) -> Unit,
    modifier: Modifier = Modifier,
    popupWidthMultiplier: Float = 1.5f,
    maxPopupHeight: Dp = 360.dp,
) {
    val repo = LocalAppGraph.current.repository
    var title by androidx.compose.runtime.remember { mutableStateOf<String?>(BookTitleCache.get(bookId)) }
    androidx.compose.runtime.LaunchedEffect(bookId) {
        if (title == null) {
            val book = runCatching { repo.getBook(bookId) }.getOrNull()
            val resolved = book?.title?.takeIf { it.isNotBlank() }
            if (resolved != null) {
                BookTitleCache.put(bookId, resolved)
                title = resolved
            }
        }
    }
    val t = title
    if (t != null) {
        TocJumpDropdownByIds(
            title = t,
            bookId = bookId,
            tocTextIds = tocTextIds,
            onEvent = onEvent,
            modifier = modifier,
            popupWidthMultiplier = popupWidthMultiplier,
            maxPopupHeight = maxPopupHeight
        )
    }
}
