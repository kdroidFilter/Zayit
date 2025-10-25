package io.github.kdroidfilter.seforimapp.features.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.kdroidfilter.seforim.navigation.Navigator
import io.github.kdroidfilter.seforim.tabs.TabAwareViewModel
import io.github.kdroidfilter.seforim.tabs.TabStateManager
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforim.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.StateKeys
import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val near: Int = 5,
    val isLoading: Boolean = false,
    val results: List<SearchResult> = emptyList()
)

class SearchResultViewModel(
    savedStateHandle: SavedStateHandle,
    private val stateManager: TabStateManager,
    private val repository: SeforimRepository,
    private val navigator: Navigator,
    private val titleUpdateManager: TabTitleUpdateManager
) : TabAwareViewModel(
    tabId = savedStateHandle.get<String>(StateKeys.TAB_ID) ?: "",
    stateManager = stateManager
) {
    private val tabId: String = savedStateHandle.get<String>(StateKeys.TAB_ID) ?: ""

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        val initialQuery = savedStateHandle.get<String>("searchQuery")
            ?: stateManager.getState<String>(tabId, SearchStateKeys.QUERY)
            ?: ""
        val initialNear = stateManager.getState<Int>(tabId, SearchStateKeys.NEAR) ?: 5
        _uiState.value = _uiState.value.copy(query = initialQuery, near = initialNear)

        // Update tab title to the query (TabsViewModel also handles initial title)
        if (initialQuery.isNotBlank()) {
            titleUpdateManager.updateTabTitle(tabId, initialQuery, TabType.SEARCH)
            executeSearch()
        }
    }

    fun setNear(near: Int) {
        _uiState.value = _uiState.value.copy(near = near)
        stateManager.saveState(tabId, SearchStateKeys.NEAR, near)
    }

    fun executeSearch() {
        val q = _uiState.value.query.trim()
        if (q.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            stateManager.saveState(tabId, SearchStateKeys.QUERY, q)
            try {
                val fts = buildNearQuery(q, _uiState.value.near)
                val results = repository.searchWithOperators(fts, limit = 200)
                _uiState.value = _uiState.value.copy(results = results)
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun openResult(result: SearchResult) {
        viewModelScope.launch {
            // Pre-initialize new BookContent tab with selected book and anchor to reduce flicker
            val newTabId = java.util.UUID.randomUUID().toString()
            repository.getBook(result.bookId)?.let { book ->
                stateManager.saveState(newTabId, StateKeys.SELECTED_BOOK, book)
            }
            stateManager.saveState(newTabId, StateKeys.CONTENT_ANCHOR_ID, result.lineId)

            navigator.navigate(
                TabsDestination.BookContent(
                    bookId = result.bookId,
                    tabId = newTabId,
                    lineId = result.lineId
                )
            )
        }
    }

    private fun buildNearQuery(raw: String, near: Int): String {
        // Split on whitespace and drop empty tokens
        val tokens = raw.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return ""
        if (tokens.size == 1) return sanitize(tokens.first()) + "*"
        // FTS5 NEAR syntax: NEAR(term1 term2 ..., distance)
        val inner = tokens.joinToString(" ") { sanitize(it) + "*" }
        return "NEAR($inner, $near)"
    }

    private fun sanitize(term: String): String {
        return term.replace('"', ' ').trim()
    }
}
