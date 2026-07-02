@file:OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)

package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.kdroidfilter.seforimapp.core.deeplink.bookShareLink
import io.github.kdroidfilter.seforimapp.core.favorites.FavoriteFolder
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.Link
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.breadcrumb_copy_link
import seforimapp.seforimapp.generated.resources.favorites_add
import seforimapp.seforimapp.generated.resources.favorites_edit
import seforimapp.seforimapp.generated.resources.favorites_folder_name_placeholder
import seforimapp.seforimapp.generated.resources.favorites_new_folder
import seforimapp.seforimapp.generated.resources.favorites_no_folder
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Action buttons at the end of the breadcrumb bar (Chrome omnibox-like): copy a deep link to
 * the current position, and star/unstar it as a favorite with a folder-picking popup.
 */
@Composable
fun BreadcrumbActionsView(uiState: BookContentState) {
    val book = uiState.navigation.selectedBook ?: return
    val toc = uiState.toc.breadcrumbPath.lastOrNull()
    val lineId = uiState.content.primaryLine?.id ?: toc?.lineId

    val aboveAnchorPlacement =
        remember {
            object : TooltipPlacement {
                @Composable
                override fun positionProvider(cursorPosition: Offset): PopupPositionProvider =
                    object : PopupPositionProvider {
                        override fun calculatePosition(
                            anchorBounds: IntRect,
                            windowSize: IntSize,
                            layoutDirection: LayoutDirection,
                            popupContentSize: IntSize,
                        ): IntOffset =
                            IntOffset(
                                x =
                                    (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
                                        .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
                                y = anchorBounds.top - popupContentSize.height,
                            )
                    }
            }
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CopyLinkButton(
            bookId = book.id,
            lineId = lineId,
            tooltipPlacement = aboveAnchorPlacement,
        )
        FavoriteStarButton(
            bookId = book.id,
            tocEntryId = toc?.id,
            lineId = lineId,
            title =
                toc
                    ?.text
                    ?.takeIf { it.isNotBlank() && it != book.title }
                    ?.let { "${book.title} - $it" } ?: book.title,
            tooltipPlacement = aboveAnchorPlacement,
        )
    }
}

@Composable
private fun CopyLinkButton(
    bookId: Long,
    lineId: Long?,
    tooltipPlacement: TooltipPlacement,
) {
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_FEEDBACK_MS)
            copied = false
        }
    }
    Tooltip(
        tooltip = { Text(stringResource(Res.string.breadcrumb_copy_link), fontSize = 13.sp) },
        tooltipPlacement = tooltipPlacement,
    ) {
        IconButton(
            onClick = {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(bookShareLink(bookId, lineId)), null)
                copied = true
            },
            modifier = Modifier.size(24.dp),
        ) {
            if (copied) {
                Icon(
                    key = AllIconsKeys.Actions.Checked,
                    contentDescription = stringResource(Res.string.breadcrumb_copy_link),
                    modifier = Modifier.size(16.dp),
                )
            } else {
                // Same chain-link vector as the tab context menu's copy-link item
                Image(
                    painter = rememberVectorPainter(Link),
                    contentDescription = stringResource(Res.string.breadcrumb_copy_link),
                    modifier = Modifier.size(16.dp),
                    colorFilter = ColorFilter.tint(JewelTheme.globalColors.text.normal),
                )
            }
        }
    }
}

