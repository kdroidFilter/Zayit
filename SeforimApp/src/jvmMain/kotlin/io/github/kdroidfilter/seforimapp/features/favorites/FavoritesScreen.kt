@file:OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)

package io.github.kdroidfilter.seforimapp.features.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.favorites.FavoriteEntry
import io.github.kdroidfilter.seforimapp.core.favorites.FavoriteFolder
import io.github.kdroidfilter.seforimapp.core.presentation.components.CardSurface
import io.github.kdroidfilter.seforimapp.core.presentation.components.EmptyState
import io.github.kdroidfilter.seforimapp.core.presentation.components.InputPopup
import io.github.kdroidfilter.seforimapp.core.presentation.components.ListPageContainer
import io.github.kdroidfilter.seforimapp.core.presentation.components.ListRow
import io.github.kdroidfilter.seforimapp.core.presentation.components.PageHeader
import io.github.kdroidfilter.seforimapp.core.presentation.components.PageSearchField
import io.github.kdroidfilter.seforimapp.core.presentation.components.SectionHeader
import io.github.kdroidfilter.seforimapp.core.presentation.components.StartBelowAnchorPositionProvider
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.logger.debugln
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.favorites_create_folder
import seforimapp.seforimapp.generated.resources.favorites_empty
import seforimapp.seforimapp.generated.resources.favorites_folder_name_placeholder
import seforimapp.seforimapp.generated.resources.favorites_new_folder
import seforimapp.seforimapp.generated.resources.favorites_no_folder
import seforimapp.seforimapp.generated.resources.favorites_search_placeholder
import seforimapp.seforimapp.generated.resources.favorites_title
import java.util.UUID

/**
 * Favorites page: a space-efficient, Jewel-styled bookmark manager.
 * Unfiled favorites are shown as a flat list at the top. Folder favorites are grouped
 * into collapsible cards. Everything is reachable through search and right-click context.
 */
@Composable
fun FavoritesTabContent(tabId: String) {
    val appGraph = LocalAppGraph.current
    val favoritesStore = appGraph.favoritesStore
    val tabsViewModel = LocalOpenWindow.current.tabsViewModel
    val scope = rememberCoroutineScope()

    val favoritesTitle = stringResource(Res.string.favorites_title)
    LaunchedEffect(tabId, favoritesTitle) {
        appGraph.tabTitleUpdateManager.updateTabTitle(tabId, favoritesTitle, TabType.FAVORITES)
    }

    var query by remember { mutableStateOf("") }
    val revision by favoritesStore.revision.collectAsState()
    var entries by remember { mutableStateOf<List<FavoriteEntry>>(emptyList()) }
    var folders by remember { mutableStateOf<List<FavoriteFolder>>(emptyList()) }
    LaunchedEffect(query, revision) {
        entries = favoritesStore.query(query)
        folders = favoritesStore.folders()
    }

    fun openEntry(entry: FavoriteEntry) {
        debugln { "[Favorites] open ${entry.key}" }
        tabsViewModel.replaceCurrentTabWithNewTabId(
            TabsDestination.BookContent(bookId = entry.bookId, tabId = UUID.randomUUID().toString(), lineId = entry.lineId),
        )
    }

    FavoritesPageContent(
        query = query,
        onQueryChange = { query = it },
        entries = entries,
        folders = folders,
        onOpen = ::openEntry,
        onDelete = { entry -> scope.launch { favoritesStore.remove(entry.key) } },
        onCreateFolder = { name -> scope.launch { favoritesStore.createFolder(name, System.currentTimeMillis()) } },
        onDeleteFolder = { folder -> scope.launch { favoritesStore.deleteFolder(folder.id) } },
        onMoveToFolder = { entry, folderId -> scope.launch { favoritesStore.setFolder(entry.key, folderId) } },
    )
}

