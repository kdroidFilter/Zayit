package io.github.kdroidfilter.seforimapp.features.bookcontent

import androidx.compose.foundation.ContextMenuDataProvider
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.LocalTextContextMenu
import androidx.compose.foundation.text.TextContextMenu
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforimapp.core.annotations.HighlightColors
import io.github.kdroidfilter.seforimapp.core.annotations.HighlightStore
import io.github.kdroidfilter.seforimapp.core.annotations.resolveHighlightRangesForSelection
import io.github.kdroidfilter.seforimapp.core.buildCopyWithSourcePayload
import io.github.kdroidfilter.seforimapp.core.deeplink.bookShareLink
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import io.github.kdroidfilter.seforimapp.core.resolveLineRangeFromSelection
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.SplitDefaults
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.EndVerticalBar
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.EnhancedHorizontalSplitPane
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.StartVerticalBar
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.asStable
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.BookContentPanel
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.HomeSearchCallbacks
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.booktoc.BookTocPanel
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.categorytree.CategoryTreePanel
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeUiState
import io.github.kdroidfilter.seforimapp.framework.database.CatalogCache
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.Ink_pen
import io.github.kdroidfilter.seforimlibrary.core.text.HebrewTextUtils
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.SplitPaneState
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.ContextMenuItemOption
import org.jetbrains.jewel.ui.component.DefaultMenuController
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.LocalMenuController
import org.jetbrains.jewel.ui.component.MenuContent
import org.jetbrains.jewel.ui.component.MenuController
import org.jetbrains.jewel.ui.component.MenuScope
import org.jetbrains.jewel.ui.component.Popup
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.ui.component.styling.MenuStyle
import org.jetbrains.jewel.ui.icon.PathIconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.menuStyle
import org.jetbrains.skiko.hostOs
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.context_menu_copy_link
import seforimapp.seforimapp.generated.resources.context_menu_copy_with_source
import seforimapp.seforimapp.generated.resources.context_menu_copy_without_nikud
import seforimapp.seforimapp.generated.resources.context_menu_find_in_page
import seforimapp.seforimapp.generated.resources.context_menu_highlight
import seforimapp.seforimapp.generated.resources.context_menu_search_selected_text
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.InputEvent
import javax.swing.KeyStroke
import kotlin.math.roundToInt
import androidx.compose.foundation.ContextMenuRepresentation as ComposeContextMenuRepresentation

private val TextSearchContextMenuIconKey = PathIconKey("icons/lucide_text_search.svg", BookContentViewModel::class.java)

private class ContextMenuItemOptionWithKeybinding(
    val icon: org.jetbrains.jewel.ui.icon.IconKey? = null,
    val keybinding: Set<String>? = null,
    val enabled: Boolean = true,
    label: String,
    action: () -> Unit,
) : ContextMenuItem(label, action)

/** Context-menu entry rendering a row of highlight color swatches (last item, plus a "clear"). */
private class ContextMenuHighlightColorPicker(
    val colors: List<Color>,
    val onColorSelected: (Color) -> Unit,
    val onDismiss: () -> Unit,
) : ContextMenuItem("highlight", {})

@OptIn(InternalJewelApi::class)
private object BookContentContextMenuRepresentationWithKeybindings : ComposeContextMenuRepresentation {
    @Composable
    override fun Representation(
        state: ContextMenuState,
        items: () -> List<ContextMenuItem>,
    ) {
        val isOpen = state.status is ContextMenuState.Status.Open
        if (!isOpen) return

        val resolvedItems by remember { derivedStateOf { items() } }
        if (resolvedItems.isEmpty()) return

        BookContentContextMenu(
            onDismissRequest = {
                state.status = ContextMenuState.Status.Closed
                true
            },
            style = JewelTheme.menuStyle,
        ) {
            contextItems(resolvedItems)
        }
    }
}

