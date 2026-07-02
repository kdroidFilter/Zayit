package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.rememberVectorPainter
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
import io.github.kdroidfilter.seforim.tabs.TabItem
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsEvents
import io.github.kdroidfilter.seforimapp.core.history.VisitEntry
import io.github.kdroidfilter.seforimapp.core.history.VisitKind
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow
import io.github.kdroidfilter.seforimapp.framework.desktop.OpenWindow
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import io.github.kdroidfilter.seforimapp.icons.bookOpenTabs
import io.github.kdroidfilter.seforimapp.icons.homeTabs
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.history_title
import seforimapp.seforimapp.generated.resources.home
import seforimapp.seforimapp.generated.resources.open_tabs
import seforimapp.seforimapp.generated.resources.tab_search_no_results
import seforimapp.seforimapp.generated.resources.tab_search_placeholder
import seforimapp.seforimapp.generated.resources.tab_search_show_all_history
import seforimapp.seforimapp.generated.resources.tab_search_tooltip
import java.util.UUID

private const val POPUP_HISTORY_LIMIT = 12

private data class OpenTabRow(
    val window: OpenWindow,
    val tab: TabItem,
)

/**
 * Chrome's Tab Search equivalent: a title-bar button opening a popup that searches across the
 * open tabs of every window AND the persistent visit history, with a footer leading to the full
 * history page. Toggled by the button or Cmd/Ctrl+Shift+A.
 */
@Composable
fun TabSearchButton() {
    val openWindow = LocalOpenWindow.current
    val visible by openWindow.tabSearchVisible.collectAsState()
    val shortcutHint = if (PlatformInfo.isMacOS) "⌘⇧A" else "Ctrl+Shift+A"

    Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
        TitleBarActionButton(
            // Vcs.History (clock): visually distinct from the find-in-page magnifier
            key = AllIconsKeys.Vcs.History,
            contentDescription = stringResource(Res.string.tab_search_tooltip),
            onClick = { openWindow.tabSearchVisible.value = !visible },
            tooltipText = stringResource(Res.string.tab_search_tooltip),
            shortcutHint = shortcutHint,
        )
        if (visible) {
            Popup(
                onDismissRequest = { openWindow.tabSearchVisible.value = false },
                popupPositionProvider = BelowAnchorEndPositionProvider,
                properties = PopupProperties(focusable = true),
            ) {
                TabSearchPopupContent(onDismiss = { openWindow.tabSearchVisible.value = false })
            }
        }
    }
}

@Composable
private fun TabSearchPopupContent(onDismiss: () -> Unit) {
    val appGraph = LocalAppGraph.current
    val desktopManager = appGraph.desktopManager
    val historyStore = appGraph.historyStore
    val currentWindow = LocalOpenWindow.current

    var query by remember { mutableStateOf("") }

    // Open tabs across every window, reactive to per-window tab changes
    val windows by desktopManager.windows.collectAsState()
    val openTabs by remember(windows) {
        if (windows.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(windows.map { w -> w.tabsViewModel.state.map { st -> st.tabs.map { OpenTabRow(w, it) } } }) { arr ->
                arr.flatMap { it }
            }
        }
    }.collectAsState(
        initial =
            windows.flatMap { w ->
                w.tabsViewModel.state.value.tabs
                    .map { OpenTabRow(w, it) }
            },
    )

    // History section, reactive to writes
    val revision by historyStore.revision.collectAsState()
    var historyEntries by remember { mutableStateOf<List<VisitEntry>>(emptyList()) }
    LaunchedEffect(query, revision) {
        historyEntries = historyStore.query(query, POPUP_HISTORY_LIMIT)
    }

    val homeLabel = stringResource(Res.string.home)
    val filteredTabs =
        openTabs.filter { row ->
            val label = row.tab.title.ifBlank { homeLabel }
            query.isBlank() || label.contains(query.trim(), ignoreCase = true)
        }

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
        // Search field
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(6.dp))
                    .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                key = AllIconsKeys.Actions.Find,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = JewelTheme.globalColors.text.info,
            )
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 13.sp, color = JewelTheme.globalColors.text.normal),
                cursorBrush = SolidColor(JewelTheme.globalColors.outlines.focused),
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(Res.string.tab_search_placeholder),
                                fontSize = 13.sp,
                                color = JewelTheme.globalColors.text.info,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }

        LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
            if (filteredTabs.isNotEmpty()) {
                item(key = "header-tabs") { SectionHeader(stringResource(Res.string.open_tabs)) }
                items(filteredTabs, key = { "tab-" + it.tab.destination.tabId }) { row ->
                    val label = row.tab.title.ifBlank { homeLabel }
                    PopupRow(
                        label = label,
                        tabType = if (row.tab.title.isBlank()) null else row.tab.tabType,
                        onClick = {
                            val index =
                                row.window.tabsViewModel.state.value.tabs
                                    .indexOfFirst { it.destination.tabId == row.tab.destination.tabId }
                            if (index >= 0) {
                                row.window.tabsViewModel.onEvent(TabsEvents.OnSelect(index))
                                row.window.requestFocus()
                                desktopManager.onWindowFocused(row.window.id)
                            }
                            onDismiss()
                        },
                        onClose = {
                            val index =
                                row.window.tabsViewModel.state.value.tabs
                                    .indexOfFirst { it.destination.tabId == row.tab.destination.tabId }
                            if (index >= 0) row.window.tabsViewModel.onEvent(TabsEvents.OnClose(index))
                        },
                    )
                }
            }
            if (historyEntries.isNotEmpty()) {
                item(key = "header-history") { SectionHeader(stringResource(Res.string.history_title)) }
                items(historyEntries, key = { "hist-" + it.key }) { entry ->
                    PopupRow(
                        label = entry.title,
                        tabType = if (entry.kind == VisitKind.BOOK) TabType.BOOK else TabType.SEARCH,
                        onClick = {
                            val destination =
                                when (entry.kind) {
                                    VisitKind.BOOK ->
                                        entry.bookId?.let {
                                            TabsDestination.BookContent(
                                                bookId = it,
                                                tabId = UUID.randomUUID().toString(),
                                                lineId = entry.lineId,
                                            )
                                        }
                                    VisitKind.SEARCH ->
                                        entry.searchQuery?.let {
                                            TabsDestination.Search(searchQuery = it, tabId = UUID.randomUUID().toString())
                                        }
                                }
                            if (destination != null) {
                                currentWindow.tabsViewModel.openTab(destination)
                            }
                            onDismiss()
                        },
                        onClose = null,
                    )
                }
            }
            if (filteredTabs.isEmpty() && historyEntries.isEmpty()) {
                item(key = "empty") {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(Res.string.tab_search_no_results),
                            fontSize = 12.sp,
                            color = JewelTheme.globalColors.text.info,
                        )
                    }
                }
            }
        }

        // Footer: full history page (chrome://history equivalent)
        FooterRow(
            label = stringResource(Res.string.tab_search_show_all_history),
            shortcutHint = if (PlatformInfo.isMacOS) "⌘Y" else "Ctrl+H",
            onClick = {
                currentWindow.tabsViewModel.openTab(TabsDestination.History(tabId = UUID.randomUUID().toString()))
                onDismiss()
            },
        )
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = JewelTheme.globalColors.text.info,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
    )
}

