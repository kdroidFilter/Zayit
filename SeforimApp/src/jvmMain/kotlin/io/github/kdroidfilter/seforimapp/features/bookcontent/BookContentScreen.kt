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
import io.github.kdroidfilter.seforimapp.core.resolveLineRangeFromSelection
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookTabUi
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.BookContentPanel
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.HomeSearchCallbacks
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.notes.NoteDraftAnchor
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeUiState
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.Ink_pen
import io.github.kdroidfilter.seforimlibrary.core.text.HebrewTextUtils
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
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
import seforimapp.seforimapp.generated.resources.context_menu_add_note
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
import androidx.compose.foundation.ContextMenuRepresentation as ComposeContextMenuRepresentation
import org.jetbrains.jewel.ui.component.ContextMenuRepresentation as JewelContextMenuRepresentation
import org.jetbrains.jewel.ui.component.TextContextMenu as JewelTextContextMenu

private val TextSearchContextMenuIconKey = PathIconKey("icons/lucide_text_search.svg", BookContentViewModel::class.java)

private class ContextMenuItemOptionWithKeybinding(
    val icon: org.jetbrains.jewel.ui.icon.IconKey? = null,
    val keybinding: Set<String>? = null,
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
 * Resolves the selected text to (line, offset range) pairs and persists/clears highlights.
 * Offsets are computed against plain text WITH diacritics so they stay valid regardless of the
 * current diacritics setting.
 *
 * [commentaryColumn] is the discriminator: it holds the visible commentary lines of the column
 * under the last right-click (set in the comments pane, cleared on a main-pane right-click), so a
 * non-empty value means the selection is in the comments pane. Comments-pane highlights are
 * stored against the commentary's own book, so they also show when that commentary is opened as a
 * main book. The selection may span several consecutive commentary entries. No-op if unresolved.
 */
private fun applyHighlightFromSelection(
    selectedText: String,
    lines: List<io.github.kdroidfilter.seforimlibrary.core.models.Line>,
    bookId: Long,
    commentaryColumn: List<io.github.kdroidfilter.seforimapp.core.selection.CommentaryLineRef>,
    color: Color,
    showDiacritics: Boolean,
    store: HighlightStore,
    @StructuredScope scope: CoroutineScope,
) {
    // Comments pane: resolve over the column's visible lines (in display order). A column is a
    // single commentator, so all its lines share one target book.
    if (commentaryColumn.isNotEmpty()) {
        val commentaryBookId = commentaryColumn.first().bookId
        val sorted = commentaryColumn.map { it.lineId to buildAnnotatedFromHtml(it.content, baseTextSize = 16f, boldScale = 1f).text }
        val ranges = resolveHighlightRangesForSelection(sorted, selectedText, showDiacritics)
        persistHighlights(commentaryBookId, ranges, color, store, scope)
        return
    }

    // Main book: all materialized lines (ordered, plain text WITH diacritics so offsets match
    // what the renderer applies). The resolver splits the selection per line and anchors it.
    val mainSorted =
        lines
            .sortedBy { it.lineIndex }
            .map { it.id to buildAnnotatedFromHtml(it.content, baseTextSize = 16f, boldScale = 1f).text }
    val mainRanges = resolveHighlightRangesForSelection(mainSorted, selectedText, showDiacritics)
    persistHighlights(bookId, mainRanges, color, store, scope)
}

private fun persistHighlights(
    bookId: Long,
    ranges: List<io.github.kdroidfilter.seforimapp.core.annotations.LineHighlightRange>,
    color: Color,
    store: HighlightStore,
    @StructuredScope scope: CoroutineScope,
) {
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

/**
 * Resolves the selected text to a single note anchor on a main-pane line. Notes are only created
 * from the main book pane (not the comments pane), so the resolved line id matches the book the
 * notes pane is keyed to. Returns null when the selection cannot be located.
 */
private fun resolveNoteDraft(
    selectedText: String,
    lines: List<io.github.kdroidfilter.seforimlibrary.core.models.Line>,
    showDiacritics: Boolean,
): NoteDraftAnchor? {
    val sorted =
        lines
            .sortedBy { it.lineIndex }
            .map { it.id to buildAnnotatedFromHtml(it.content, baseTextSize = 16f, boldScale = 1f).text }
    val range = resolveHighlightRangesForSelection(sorted, selectedText, showDiacritics).firstOrNull() ?: return null
    return NoteDraftAnchor(
        lineId = range.lineId,
        startOffset = range.range.first,
        endOffset = range.range.last + 1,
        quote = selectedText.trim().substringBefore('\n').trim(),
    )
}

/** Builds a note anchor covering the whole right-clicked line (used when no text is selected). */
private fun resolveWholeLineNoteDraft(
    lineId: Long,
    lines: List<io.github.kdroidfilter.seforimlibrary.core.models.Line>,
): NoteDraftAnchor? {
    if (lineId <= 0) return null
    val line = lines.firstOrNull { it.id == lineId } ?: return null
    val plain = buildAnnotatedFromHtml(line.content, baseTextSize = 16f, boldScale = 1f).text
    if (plain.isEmpty()) return null
    return NoteDraftAnchor(lineId = lineId, startOffset = 0, endOffset = plain.length, quote = plain.trim())
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
 * The text context menu of everything drawing [uiState]'s book — the text itself and the dock panes
 * (commentaries, links, notes): copy without nikud, copy with source, share link, search, find in
 * page, add note, highlight. [tabUi] carries the note draft the "add note" action opens.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookTextMenus(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    showDiacritics: Boolean,
    tabUi: BookTabUi,
    content: @Composable () -> Unit,
) {
    val searchSelectedLabel = stringResource(Res.string.context_menu_search_selected_text)
    val findInPageLabel = stringResource(Res.string.context_menu_find_in_page)
    val copyWithoutNikudLabel = stringResource(Res.string.context_menu_copy_without_nikud)
    val copyWithSourceLabel = stringResource(Res.string.context_menu_copy_with_source)
    val copyLinkLabel = stringResource(Res.string.context_menu_copy_link)
    val addNoteLabel = stringResource(Res.string.context_menu_add_note)
    val baseTextContextMenu = LocalTextContextMenu.current
    val tabId = uiState.tabId
    val selectedBook = uiState.navigation.selectedBook
    val bookHasDiacritics = selectedBook?.hasNekudot == true || selectedBook?.hasTeamim == true
    val selectionContext = LocalAppGraph.current.selectionContext
    // Always read the latest values inside the context-menu actions, without rebuilding the menu.
    val currentSelectedBook by rememberUpdatedState(selectedBook)
    val currentNotesVisible by rememberUpdatedState(uiState.notes.isVisible)
    val currentPrimaryLineId by rememberUpdatedState(uiState.content.primaryLine?.id)
    val highlightStore = LocalAppGraph.current.highlightStore
    val highlightScope = rememberCoroutineScope()
    val bookId = selectedBook?.id ?: 0L

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
                    val appSettings = LocalAppGraph.current.appSettings
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
                                            appSettings.setFindQuery(tabId, query)
                                        }
                                        appSettings.openFindBar(tabId)
                                    },
                                )
                                // Add note (main pane only): anchors a note to the selected text,
                                // or to the whole right-clicked line when nothing is selected.
                                if (bookId > 0 &&
                                    selectionContext.activeCommentaryColumn.value
                                        .isEmpty() &&
                                    (selectedText.isNotBlank() || selectionContext.currentLineId.value > 0)
                                ) {
                                    add(
                                        ContextMenuItemOptionWithKeybinding(
                                            icon = AllIconsKeys.Actions.Annotate,
                                            label = addNoteLabel,
                                        ) {
                                            val lines = selectionContext.visibleLines.value.lines
                                            val draft =
                                                if (selectedText.isNotBlank()) {
                                                    resolveNoteDraft(selectedText, lines, showDiacritics)
                                                } else {
                                                    resolveWholeLineNoteDraft(selectionContext.currentLineId.value, lines)
                                                }
                                            if (draft != null) {
                                                tabUi.noteDraft = draft
                                                tabUi.noteDraftBaselineLine = currentPrimaryLineId
                                                if (!currentNotesVisible) onEvent(BookContentEvent.ToggleNotes)
                                            }
                                        },
                                    )
                                }
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
                                                    commentaryColumn = selectionContext.activeCommentaryColumn.value,
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

    CompositionLocalProvider(
        LocalTextContextMenu provides textContextMenu,
        LocalContextMenuRepresentation provides BookContentContextMenuRepresentationWithKeybindings,
        content = content,
    )
}

