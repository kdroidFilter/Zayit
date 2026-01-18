package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.catalog.CategoryDropdownSpec
import io.github.kdroidfilter.seforimapp.catalog.DropdownSpec
import io.github.kdroidfilter.seforimapp.catalog.MultiCategoryDropdownSpec
import io.github.kdroidfilter.seforimapp.catalog.PrecomputedCatalog
import io.github.kdroidfilter.seforimapp.catalog.TocQuickLinksSpec
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Book as BookModel
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.Orientation

@Composable
fun CatalogDropdown(
    spec: DropdownSpec,
    onEvent: (BookContentEvent) -> Unit,
    modifier: Modifier = Modifier,
    popupWidthMultiplier: Float? = null,
    minPopupHeight: Dp? = null,
    maxPopupHeight: Dp? = null,
) {
    val repo = LocalAppGraph.current.repository
    val scope = rememberCoroutineScope()

    when (spec) {
        is CategoryDropdownSpec -> {
            val categoryId = spec.categoryId
            val categoryTitle = remember(categoryId) { PrecomputedCatalog.CATEGORY_TITLES[categoryId] }
            val precomputedBooks = remember(categoryId) { PrecomputedCatalog.CATEGORY_BOOKS[categoryId] }

            if (categoryTitle != null && !precomputedBooks.isNullOrEmpty()) {
                val baseMax: Dp = 360.dp
                val minHeight: Dp = minPopupHeight ?: when (categoryId) {
                    PrecomputedCatalog.Ids.Categories.TORAH -> 160.dp
                    PrecomputedCatalog.Ids.Categories.SHULCHAN_ARUCH -> 120.dp
                    else -> Dp.Unspecified
                }
                val desiredMax: Dp = maxPopupHeight ?: baseMax
                val effectiveMax: Dp = if (minHeight != Dp.Unspecified && minHeight > desiredMax) minHeight else desiredMax
                DropdownButton(
                    modifier = modifier.widthIn(max = 280.dp),
                    popupWidthMultiplier = popupWidthMultiplier ?: 1.5f,
                    maxPopupHeight = effectiveMax,
                    minPopupHeight = minHeight,
                    content = { Text(text = categoryTitle) },
                    popupContent = { close ->
                        precomputedBooks.forEach { bookRef ->
                            val hoverSource = remember { MutableInteractionSource() }
                            val isHovered by hoverSource.collectIsHoveredAsState()
                            val backgroundColor by animateColorAsState(
                                targetValue = if (isHovered) {
                                    JewelTheme.globalColors.outlines.focused.copy(alpha = 0.12f)
                                } else {
                                    Color.Transparent
                                },
                                animationSpec = tween(durationMillis = 150)
                            )
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(backgroundColor)
                                    .clickable(
                                        indication = null,
                                        interactionSource = hoverSource
                                    ) {
                                        close()
                                        scope.launch {
                                            val b: BookModel? = runCatching { repo.getBookCore(bookRef.id) }.getOrNull()
                                            if (b != null) onEvent(BookContentEvent.BookSelected(b))
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .pointerHoverIcon(PointerIcon.Hand),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = bookRef.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                )
            }
        }
        is MultiCategoryDropdownSpec -> {
            val labelTitle = remember(spec.labelCategoryId) { PrecomputedCatalog.CATEGORY_TITLES[spec.labelCategoryId] }
            val sections = remember(spec.bookCategoryIds) {
                spec.bookCategoryIds.mapNotNull { cid ->
                    val t = PrecomputedCatalog.CATEGORY_TITLES[cid]
                    val list = PrecomputedCatalog.CATEGORY_BOOKS[cid]
                    if (t != null && !list.isNullOrEmpty()) t to list else null
                }
            }
            if (labelTitle != null && sections.any { it.second.isNotEmpty() }) {
                val popupWidth = popupWidthMultiplier ?: when (spec.labelCategoryId) {
                    PrecomputedCatalog.Ids.Categories.BAVLI,
                    PrecomputedCatalog.Ids.Categories.YERUSHALMI -> 1.1f
                    else -> 1.5f
                }
                val baseMax: Dp = 360.dp
                val minHeight: Dp = minPopupHeight ?: Dp.Unspecified
                val desiredMax: Dp = maxPopupHeight ?: baseMax
                val effectiveMax: Dp = if (minHeight != Dp.Unspecified && minHeight > desiredMax) minHeight else desiredMax
                DropdownButton(
                    modifier = modifier.widthIn(max = 280.dp),
                    popupWidthMultiplier = popupWidth,
                    maxPopupHeight = effectiveMax,
                    minPopupHeight = minHeight,
                    content = { Text(text = labelTitle) },
                    popupContent = { close ->
                        sections.forEachIndexed { index, (catTitle, books) ->
                            if (books.isEmpty()) return@forEachIndexed
                            if (index > 0) {
                                Divider(orientation = Orientation.Horizontal)
                            }
                            Text(
                                text = catTitle,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = JewelTheme.globalColors.text.disabled
                            )
                            books.forEach { bookRef ->
                                val hoverSource = remember { MutableInteractionSource() }
                                val isHovered by hoverSource.collectIsHoveredAsState()
                                val backgroundColor by animateColorAsState(
                                    targetValue = if (isHovered) {
                                        JewelTheme.globalColors.outlines.focused.copy(alpha = 0.12f)
                                    } else {
                                        Color.Transparent
                                    },
                                    animationSpec = tween(durationMillis = 150)
                                )
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(backgroundColor)
                                        .clickable(
                                            indication = null,
                                            interactionSource = hoverSource
                                        ) {
                                            close()
                                            scope.launch {
                                                val b: BookModel? = runCatching { repo.getBookCore(bookRef.id) }.getOrNull()
                                                if (b != null) onEvent(BookContentEvent.BookSelected(b))
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                        .pointerHoverIcon(PointerIcon.Hand),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = bookRef.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
        is TocQuickLinksSpec -> {
            TocJumpDropdownByIds(
                bookId = spec.bookId,
                tocTextIds = spec.tocTextIds,
                onEvent = onEvent,
                modifier = modifier,
                popupWidthMultiplier = popupWidthMultiplier ?: 1.5f,
                minPopupHeight = minPopupHeight ?: Dp.Unspecified,
                maxPopupHeight = run {
                    val baseMax = 360.dp
                    val desiredMax = maxPopupHeight ?: baseMax
                    val minH = minPopupHeight ?: Dp.Unspecified
                    if (minH != Dp.Unspecified && minH > desiredMax) minH else desiredMax
                },
            )
        }
    }
}
