package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsEvents
import io.github.kdroidfilter.seforimapp.core.favorites.FavoriteEntry
import io.github.kdroidfilter.seforimapp.core.favorites.FavoriteFolder
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icon.PathIconKey
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.favorites_empty
import seforimapp.seforimapp.generated.resources.favorites_search_placeholder
import seforimapp.seforimapp.generated.resources.favorites_show_all
import seforimapp.seforimapp.generated.resources.favorites_title
import java.util.UUID

// The IntelliJ star icons (Nodes.Favorite / NotFavoriteOnHover) carry fixed node colors;
// this local outline star matches the flat expUI palette of the other title-bar icons.
private object FavoritesIconAnchor

private val FavoritesStar = PathIconKey("icons/favorites.svg", FavoritesIconAnchor::class.java)

/**
 * Chrome's bookmarks menu equivalent for Linux/Windows: a title-bar button opening a popup that
 * lists the favorites (root entries first, then folders as sections) with a search field and a
 * footer leading to the full favorites page. macOS uses the native Favorites menu instead.
 */
@Composable
fun FavoritesMenuButton() {
    var visible by remember { mutableStateOf(false) }
    val shortcutHint = if (PlatformInfo.isMacOS) "⌘⌥B" else "Ctrl+Shift+O"

    Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
        TitleBarActionButton(
            key = FavoritesStar,
            contentDescription = stringResource(Res.string.favorites_title),
            onClick = { visible = !visible },
            tooltipText = stringResource(Res.string.favorites_title),
            shortcutHint = shortcutHint,
        )
        if (visible) {
            Popup(
                onDismissRequest = { visible = false },
                popupPositionProvider = BelowAnchorEndPositionProvider,
                properties = PopupProperties(focusable = true),
            ) {
                FavoritesPopupContent(onDismiss = { visible = false })
            }
        }
    }
}

@Composable
private fun FavoritesPopupContent(onDismiss: () -> Unit) {
    val favoritesStore = LocalAppGraph.current.favoritesStore
    val currentWindow = LocalOpenWindow.current
    val tabsViewModel = currentWindow.tabsViewModel

    var query by remember { mutableStateOf("") }

    // Favorites and folders, reactive to writes
    val revision by favoritesStore.revision.collectAsState()
    var entries by remember { mutableStateOf<List<FavoriteEntry>>(emptyList()) }
    var folders by remember { mutableStateOf<List<FavoriteFolder>>(emptyList()) }
    LaunchedEffect(query, revision) {
        entries = favoritesStore.query(query)
        folders = favoritesStore.folders()
    }

    fun openFavorite(entry: FavoriteEntry) {
        tabsViewModel.openTab(
            TabsDestination.BookContent(bookId = entry.bookId, tabId = UUID.randomUUID().toString(), lineId = entry.lineId),
        )
        onDismiss()
    }

    val rootEntries = entries.filter { it.folderId == null }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier =
            Modifier
                .width(340.dp)
                .background(JewelTheme.globalColors.panelBackground, PopupShape)
                .border(1.dp, JewelTheme.globalColors.borders.normal, PopupShape)
                .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PopupSearchField(
            query = query,
            onQueryChange = { query = it },
            placeholder = stringResource(Res.string.favorites_search_placeholder),
            focusRequester = focusRequester,
        )

        LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
            items(rootEntries, key = { "fav-" + it.key }) { entry ->
                PopupRow(
                    label = entry.title,
                    tabType = TabType.BOOK,
                    onClick = { openFavorite(entry) },
                    onClose = null,
                )
            }
            folders.forEach { folder ->
                val folderEntries = entries.filter { it.folderId == folder.id }
                if (folderEntries.isNotEmpty()) {
                    item(key = "folder-" + folder.id) { PopupSectionHeader(folder.name) }
                    items(folderEntries, key = { "fav-" + it.key }) { entry ->
                        PopupRow(
                            label = entry.title,
                            tabType = TabType.BOOK,
                            onClick = { openFavorite(entry) },
                            onClose = null,
                        )
                    }
                }
            }
            if (entries.isEmpty()) {
                item(key = "empty") {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(Res.string.favorites_empty),
                            fontSize = 12.sp,
                            color = JewelTheme.globalColors.text.info,
                        )
                    }
                }
            }
        }

        // Footer: full favorites page (chrome://bookmarks equivalent)
        PopupFooterRow(
            label = stringResource(Res.string.favorites_show_all),
            shortcutHint = if (PlatformInfo.isMacOS) "⌘⌥B" else "Ctrl+Shift+O",
            icon = FavoritesStar,
            onClick = {
                val tabs = tabsViewModel.state.value.tabs
                val existing = tabs.indexOfFirst { it.destination is TabsDestination.Favorites }
                if (existing >= 0) {
                    tabsViewModel.onEvent(TabsEvents.OnSelect(existing))
                } else {
                    tabsViewModel.openTab(TabsDestination.Favorites(tabId = UUID.randomUUID().toString()))
                }
                onDismiss()
            },
        )
    }
}