/**
 * Restores Jewel's default text context menu (copy/cut/paste) for editable fields inside a
 * book screen, where [BookTextMenus] (copy link, highlight, add note…) is irrelevant. Both the
 * items and the representation are reset, since [BookTextMenus] overrides both.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlainTextMenus(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalTextContextMenu provides JewelTextContextMenu,
        LocalContextMenuRepresentation provides JewelContextMenuRepresentation,
        content = content,
    )
}

/**
 * The text of a book tab: the book (or Home when none is open) with its breadcrumb. Its panes —
 * tree, contents, notes, links, commentaries, sources — are dock satellites of the window
 * (see `ReaderPanes`), drawing the same ViewModel.
 */
@OptIn(FlowPreview::class, ExperimentalFoundationApi::class)
@Composable
fun BookContentScreen(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    showDiacritics: Boolean,
    searchUi: SearchHomeUiState,
    searchCallbacks: HomeSearchCallbacks,
    tabUi: BookTabUi,
    isRestoringSession: Boolean = false,
    isSelected: Boolean = true,
    bookCharCounts: IntArray? = null,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    val tabId = uiState.tabId
    val selectedBook = uiState.navigation.selectedBook
    val selectionContext = LocalAppGraph.current.selectionContext
    val catalogCache = LocalAppGraph.current.catalogCache

    // Selecting a different line drops the unsaved explicit note draft so the editor reflects the
    // newly selected line instead of the stale anchor.
    LaunchedEffect(uiState.content.primaryLine?.id) {
        if (tabUi.noteDraft != null && uiState.content.primaryLine?.id != tabUi.noteDraftBaselineLine) {
            tabUi.noteDraft = null
        }
    }

    // Publish the active book + its root category to the SelectionContext so the Ctrl+Alt+C
    // dispatcher and the context-menu action can apply per-tradition formatting. Lifecycle
    // clears are tabId-scoped so a backgrounded or disposed tab cannot wipe the foreground
    // tab's published book — even when both tabs happen to reference the same book.
    LaunchedEffect(selectedBook, isSelected, tabId, selectionContext) {
        if (isSelected) {
            val rootTitle = selectedBook?.let { catalogCache.getRootForBook(it)?.title }
            selectionContext.setActiveBook(tabId, selectedBook, rootTitle)
        } else {
            selectionContext.clearActiveBookIfOwnedBy(tabId)
        }
    }
    DisposableEffect(tabId, selectionContext) {
        onDispose { selectionContext.clearActiveBookIfOwnedBy(tabId) }
    }

    DisposableEffect(Unit) {
        onDispose { currentOnEvent(BookContentEvent.SaveState) }
    }

    BookTextMenus(uiState = uiState, onEvent = onEvent, showDiacritics = showDiacritics, tabUi = tabUi) {
        BookContentPanel(
            uiState = uiState,
            onEvent = onEvent,
            showDiacritics = showDiacritics,
            isRestoringSession = isRestoringSession,
            searchUi = searchUi,
            searchCallbacks = searchCallbacks,
            isSelected = isSelected,
            bookCharCounts = bookCharCounts,
            noteDraft = tabUi.noteDraft,
            tabUi = tabUi,
        )
    }
}

/** Book-view shortcuts: Ctrl/Cmd+B tree (Shift: contents), Ctrl/Cmd+K commentaries (Shift: links), Ctrl/Cmd+J nikud. */
fun handleBookShortcut(
    keyEvent: KeyEvent,
    onEvent: (BookContentEvent) -> Unit,
): Boolean {
    if (keyEvent.type != KeyEventType.KeyDown) return false
    val isCtrlOrCmd = keyEvent.isCtrlPressed || keyEvent.isMetaPressed
    if (!isCtrlOrCmd) return false
    val event =
        when (keyEvent.key) {
            Key.B -> if (keyEvent.isShiftPressed) BookContentEvent.ToggleToc else BookContentEvent.ToggleBookTree
            Key.K -> if (keyEvent.isShiftPressed) BookContentEvent.ToggleTargum else BookContentEvent.ToggleCommentaries
            Key.J -> BookContentEvent.ToggleDiacritics
            else -> return false
        }
    onEvent(event)
    return true
}

private fun normalizeSearchQuery(text: String): String {
    val normalizedLineBreaks = text.replace('\n', ' ').replace('\r', ' ')
    return normalizedLineBreaks.replace(Regex("\\s+"), " ").trim()
}