@Composable
private fun PopupRow(
    label: String,
    tabType: TabType?,
    onClick: () -> Unit,
    onClose: (() -> Unit)?,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val accent = JewelTheme.globalColors.outlines.focused

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .hoverable(interactionSource)
                .background(if (isHovered) accent.copy(alpha = 0.08f) else Color.Transparent, RowShape)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (tabType) {
            TabType.BOOK ->
                Image(
                    painter = rememberVectorPainter(bookOpenTabs(JewelTheme.globalColors.text.normal)),
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    colorFilter = ColorFilter.tint(JewelTheme.globalColors.text.normal),
                )
            TabType.HISTORY ->
                Icon(
                    key = AllIconsKeys.Vcs.History,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = JewelTheme.globalColors.text.normal,
                )
            TabType.SEARCH ->
                Icon(
                    key = AllIconsKeys.Actions.Find,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = JewelTheme.globalColors.text.normal,
                )
            null ->
                Image(
                    painter = rememberVectorPainter(homeTabs(JewelTheme.globalColors.text.normal)),
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    colorFilter = ColorFilter.tint(JewelTheme.globalColors.text.normal),
                )
        }
        Text(
            text = label,
            fontSize = 13.sp,
            color = JewelTheme.globalColors.text.normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onClose != null) {
            Box(
                modifier =
                    Modifier
                        .size(18.dp)
                        .background(if (isHovered) accent.copy(alpha = 0.10f) else Color.Transparent, CircleShape)
                        .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    key = AllIconsKeys.Actions.Close,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = if (isHovered) JewelTheme.globalColors.text.normal else Color.Transparent,
                )
            }
        }
    }
}

@Composable
private fun FooterRow(
    label: String,
    shortcutHint: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val accent = JewelTheme.globalColors.outlines.focused

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .hoverable(interactionSource)
                .background(if (isHovered) accent.copy(alpha = 0.08f) else Color.Transparent, RowShape)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            key = AllIconsKeys.Vcs.History,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = JewelTheme.globalColors.text.info,
        )
        Text(
            text = label,
            fontSize = 13.sp,
            color = JewelTheme.globalColors.text.info,
            modifier = Modifier.weight(1f),
        )
        // Same convention as the title-bar buttons: dimmed shortcut hint next to the label
        Text(
            text = shortcutHint,
            fontSize = 12.sp,
            color = JewelTheme.globalColors.text.disabled,
        )
    }
}

private val PopupShape = RoundedCornerShape(8.dp)
private val RowShape = RoundedCornerShape(5.dp)

/** Positions the popup below the anchor, aligned to its end edge. */
private object BelowAnchorEndPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.right - popupContentSize.width
        val y = anchorBounds.bottom
        return IntOffset(
            x = x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y = y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    }
}
