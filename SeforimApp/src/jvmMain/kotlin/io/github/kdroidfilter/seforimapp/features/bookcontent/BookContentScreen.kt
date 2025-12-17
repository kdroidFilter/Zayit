package io.github.kdroidfilter.seforimapp.features.bookcontent

import androidx.compose.foundation.ContextMenuDataProvider
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.foundation.ContextMenuRepresentation as ComposeContextMenuRepresentation
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.LocalTextContextMenu
import androidx.compose.foundation.text.TextContextMenu
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.nativeKeyCode
import com.dokar.sonner.ToastType
import com.dokar.sonner.rememberToasterState
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.EndVerticalBar
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.EnhancedHorizontalSplitPane
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.asStable
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.StartVerticalBar
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.BookContentPanel
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.booktoc.BookTocPanel
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.categorytree.CategoryTreePanel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.SplitPaneState
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.SplitDefaults
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.ContextMenuItemOption
import org.jetbrains.jewel.ui.icon.PathIconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.max_commentators_limit
import io.github.kdroidfilter.seforimapp.core.presentation.components.AppToaster
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeUiState
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.HomeSearchCallbacks
import seforimapp.seforimapp.generated.resources.context_menu_find_in_page
import seforimapp.seforimapp.generated.resources.context_menu_search_selected_text
import java.awt.event.InputEvent
import javax.swing.KeyStroke
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultMenuController
import org.jetbrains.jewel.ui.component.LocalMenuController
import org.jetbrains.jewel.ui.component.MenuContent
import org.jetbrains.jewel.ui.component.MenuController
import org.jetbrains.jewel.ui.component.MenuScope
import org.jetbrains.jewel.ui.component.Popup
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.ui.component.styling.MenuStyle
import org.jetbrains.jewel.ui.theme.menuStyle
import org.jetbrains.skiko.hostOs

private val TextSearchContextMenuIconKey = PathIconKey("icons/lucide_text_search.svg", BookContentViewModel::class.java)

private class ContextMenuItemOptionWithKeybinding(
    val icon: org.jetbrains.jewel.ui.icon.IconKey? = null,
    val keybinding: Set<String>? = null,
    val enabled: Boolean = true,
    label: String,
    action: () -> Unit,
) : ContextMenuItem(label, action)

