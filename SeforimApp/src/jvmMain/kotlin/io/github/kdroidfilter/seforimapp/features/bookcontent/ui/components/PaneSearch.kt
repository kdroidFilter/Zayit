package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.icons.AllIconsKeys

private const val SEARCH_DEBOUNCE_MS = 120L

/** Header action showing or hiding the search bar of a pane. */
@Composable
fun PaneSearchButton(
    onClick: () -> Unit,
    contentDescription: String,
) {
    IconActionButton(key = AllIconsKeys.Actions.Find, onClick = onClick, contentDescription = contentDescription)
}

/**
 * The search bar of a side pane, focused on open, above its tree: [regular] (the regular tree)
 * while the query is shorter than [minQueryLength], [results] otherwise. Enter opens the
 * active match ([onSelect]), ↑/↓ move it, Escape closes the bar.
 */
@Composable
fun <T : PaneSearchResult> PaneSearch(
    search: PaneSearchState<T>,
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
    val result = search.result
    var activeId by remember(result) { mutableStateOf(result?.bestMatchId) }
    // Enter hit before the result of the typed text arrived: open its best match once it does
    var pendingSubmit by remember { mutableStateOf<String?>(null) }
    val currentOnSelect by rememberUpdatedState(onSelect)
    LaunchedEffect(search.resultQuery, result) {
        val pending = pendingSubmit ?: return@LaunchedEffect
        if (search.resultQuery == pending) {
            pendingSubmit = null
            result?.bestMatchId?.let(currentOnSelect)
        }
    }

    Column(modifier = modifier) {
        PaneSearchField(
            query = search.query,
            placeholder = placeholder,
            onQueryChange = onQueryChange,
            onSubmit = { text ->
                if (search.resultQuery == text) {
                    activeId?.let(onSelect)
                } else {
                    pendingSubmit = text
                    // Skip the debounce
                    if (search.query != text) onQueryChange(text)
                }
            },
            onMove = { delta ->
                val ids = result?.matchIds.orEmpty()
                if (ids.isNotEmpty()) activeId = ids[(ids.indexOf(activeId) + delta).coerceIn(0, ids.lastIndex)]
            },
            onClose = onClose,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            when {
                search.query.trim().length < minQueryLength -> regular()
                // First result still computing
                result == null -> Unit
                // Only claim "nothing found" for the query actually typed
                result.matchIds.isEmpty() && search.resultQuery == search.query ->
                    Text(
                        text = noResults,
                        color = JewelTheme.globalColors.text.disabled,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
                    )
                result.matchIds.isEmpty() -> Unit
                else -> results(result, activeId)
            }
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun PaneSearchField(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    onSubmit: (text: String) -> Unit,
    onMove: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fieldState = rememberTextFieldState(query)
    val focusRequester = remember { FocusRequester() }
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)

    // Focus a freshly opened bar only: a bar rebuilt with its query (tab switch) must not
    // steal the focus from the reader
    LaunchedEffect(Unit) { if (query.isEmpty()) focusRequester.requestFocus() }
    // Edits are debounced; clearing is immediate
    LaunchedEffect(fieldState) {
        snapshotFlow { fieldState.text.toString() }
            .distinctUntilChanged()
            .drop(1)
            .debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }
            .collect { currentOnQueryChange(it) }
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
                            Key.Enter, Key.NumPadEnter -> onSubmit(fieldState.text.toString())
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