@OptIn(InternalJewelApi::class)
@Composable
private fun BookContentContextMenu(
    onDismissRequest: (InputMode) -> Boolean,
    modifier: Modifier = Modifier,
    focusable: Boolean = true,
    style: MenuStyle = JewelTheme.menuStyle,
    content: MenuScope.() -> Unit,
) {
    var focusManager: FocusManager? by remember { mutableStateOf(null) }
    var inputModeManager: InputModeManager? by remember { mutableStateOf(null) }
    val menuController = remember(onDismissRequest) { DefaultMenuController(onDismissRequest = onDismissRequest) }

    Popup(
        popupPositionProvider =
            androidx.compose.ui.window
                .rememberCursorPositionProvider(style.metrics.offset),
        onDismissRequest = { onDismissRequest(InputMode.Touch) },
        properties =
            androidx.compose.ui.window
                .PopupProperties(focusable = focusable),
        onPreviewKeyEvent = { false },
        onKeyEvent = { keyEvent ->
            val currentFocusManager = focusManager ?: return@Popup false
            val currentInputModeManager = inputModeManager ?: return@Popup false

            val swingKeyStroke = composeKeyEventToSwingKeyStroke(keyEvent)
            menuController.findAndExecuteShortcut(swingKeyStroke)
                ?: handlePopupMenuOnKeyEvent(keyEvent, currentFocusManager, currentInputModeManager, menuController)
        },
        cornerSize = style.metrics.cornerSize,
    ) {
        @Suppress("AssignedValueIsNeverRead")
        focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        @Suppress("AssignedValueIsNeverRead")
        inputModeManager = androidx.compose.ui.platform.LocalInputModeManager.current

        CompositionLocalProvider(LocalMenuController provides menuController) {
            MenuContent(modifier = modifier, content = content)
        }
    }
}

private fun MenuScope.contextItems(items: List<ContextMenuItem>) {
    for (item in items) {
        when (item) {
            is org.jetbrains.jewel.ui.component.ContextMenuDivider -> separator()
            is org.jetbrains.jewel.ui.component.ContextSubmenu -> submenu(submenu = { contextItems(item.submenu()) }) { Text(item.label) }
            is ContextMenuHighlightColorPicker -> {
                separator()
                passiveItem {
                    HighlightColorPickerRow(
                        colors = item.colors,
                        onSelect = { color ->
                            item.onColorSelected(color)
                            item.onDismiss()
                        },
                    )
                }
            }

            is ContextMenuItemOptionWithKeybinding ->
                selectableItem(
                    selected = false,
                    iconKey = item.icon,
                    keybinding = item.keybinding,
                    onClick = item.onClick,
                    enabled = item.enabled,
                ) {
                    Text(item.label)
                }

            is ContextMenuItemOption ->
                selectableItemWithActionType(
                    selected = false,
                    onClick = item.onClick,
                    iconKey = item.icon,
                    actionType = item.actionType,
                    enabled = item.enabled,
                ) {
                    Text(item.label)
                }

            else -> selectableItem(selected = false, onClick = item.onClick) { Text(item.label) }
        }
    }
}

/** A row of color swatches (plus a "clear" cross) for highlighting the current selection. */
@Composable
private fun HighlightColorPickerRow(
    colors: List<Color>,
    onSelect: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val highlightLabel = stringResource(Res.string.context_menu_highlight)
    // passiveItem does not provide LocalContentColor, so read the menu item color explicitly.
    val contentColor = JewelTheme.menuStyle.colors.itemColors.content
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = Ink_pen, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
        Text(text = highlightLabel, color = contentColor, modifier = Modifier.padding(horizontal = 8.dp))
        colors.forEach { color ->
            HighlightColorSwatch(color = color, onClick = { onSelect(color) })
        }
    }
}

/** A single hover-aware swatch; [Color.Transparent] renders a "clear" cross. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun HighlightColorSwatch(
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isHovered by remember { mutableStateOf(false) }
    val hoverBackground = JewelTheme.menuStyle.colors.itemColors.backgroundHovered
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(4.dp))
                .background(if (isHovered) hoverBackground else Color.Transparent)
                .onPointerEvent(PointerEventType.Enter) { isHovered = true }
                .onPointerEvent(PointerEventType.Exit) { isHovered = false }
                .clickable { onClick() }
                .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (color == Color.Transparent) {
            Icon(key = AllIconsKeys.Windows.Close, contentDescription = null, modifier = Modifier.size(16.dp))
        } else {
            Box(modifier = Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(color))
        }
    }
}

/**
 * Resolves the selected text to a (line, offset range) and persists/clears a highlight.
 * Offsets are computed against the line's plain text with diacritics so they stay valid
 * regardless of the current diacritics setting. No-op if the selection cannot be located.
 */