@Composable
private fun FavoritesPageContent(
    query: String,
    onQueryChange: (String) -> Unit,
    entries: List<FavoriteEntry>,
    folders: List<FavoriteFolder>,
    onOpen: (FavoriteEntry) -> Unit,
    onDelete: (FavoriteEntry) -> Unit,
    onCreateFolder: (String) -> Unit,
    onDeleteFolder: (FavoriteFolder) -> Unit,
    onMoveToFolder: (FavoriteEntry, Long?) -> Unit,
) {
    val byFolder = remember(entries) { entries.groupBy { it.folderId } }
    val unfiled = byFolder[null].orEmpty()
    val accent = JewelTheme.globalColors.outlines.focused

    ListPageContainer {
        PageHeader(
            title = stringResource(Res.string.favorites_title),
            actions = { NewFolderButton(onCreate = onCreateFolder) },
        )

        PageSearchField(
            query = query,
            placeholder = Res.string.favorites_search_placeholder,
            onQueryChange = onQueryChange,
        )

        if (entries.isEmpty() && folders.isEmpty()) {
            EmptyState(
                iconKey = AllIconsKeys.Nodes.NotFavoriteOnHover,
                message = stringResource(Res.string.favorites_empty),
            )
        } else {
            if (folders.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.widthIn(max = 720.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    maxItemsInEachRow = 2,
                ) {
                    folders.forEach { folder ->
                        val folderEntries = byFolder[folder.id].orEmpty()
                        if (query.isNotBlank() && folderEntries.isEmpty()) return@forEach
                        FolderCard(
                            folder = folder,
                            entries = folderEntries,
                            onOpen = onOpen,
                            onDeleteEntry = onDelete,
                            onDeleteFolder = onDeleteFolder,
                            onMoveToFolder = onMoveToFolder,
                            allFolders = folders,
                            accent = accent,
                        )
                    }
                }
            }

            if (unfiled.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.favorites_no_folder),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = JewelTheme.globalColors.text.info,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                CardSurface {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        unfiled.forEachIndexed { index, entry ->
                            FavoriteRowWithContextMenu(
                                entry = entry,
                                folders = folders,
                                onOpen = { onOpen(entry) },
                                onDelete = { onDelete(entry) },
                                onMoveToFolder = { id -> onMoveToFolder(entry, id) },
                                showDivider = index < unfiled.lastIndex,
                                accent = accent,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewFolderButton(onCreate: (String) -> Unit) {
    var showPopup by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    Box {
        IconButton(
            onClick = {
                showPopup = true
                name = ""
            },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                key = AllIconsKeys.General.Add,
                contentDescription = stringResource(Res.string.favorites_new_folder),
                modifier = Modifier.size(18.dp),
            )
        }

        if (showPopup) {
            Popup(
                onDismissRequest = { showPopup = false },
                popupPositionProvider = StartBelowAnchorPositionProvider,
                properties = PopupProperties(focusable = true),
            ) {
                InputPopup(
                    title = stringResource(Res.string.favorites_new_folder),
                    value = name,
                    placeholder = Res.string.favorites_folder_name_placeholder,
                    confirmText = stringResource(Res.string.favorites_create_folder),
                    onValueChange = { name = it },
                    onConfirm = {
                        if (name.isNotBlank()) onCreate(name.trim())
                        showPopup = false
                        name = ""
                    },
                    onCancel = {
                        showPopup = false
                        name = ""
                    },
                )
            }
        }
    }
}

@Composable
private fun FolderCard(
    folder: FavoriteFolder,
    entries: List<FavoriteEntry>,
    onOpen: (FavoriteEntry) -> Unit,
    onDeleteEntry: (FavoriteEntry) -> Unit,
    onDeleteFolder: (FavoriteFolder) -> Unit,
    onMoveToFolder: (FavoriteEntry, Long?) -> Unit,
    allFolders: List<FavoriteFolder>,
    accent: Color,
) {
    var expanded by remember { mutableStateOf(true) }

    CardSurface(modifier = Modifier.widthIn(min = 220.dp, max = 320.dp)) {
        Column {
            SectionHeader(
                title = folder.name,
                count = entries.size,
                expanded = expanded,
                onToggleExpand = { expanded = !expanded },
                onDelete = { onDeleteFolder(folder) },
                leadingIconKey = AllIconsKeys.Nodes.Folder,
            )

            if (expanded && entries.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    entries.forEachIndexed { index, entry ->
                        FavoriteRowWithContextMenu(
                            entry = entry,
                            folders = allFolders,
                            onOpen = { onOpen(entry) },
                            onDelete = { onDeleteEntry(entry) },
                            onMoveToFolder = { id -> onMoveToFolder(entry, id) },
                            compact = true,
                            showDivider = index < entries.lastIndex,
                            accent = accent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteRowWithContextMenu(
    entry: FavoriteEntry,
    folders: List<FavoriteFolder>,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onMoveToFolder: (Long?) -> Unit,
    accent: Color,
    compact: Boolean = false,
    showDivider: Boolean = true,
) {
    var contextMenuOpen by remember { mutableStateOf(false) }

    Box(
        modifier =
            Modifier
                .onPointerEvent(PointerEventType.Press) { event ->
                    if (event.buttons.isSecondaryPressed) contextMenuOpen = true
                },
    ) {
        ListRow(
            title = entry.title,
            onOpen = onOpen,
            onDelete = onDelete,
            leadingIconKey = AllIconsKeys.Nodes.Favorite,
            leadingTint = accent,
            compact = compact,
            showDivider = showDivider,
        )

        if (contextMenuOpen) {
            FolderMovePopup(
                folders = folders,
                currentFolderId = entry.folderId,
                onMove = { onMoveToFolder(it) },
                onDismiss = { contextMenuOpen = false },
            )
        }
    }
}

@Composable
private fun FolderMovePopup(
    folders: List<FavoriteFolder>,
    currentFolderId: Long?,
    onMove: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    Popup(
        popupPositionProvider = StartBelowAnchorPositionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        CardSurface(modifier = Modifier.widthIn(max = 220.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(Res.string.favorites_no_folder),
                    fontSize = 13.sp,
                    fontWeight = if (currentFolderId == null) FontWeight.SemiBold else FontWeight.Normal,
                    color = JewelTheme.globalColors.text.normal,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                onMove(null)
                                onDismiss()
                            }.padding(horizontal = 8.dp, vertical = 6.dp),
                )
                folders.forEach { folder ->
                    Text(
                        text = folder.name,
                        fontSize = 13.sp,
                        fontWeight = if (currentFolderId == folder.id) FontWeight.SemiBold else FontWeight.Normal,
                        color = JewelTheme.globalColors.text.normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    onMove(folder.id)
                                    onDismiss()
                                }.padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
