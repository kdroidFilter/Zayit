package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.features.bookcontent.PlainTextMenus
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.PaneSearchResult
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.PaneSearchState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.onEach
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.icons.AllIconsKeys

private const val SEARCH_DEBOUNCE_MS = 120L

/**
 * Whether the search field should take the focus: set when the user opens the bar, consumed
 * by the field. A bar rebuilt on its own (tab switch, re-docked pane) leaves the focus alone.
 */
@Stable
class PaneSearchFocus {
    internal var requested by mutableStateOf(false)
}

@Composable
fun rememberPaneSearchFocus(): PaneSearchFocus = remember { PaneSearchFocus() }

/** Header action showing or hiding the search bar of a pane. */
@Composable
fun PaneSearchButton(
    isOpen: Boolean,
    focus: PaneSearchFocus,
    onClick: () -> Unit,
    contentDescription: String,
) {
    IconActionButton(
        key = AllIconsKeys.Actions.Find,
        onClick = {
            focus.requested = !isOpen
            onClick()
        },
        contentDescription = contentDescription,
    )
}

/**
 * A side pane's tree under its optional search bar ([search] null: hidden): [regular] (the
 * regular tree) while the query is shorter than [minQueryLength], [results] otherwise. The
 * regular tree keeps one place in the composition, so opening or closing the bar leaves its
 * scroll alone. Enter opens the active match ([onSelect]), ↑/↓ move it, Escape closes the bar.
 */
@Composable
fun <T : PaneSearchResult> PaneSearchLayout(
    search: PaneSearchState<T>?,
    focus: PaneSearchFocus,
    placeholder: String,
    noResults: String,
    onQueryChange: (String) -> Unit,
    onSelect: (Long) -> Unit,
    onClose: () -> Unit,
    regular: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    minQueryLength: Int = 1,
    results: @Composable (result: T, activeId: Long?) -> Unit,
) {
    val result = search?.result
    var activeId by remember(result) { mutableStateOf(result?.bestMatchId) }
    // Enter hit before the result of the typed text arrived: open its best match once it does,
    // unless the text changed meanwhile
    var pendingSubmit by remember { mutableStateOf<String?>(null) }
    val currentOnSelect by rememberUpdatedState(onSelect)
    LaunchedEffect(search?.resultQuery, result) {
        val pending = pendingSubmit ?: return@LaunchedEffect
        if (search?.resultQuery == pending) {
            pendingSubmit = null
            result?.bestMatchId?.let(currentOnSelect)
        }
    }

    Column(modifier = modifier) {
        if (search != null) {
            PaneSearchField(
                query = search.query,
                focus = focus,
                placeholder = placeholder,
                onTextChange = { text -> if (text != pendingSubmit) pendingSubmit = null },
                onQueryChange = onQueryChange,
                onSubmit = { text ->
                    if (search.resultQuery == text) activeId?.let(onSelect) else pendingSubmit = text
                },
                onMove = { delta ->
                    val ids = result?.matchIds.orEmpty()
                    if (ids.isNotEmpty()) activeId = ids[(ids.indexOf(activeId) + delta).coerceIn(0, ids.lastIndex)]
                },
                onClose = onClose,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            if (search == null || search.query.trim().length < minQueryLength) {
                regular()
            } else {
                when {
                    // First result still computing
                    result == null -> Unit
                    result.matchIds.isNotEmpty() -> results(result, activeId)
                    // Only claim "nothing found" for the query actually typed
                    search.resultQuery == search.query ->
                        Text(
                            text = noResults,
                            color = JewelTheme.globalColors.text.disabled,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
                        )
                }
            }
        }
    }
}

/** Scrolls the least needed to show item [index] whole; not at all when it already is. */
suspend fun LazyListState.revealItem(index: Int) {
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
    when {
        item == null || item.offset < info.viewportStartOffset -> scrollToItem(index)
        item.offset + item.size > info.viewportEndOffset ->
            scrollBy((item.offset + item.size - info.viewportEndOffset).toFloat())
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun PaneSearchField(
    query: String,
    focus: PaneSearchFocus,
    placeholder: String,
    onTextChange: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onSubmit: (text: String) -> Unit,
    onMove: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fieldState = rememberTextFieldState(query)
    val focusRequester = remember { FocusRequester() }
    val currentOnTextChange by rememberUpdatedState(onTextChange)
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)
    // The last query handed over, to flush what the debounce still holds when the field goes
    // away (tab switch, re-docked pane): the rebuilt field starts from the handed-over query
    val sentQuery = remember { mutableStateOf(query) }
    val send = { text: String ->
        sentQuery.value = text
        currentOnQueryChange(text)
    }
    DisposableEffect(fieldState) {
        onDispose {
            val text = fieldState.text.toString()
            if (text != sentQuery.value) send(text)
        }
    }

    LaunchedEffect(focus.requested) {
        if (focus.requested) {
            focusRequester.requestFocus()
            focus.requested = false
        }
    }
    // Edits are debounced; clearing is immediate
    LaunchedEffect(fieldState) {
        snapshotFlow { fieldState.text.toString() }
            .distinctUntilChanged()
            .drop(1)
            .onEach { currentOnTextChange(it) }
            .debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }
            .collect { if (it != sentQuery.value) send(it) }
    }

    // The book text menu (copy link, highlight…) is inherited from the pane: not for a search field
    PlainTextMenus {
        TextField(
            state = fieldState,
            modifier =
                modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Enter, Key.NumPadEnter -> {
                                val text = fieldState.text.toString()
                                // Skip the debounce
                                if (text != sentQuery.value) send(text)
                                onSubmit(text)
                            }
                            Key.DirectionDown -> onMove(1)
                            Key.DirectionUp -> onMove(-1)
                            Key.Escape -> onClose()
                            else -> return@onPreviewKeyEvent false
                        }
                        true
                    },
            placeholder = { Text(placeholder) },
            textStyle = TextStyle(fontSize = 13.sp),
        )
    }
}