private fun applyHighlightFromSelection(
    selectedText: String,
    lines: List<io.github.kdroidfilter.seforimlibrary.core.models.Line>,
    bookId: Long,
    color: Color,
    showDiacritics: Boolean,
    store: HighlightStore,
    @StructuredScope scope: CoroutineScope,
) {
    // All materialized lines (ordered, plain text WITH diacritics so offsets match what the
    // renderer applies). The resolver splits the selection per line and anchors it here.
    val sortedVisible =
        lines
            .sortedBy { it.lineIndex }
            .map { it.id to buildAnnotatedFromHtml(it.content, baseTextSize = 16f, boldScale = 1f).text }
    val ranges = resolveHighlightRangesForSelection(sortedVisible, selectedText, showDiacritics)
    if (ranges.isEmpty()) return
    scope.launch {
        ranges.forEach { (lineId, range) ->
            if (color == Color.Transparent) {
                store.removeOverlapping(bookId, lineId, range.first, range.last + 1)
            } else {
                store.addHighlight(bookId, lineId, range.first, range.last + 1, color, System.currentTimeMillis())
            }
        }
    }
}

private fun handlePopupMenuOnKeyEvent(
    keyEvent: KeyEvent,
    focusManager: FocusManager,
    inputModeManager: InputModeManager,
    menuController: MenuController,
): Boolean {
    if (keyEvent.type != KeyEventType.KeyDown) return false

    return when (keyEvent.key) {
        Key.DirectionDown -> {
            inputModeManager.requestInputMode(InputMode.Keyboard)
            focusManager.moveFocus(FocusDirection.Next)
            true
        }

        Key.DirectionUp -> {
            inputModeManager.requestInputMode(InputMode.Keyboard)
            focusManager.moveFocus(FocusDirection.Previous)
            true
        }

        Key.Escape -> {
            inputModeManager.requestInputMode(InputMode.Keyboard)
            menuController.closeAll(InputMode.Keyboard, true)
            true
        }

        Key.DirectionLeft -> {
            if (menuController.isSubmenu()) {
                inputModeManager.requestInputMode(InputMode.Keyboard)
                menuController.close(InputMode.Keyboard)
                true
            } else {
                false
            }
        }

        else -> false
    }
}

private fun composeKeyEventToSwingKeyStroke(event: KeyEvent): KeyStroke? {
    val awtKeyCode = event.key.nativeKeyCode
    var modifiers = 0

    if (event.isCtrlPressed) modifiers = modifiers or InputEvent.CTRL_DOWN_MASK
    if (event.isMetaPressed) modifiers = modifiers or InputEvent.META_DOWN_MASK
    if (event.isAltPressed) modifiers = modifiers or InputEvent.ALT_DOWN_MASK
    if (event.isShiftPressed) modifiers = modifiers or InputEvent.SHIFT_DOWN_MASK

    return KeyStroke.getKeyStroke(awtKeyCode, modifiers, false)
}

/**
 * Displays the content view of a book with multiple panels configured within split panes.
 *
 * @param uiState The complete UI state used for rendering the book content screen, capturing navigation, TOC, content display, layout management, and more.
 * @param onEvent Function that handles various user-driven events or state updates within the book content view.
 * @param showDiacritics Whether to render Hebrew diacritics for the current root category.
 */