@Composable
private fun FavoriteStarButton(
    bookId: Long,
    tocEntryId: Long?,
    lineId: Long?,
    title: String,
    tooltipPlacement: TooltipPlacement,
) {
    val favoritesStore = LocalAppGraph.current.favoritesStore
    val scope = rememberCoroutineScope()
    val revision by favoritesStore.revision.collectAsState()
    val key = favoritesStore.keyFor(bookId, tocEntryId)
    var isFavorite by remember { mutableStateOf(false) }
    LaunchedEffect(key, revision) { isFavorite = favoritesStore.isFavorite(key) }
    var popupVisible by remember { mutableStateOf(false) }

    Box(
        modifier =
            Modifier.onPointerEvent(PointerEventType.Press) { event ->
                if (event.buttons.isSecondaryPressed) {
                    scope.launch {
                        if (!isFavorite) {
                            favoritesStore.add(bookId, title, System.currentTimeMillis(), tocEntryId, lineId)
                        }
                        popupVisible = true
                    }
                }
            },
    ) {
        val starLabel = stringResource(if (isFavorite) Res.string.favorites_edit else Res.string.favorites_add)
        Tooltip(
            tooltip = { Text(starLabel, fontSize = 13.sp) },
            tooltipPlacement = tooltipPlacement,
        ) {
            IconButton(
                onClick = {
                    scope.launch {
                        if (isFavorite) {
                            favoritesStore.remove(key)
                        } else {
                            favoritesStore.add(bookId, title, System.currentTimeMillis(), tocEntryId, lineId)
                        }
                    }
                },
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    key = if (isFavorite) AllIconsKeys.Nodes.Favorite else AllIconsKeys.Nodes.NotFavoriteOnHover,
                    contentDescription = starLabel,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (popupVisible) {
            Popup(
                popupPositionProvider = AboveAnchorPositionProvider,
                onDismissRequest = { popupVisible = false },
                properties = PopupProperties(focusable = true),
            ) {
                FavoritePopupContent(
                    favoriteKey = key,
                    title = title,
                    onDismiss = { popupVisible = false },
                )
            }
        }
    }
}

@Composable
private fun FavoritePopupContent(
    favoriteKey: String,
    title: String,
    onDismiss: () -> Unit,
) {
    val favoritesStore = LocalAppGraph.current.favoritesStore
    val scope = rememberCoroutineScope()
    val revision by favoritesStore.revision.collectAsState()
    var folders by remember { mutableStateOf<List<FavoriteFolder>>(emptyList()) }
    var selectedFolderId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(revision) {
        folders = favoritesStore.folders()
        selectedFolderId = favoritesStore.get(favoriteKey)?.folderId
    }

    fun moveTo(folderId: Long?) {
        selectedFolderId = folderId
        scope.launch { favoritesStore.setFolder(favoriteKey, folderId) }
    }

    Column(
        modifier =
            Modifier
                .width(260.dp)
                .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(8.dp))
                .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(8.dp))
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = JewelTheme.globalColors.text.normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        FolderChoiceRow(
            label = stringResource(Res.string.favorites_no_folder),
            selected = selectedFolderId == null,
            onClick = {
                moveTo(null)
                onDismiss()
            },
        )
        folders.forEach { folder ->
            FolderChoiceRow(
                label = folder.name,
                selected = selectedFolderId == folder.id,
                onClick = {
                    moveTo(folder.id)
                    onDismiss()
                },
            )
        }

        NewFolderRow(
            onCreate = { name ->
                scope.launch {
                    val id = favoritesStore.createFolder(name, System.currentTimeMillis())
                    favoritesStore.setFolder(favoriteKey, id)
                    onDismiss()
                }
            },
        )
    }
}

@Composable
private fun FolderChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()
    val accent = JewelTheme.globalColors.outlines.focused
    val backgroundColor by animateColorAsState(
        targetValue =
            when {
                selected -> accent.copy(alpha = 0.12f)
                isHovered -> accent.copy(alpha = 0.08f)
                else -> Color.Transparent
            },
        animationSpec = tween(durationMillis = 150),
    )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(backgroundColor)
                .clickable(
                    onClick = onClick,
                    indication = null,
                    interactionSource = hoverSource,
                ).pointerHoverIcon(PointerIcon.Hand)
                .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            key = AllIconsKeys.Nodes.Folder,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = JewelTheme.globalColors.text.info,
        )
        Text(
            text = label,
            fontSize = 13.sp,
            color = JewelTheme.globalColors.text.normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                key = AllIconsKeys.Actions.Checked,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun NewFolderRow(onCreate: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    if (!editing) {
        val hoverSource = remember { MutableInteractionSource() }
        val isHovered by hoverSource.collectIsHoveredAsState()
        val accent = JewelTheme.globalColors.outlines.focused
        val backgroundColor by animateColorAsState(
            targetValue = if (isHovered) accent.copy(alpha = 0.08f) else Color.Transparent,
            animationSpec = tween(durationMillis = 150),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(backgroundColor)
                    .clickable(
                        onClick = { editing = true },
                        indication = null,
                        interactionSource = hoverSource,
                    ).pointerHoverIcon(PointerIcon.Hand)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                key = AllIconsKeys.General.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = JewelTheme.globalColors.text.info,
            )
            Text(
                text = stringResource(Res.string.favorites_new_folder),
                fontSize = 13.sp,
                color = JewelTheme.globalColors.text.normal,
            )
        }
    } else {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        fun confirm() {
            if (name.isNotBlank()) onCreate(name.trim())
            name = ""
            editing = false
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 13.sp, color = JewelTheme.globalColors.text.normal),
                cursorBrush = SolidColor(JewelTheme.globalColors.outlines.focused),
                modifier =
                    Modifier
                        .weight(1f)
                        .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(6.dp))
                        .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { event ->
                            when {
                                event.type == KeyEventType.KeyDown && event.key == Key.Enter -> {
                                    confirm()
                                    true
                                }
                                event.type == KeyEventType.KeyDown && event.key == Key.Escape -> {
                                    name = ""
                                    editing = false
                                    true
                                }
                                else -> false
                            }
                        },
                decorationBox = { innerTextField ->
                    Box {
                        if (name.isEmpty()) {
                            Text(
                                text = stringResource(Res.string.favorites_folder_name_placeholder),
                                fontSize = 13.sp,
                                color = JewelTheme.globalColors.text.info,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            IconButton(
                onClick = ::confirm,
                modifier = Modifier.size(28.dp),
                enabled = name.isNotBlank(),
            ) {
                Icon(
                    key = AllIconsKeys.Actions.Checked,
                    contentDescription = stringResource(Res.string.favorites_new_folder),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** Places the popup above its anchor, aligned to the anchor's leading edge. */
private object AboveAnchorPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.left
        val y = anchorBounds.top - popupContentSize.height
        return IntOffset(
            x = x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y = y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    }
}

private const val COPIED_FEEDBACK_MS = 1_500L
