package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.panels.categorytree

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.models.BookContentUiState
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import seforimapp.composeapp.generated.resources.Res
import seforimapp.composeapp.generated.resources.search_placeholder

@Composable
fun CategoryTreePanel(
    uiState: BookContentUiState,
    onEvent: (BookContentEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        SearchField(
            searchText = uiState.navigation.searchText,
            onSearchTextChange = { onEvent(BookContentEvent.SearchTextChanged(it)) }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        val windowInfo = LocalWindowInfo.current
        CategoryBookTreeView(
            navigationState = uiState.navigation,
            onCategoryClick = { onEvent(BookContentEvent.CategorySelected(it)) },
            onBookClick = {
                val mods = windowInfo.keyboardModifiers
                if (mods.isCtrlPressed || mods.isMetaPressed) {
                    onEvent(BookContentEvent.BookSelectedInNewTab(it))
                } else {
                    onEvent(BookContentEvent.BookSelected(it))
                }
            },
            onScroll = { index, offset -> onEvent(BookContentEvent.BookTreeScrolled(index, offset)) }
        )
    }
}

@Composable
private fun SearchField(
    searchText: String,
    onSearchTextChange: (String) -> Unit
) {
    val textFieldState = rememberTextFieldState(searchText)
    
    LaunchedEffect(searchText) {
        if (textFieldState.text.toString() != searchText) {
            textFieldState.edit { replace(0, length, searchText) }
        }
    }
    
    LaunchedEffect(textFieldState.text) {
        onSearchTextChange(textFieldState.text.toString())
    }
    
    TextField(
        state = textFieldState,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(Res.string.search_placeholder)) }
    )
}