@OptIn(InternalJewelApi::class)
private object BookContentContextMenuRepresentationWithKeybindings : ComposeContextMenuRepresentation {
    @Composable
    override fun Representation(state: ContextMenuState, items: () -> List<ContextMenuItem>) {
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
        popupPositionProvider = androidx.compose.ui.window.rememberCursorPositionProvider(style.metrics.offset),
        onDismissRequest = { onDismissRequest(InputMode.Touch) },
        properties = androidx.compose.ui.window.PopupProperties(focusable = focusable),
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
 * Composable function to display the book content screen.
 *
 * This screen observes the `uiState` from the `BookContentViewModel` and passes
 * it to the `BookContentView` composable for rendering. It also provides the
 * `onEvent` lambda from the ViewModel to handle user interactions within the
 * `BookContentView`.
 */
@Composable
fun BookContentScreen(
    viewModel: BookContentViewModel,
    isRestoringSession: Boolean = false,
    searchUi: SearchHomeUiState,
    searchCallbacks: HomeSearchCallbacks,
) {
    val uiState by viewModel.uiState.collectAsState()

    BookContentView(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        isRestoringSession = isRestoringSession,
        searchUi = searchUi,
        searchCallbacks = searchCallbacks
    )
}

/**
 * Displays the content view of a book with multiple panels configured within split panes.
 *
 * @param uiState The complete UI state used for rendering the book content screen, capturing navigation, TOC, content display, layout management, and more.
 * @param onEvent Function that handles various user-driven events or state updates within the book content view.
 */
@OptIn(ExperimentalSplitPaneApi::class, FlowPreview::class, ExperimentalFoundationApi::class)
@Composable
fun BookContentView(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    isRestoringSession: Boolean = false,
    searchUi: SearchHomeUiState,
    searchCallbacks: HomeSearchCallbacks,
) {
    // Toaster for transient messages (e.g., selection limits)
    val toaster = rememberToasterState()
    val searchSelectedLabel = stringResource(Res.string.context_menu_search_selected_text)
    val findInPageLabel = stringResource(Res.string.context_menu_find_in_page)
    val baseTextContextMenu = LocalTextContextMenu.current
    val tabId = uiState.tabId

    val textContextMenu = remember(baseTextContextMenu, tabId, onEvent, searchSelectedLabel, findInPageLabel) {
        object : TextContextMenu {
            @OptIn(ExperimentalFoundationApi::class)
            @Composable
            override fun Area(
                textManager: TextContextMenu.TextManager,
                state: ContextMenuState,
                content: @Composable () -> Unit
            ) {
                ContextMenuDataProvider(
                    items = {
                        val query = normalizeSearchQuery(textManager.selectedText.text)
                        buildList {
                            if (query.isNotBlank()) {
                                add(
                                    ContextMenuItemOption(
                                        icon = AllIconsKeys.Actions.Find,
                                        label = searchSelectedLabel
                                    ) {
                                        onEvent(BookContentEvent.SearchInDatabase(query))
                                    }
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
                                    label = findInPageLabel
                                ) {
                                    if (query.isNotBlank()) {
                                        AppSettings.setFindQuery(tabId, query)
                                    }
                                    AppSettings.openFindBar(tabId)
                                }
                            )
                        }
                    }
                ) {
                    baseTextContextMenu.Area(
                        textManager = textManager,
                        state = state,
                        content = content
                    )
                }
            }
        }
    }

    // Configuration of split panes to monitor
    val splitPaneConfigs = listOf(
        SplitPaneConfig(
            splitState = uiState.layout.mainSplitState,
            isVisible = uiState.navigation.isVisible,
            positionFilter = { it > 0 }
        ),
        SplitPaneConfig(
            splitState = uiState.layout.tocSplitState,
            isVisible = uiState.toc.isVisible,
            positionFilter = { it > 0 }
        ),
        SplitPaneConfig(
            splitState = uiState.layout.contentSplitState,
            isVisible = uiState.content.showCommentaries,
            positionFilter = { it > 0 && it < 1 }
        ),
        SplitPaneConfig(
            splitState = uiState.layout.targumSplitState,
            isVisible = uiState.content.showTargum,
            positionFilter = { it > 0 && it < 1 }
        )
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
                    .collect { onEvent(BookContentEvent.SaveState) }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { onEvent(BookContentEvent.SaveState) }
    }

    CompositionLocalProvider(
        LocalTextContextMenu provides textContextMenu,
        LocalContextMenuRepresentation provides BookContentContextMenuRepresentationWithKeybindings,
    ) {
        Row(
            modifier = Modifier
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
                            else -> false
                        }
                    } else {
                        false
                    }
                }
        ) {
            StartVerticalBar(uiState = uiState, onEvent = onEvent)

            EnhancedHorizontalSplitPane(
                splitPaneState = uiState.layout.mainSplitState.asStable(),
                modifier = Modifier.weight(1f),
                firstMinSize = if (uiState.navigation.isVisible) SplitDefaults.MIN_MAIN else 0f,
                firstContent = {
                    if (uiState.navigation.isVisible) {
                        CategoryTreePanel(uiState = uiState, onEvent = onEvent)
                    }
                },
                secondContent = {
                    EnhancedHorizontalSplitPane(
                        splitPaneState = uiState.layout.tocSplitState.asStable(),
                        firstMinSize = if (uiState.toc.isVisible) SplitDefaults.MIN_TOC else 0f,
                        firstContent = {
                            if (uiState.toc.isVisible) {
                                BookTocPanel(uiState = uiState, onEvent = onEvent)
                            }
                        },
                        secondContent = {
                            BookContentPanel(
                                uiState = uiState,
                                onEvent = onEvent,
                                isRestoringSession = isRestoringSession,
                                searchUi = searchUi,
                                searchCallbacks = searchCallbacks
                            )
                        },
                        showSplitter = uiState.toc.isVisible
                    )
                },
                showSplitter = uiState.navigation.isVisible
            )

            EndVerticalBar(uiState = uiState, onEvent = onEvent)
        }
    }

    // Render toaster overlay themed like Jewel (reusable)
    AppToaster(state = toaster)

    // React to state mutations to show a toast (no callbacks)
    val maxLimitMsg = stringResource(Res.string.max_commentators_limit)
    LaunchedEffect(uiState.content.maxCommentatorsLimitSignal) {
        if (uiState.content.maxCommentatorsLimitSignal > 0L) {
            toaster.show(
                message = maxLimitMsg,
                type = ToastType.Warning,

            )
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
private data class SplitPaneConfig @OptIn(ExperimentalSplitPaneApi::class) constructor(
    val splitState: SplitPaneState,
    val isVisible: Boolean,
    val positionFilter: (Float) -> Boolean
)

private fun normalizeSearchQuery(text: String): String {
    val normalizedLineBreaks = text.replace('\n', ' ').replace('\r', ' ')
    return normalizedLineBreaks.replace(Regex("\\s+"), " ").trim()
}