@OptIn(ExperimentalSplitPaneApi::class, FlowPreview::class, ExperimentalFoundationApi::class)
@Composable
fun BookContentScreen(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    showDiacritics: Boolean,
    searchUi: SearchHomeUiState,
    searchCallbacks: HomeSearchCallbacks,
    isRestoringSession: Boolean = false,
    isSelected: Boolean = true,
    bookCharCounts: IntArray? = null,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    val searchSelectedLabel = stringResource(Res.string.context_menu_search_selected_text)
    val findInPageLabel = stringResource(Res.string.context_menu_find_in_page)
    val copyWithoutNikudLabel = stringResource(Res.string.context_menu_copy_without_nikud)
    val copyWithSourceLabel = stringResource(Res.string.context_menu_copy_with_source)
    val copyLinkLabel = stringResource(Res.string.context_menu_copy_link)
    val baseTextContextMenu = LocalTextContextMenu.current
    val tabId = uiState.tabId
    val selectedBook = uiState.navigation.selectedBook
    val bookHasDiacritics = selectedBook?.hasNekudot == true || selectedBook?.hasTeamim == true
    val selectionContext = LocalAppGraph.current.selectionContext
    // Always read the latest values inside the context-menu actions, without rebuilding the menu.
    val currentSelectedBook by rememberUpdatedState(selectedBook)

    // User-highlight persistence (separate local user DB).
    val highlightStore = LocalAppGraph.current.highlightStore
    val highlightScope = rememberCoroutineScope()
    val bookId = selectedBook?.id ?: 0L

    // Publish the active book + its root category to the SelectionContext so the Ctrl+Alt+C
    // dispatcher and the context-menu action can apply per-tradition formatting. Lifecycle
    // clears are tabId-scoped so a backgrounded or disposed tab cannot wipe the foreground
    // tab's published book — even when both tabs happen to reference the same book.
    LaunchedEffect(selectedBook, isSelected, tabId, selectionContext) {
        if (isSelected) {
            val rootTitle = selectedBook?.let { CatalogCache.getRootForBook(it)?.title }
            selectionContext.setActiveBook(tabId, selectedBook, rootTitle)
        } else {
            selectionContext.clearActiveBookIfOwnedBy(tabId)
        }
    }
    DisposableEffect(tabId, selectionContext) {
        onDispose { selectionContext.clearActiveBookIfOwnedBy(tabId) }
    }

    val textContextMenu =
        remember(
            baseTextContextMenu,
            tabId,
            onEvent,
            searchSelectedLabel,
            findInPageLabel,
            copyWithoutNikudLabel,
            copyWithSourceLabel,
            copyLinkLabel,
            showDiacritics,
            bookHasDiacritics,
            bookId,
        ) {
            object : TextContextMenu {
                @OptIn(ExperimentalFoundationApi::class)
                @Composable
                override fun Area(
                    textManager: TextContextMenu.TextManager,
                    state: ContextMenuState,
                    content: @Composable () -> Unit,
                ) {
                    // Mirror the current selection into the SelectionContext so the AWT
                    // keyboard dispatcher can read it without touching Compose state directly.
                    LaunchedEffect(textManager.selectedText.text) {
                        selectionContext.setSelectedText(textManager.selectedText.text)
                    }

                    ContextMenuDataProvider(
                        items = {
                            val query = normalizeSearchQuery(textManager.selectedText.text)
                            val selectedText = textManager.selectedText.text
                            buildList {
                                // Copy without nikud option - first position, only show when book has diacritics and they are enabled
                                if (bookHasDiacritics &&
                                    showDiacritics &&
                                    selectedText.isNotBlank() &&
                                    (HebrewTextUtils.containsNikud(selectedText) || HebrewTextUtils.containsTeamim(selectedText))
                                ) {
                                    add(
                                        ContextMenuItemOptionWithKeybinding(
                                            icon = AllIconsKeys.Actions.Copy,
                                            keybinding =
                                                if (hostOs.isMacOS) {
                                                    linkedSetOf("⇧", "⌘", "C")
                                                } else {
                                                    linkedSetOf("Ctrl", "Shift", "C")
                                                },
                                            label = copyWithoutNikudLabel,
                                        ) {
                                            val textWithoutDiacritics = HebrewTextUtils.removeAllDiacritics(selectedText)
                                            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                                            clipboard.setContents(StringSelection(textWithoutDiacritics), null)
                                        },
                                    )
                                }
                                // Copy with source: append "(book ref – line ref)" to the selection
                                val bookForCopy = currentSelectedBook
                                if (bookForCopy != null && selectedText.isNotBlank()) {
                                    add(
                                        ContextMenuItemOptionWithKeybinding(
                                            icon = AllIconsKeys.Actions.Copy,
                                            keybinding =
                                                if (hostOs.isMacOS) {
                                                    linkedSetOf("⌥", "⌘", "C")
                                                } else {
                                                    linkedSetOf("Ctrl", "Alt", "C")
                                                },
                                            label = copyWithSourceLabel,
                                        ) {
                                            val payload =
                                                buildCopyWithSourcePayload(
                                                    selectedText,
                                                    bookForCopy,
                                                    selectionContext.activeBook.value?.rootTitle,
                                                    selectionContext.visibleLines.value.lines,
                                                )
                                            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                                            clipboard.setContents(StringSelection(payload), null)
                                        },
                                    )
                                }
                                // Copy a shareable deep link. Available whenever a book is active,
                                // with or without a text selection: when nothing is selected it falls
                                // back to the right-clicked line recorded in the SelectionContext.
                                if (bookForCopy != null) {
                                    add(
                                        ContextMenuItemOption(
                                            icon = AllIconsKeys.ToolbarDecorator.AddLink,
                                            label = copyLinkLabel,
                                        ) {
                                            val lineId =
                                                if (selectedText.isNotBlank()) {
                                                    resolveLineRangeFromSelection(
                                                        selectedText,
                                                        selectionContext.visibleLines.value.lines,
                                                    )?.first?.id
                                                } else {
                                                    selectionContext.currentLineId.value.takeIf { it > 0 }
                                                }
                                            val link = bookShareLink(bookForCopy.id, lineId)
                                            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                                            clipboard.setContents(StringSelection(link), null)
                                        },
                                    )
                                }
                                if (query.isNotBlank()) {
                                    add(
                                        ContextMenuItemOption(
                                            icon = AllIconsKeys.Actions.Find,
                                            label = searchSelectedLabel,
                                        ) {
                                            onEvent(BookContentEvent.SearchInDatabase(query))
                                        },
                                    )
                                }
                                add(
                                    ContextMenuItemOptionWithKeybinding(
                                        icon = TextSearchContextMenuIconKey,
                                        keybinding =
                                            if (hostOs.isMacOS) {
                                                linkedSetOf("⌘", "F")
                                            } else {
                                                linkedSetOf("Ctrl", "F")
                                            },
                                        label = findInPageLabel,
                                    ) {
                                        if (query.isNotBlank()) {
                                            AppSettings.setFindQuery(tabId, query)
                                        }
                                        AppSettings.openFindBar(tabId)
                                    },
                                )
                                // Highlight color picker (last item): persists a position-based
                                // highlight for the selected text on its resolved line.
                                if (selectedText.isNotBlank() && bookId > 0) {
                                    add(
                                        ContextMenuHighlightColorPicker(
                                            colors = HighlightColors.allWithClear,
                                            onColorSelected = { color ->
                                                applyHighlightFromSelection(
                                                    selectedText = selectedText,
                                                    lines = selectionContext.visibleLines.value.lines,
                                                    bookId = bookId,
                                                    color = color,
                                                    showDiacritics = showDiacritics,
                                                    store = highlightStore,
                                                    scope = highlightScope,
                                                )
                                            },
                                            onDismiss = { state.status = ContextMenuState.Status.Closed },
                                        ),
                                    )
                                }
                            }
                        },
                    ) {
                        baseTextContextMenu.Area(
                            textManager = textManager,
                            state = state,
                            content = content,
                        )
                    }
                }
            }
        }

    // Configuration of split panes to monitor
    val splitPaneConfigs =
        listOf(
            SplitPaneConfig(
                splitState = uiState.layout.mainSplitState,
                isVisible = uiState.navigation.isVisible,
                positionFilter = { it > 0 },
            ),
            SplitPaneConfig(
                splitState = uiState.layout.tocSplitState,
                isVisible = uiState.toc.isVisible,
                positionFilter = { it > 0 },
            ),
            SplitPaneConfig(
                splitState = uiState.layout.contentSplitState,
                isVisible = uiState.content.showCommentaries,
                positionFilter = { it > 0 && it < 1 },
            ),
            SplitPaneConfig(
                splitState = uiState.layout.targumSplitState,
                isVisible = uiState.content.showTargum,
                positionFilter = { it > 0 && it < 1 },
            ),
        )

    // Monitor all split panes with the same logic
    splitPaneConfigs.forEach { config ->
        LaunchedEffect(config.splitState, config.isVisible) {
            if (config.isVisible) {
                snapshotFlow { config.splitState.positionPercentage }
                    .map { ((it * 100).roundToInt() / 100f) }
                    .distinctUntilChanged()
                    .debounce(300)
                    .filter(config.positionFilter)
                    .collect { currentOnEvent(BookContentEvent.SaveState) }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { currentOnEvent(BookContentEvent.SaveState) }
    }

    CompositionLocalProvider(
        LocalTextContextMenu provides textContextMenu,
        LocalContextMenuRepresentation provides BookContentContextMenuRepresentationWithKeybindings,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            val isCtrlOrCmd = keyEvent.isCtrlPressed || keyEvent.isMetaPressed
                            when {
                                isCtrlOrCmd && keyEvent.key == Key.B -> {
                                    if (keyEvent.isShiftPressed) {
                                        onEvent(BookContentEvent.ToggleToc)
                                    } else {
                                        onEvent(BookContentEvent.ToggleBookTree)
                                    }
                                    true
                                }
                                isCtrlOrCmd && keyEvent.key == Key.K -> {
                                    if (keyEvent.isShiftPressed) {
                                        onEvent(BookContentEvent.ToggleTargum)
                                    } else {
                                        onEvent(BookContentEvent.ToggleCommentaries)
                                    }
                                    true
                                }
                                isCtrlOrCmd && keyEvent.key == Key.J -> {
                                    onEvent(BookContentEvent.ToggleDiacritics)
                                    true
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    },
        ) {
            StartVerticalBar(uiState = uiState, onEvent = onEvent)

            val isHome = uiState.navigation.selectedBook == null
            val isIslands = ThemeUtils.isIslandsStyle()
            val panelCardModifier =
                if (isIslands) {
                    Modifier
                        .fillMaxSize()
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(JewelTheme.globalColors.panelBackground)
                } else {
                    Modifier
                }

            EnhancedHorizontalSplitPane(
                splitPaneState = uiState.layout.mainSplitState.asStable(),
                modifier = Modifier.weight(1f),
                firstMinSize = if (uiState.navigation.isVisible) SplitDefaults.MIN_MAIN else 0f,
                firstContent = {
                    if (uiState.navigation.isVisible) {
                        CategoryTreePanel(uiState = uiState, onEvent = onEvent, modifier = panelCardModifier)
                    }
                },
                secondContent = {
                    EnhancedHorizontalSplitPane(
                        splitPaneState = uiState.layout.tocSplitState.asStable(),
                        firstMinSize = if (uiState.toc.isVisible) SplitDefaults.MIN_TOC else 0f,
                        firstContent = {
                            if (uiState.toc.isVisible) {
                                BookTocPanel(uiState = uiState, onEvent = onEvent, modifier = panelCardModifier)
                            }
                        },
                        secondContent = {
                            BookContentPanel(
                                uiState = uiState,
                                onEvent = onEvent,
                                showDiacritics = showDiacritics,
                                isRestoringSession = isRestoringSession,
                                searchUi = searchUi,
                                searchCallbacks = searchCallbacks,
                                isSelected = isSelected,
                                bookCharCounts = bookCharCounts,
                            )
                        },
                        showSplitter = uiState.toc.isVisible,
                    )
                },
                showSplitter = uiState.navigation.isVisible,
            )

            if (!isHome) {
                EndVerticalBar(uiState = uiState, onEvent = onEvent, showDiacritics = showDiacritics)
            }
        }
    }
}

/**
 * Represents the configuration used to manage the state and behavior of a split-pane component.
 *
 * @property splitState The state object representing the current split position and related properties.
 * @property isVisible Indicates whether the split-pane is visible or not.
 * @property positionFilter A filter function applied to the split position value to determine its validity.
 */
@Stable
private data class SplitPaneConfig
    @OptIn(ExperimentalSplitPaneApi::class)
    constructor(
        val splitState: SplitPaneState,
        val isVisible: Boolean,
        val positionFilter: (Float) -> Boolean,
    )

private fun normalizeSearchQuery(text: String): String {
    val normalizedLineBreaks = text.replace('\n', ' ').replace('\r', ' ')
    return normalizedLineBreaks.replace(Regex("\\s+"), " ").trim()
}
