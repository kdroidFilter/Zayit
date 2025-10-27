package io.github.kdroidfilter.seforimapp.features.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.kdroidfilter.seforim.navigation.Navigator
import io.github.kdroidfilter.seforim.tabs.TabAwareViewModel
import io.github.kdroidfilter.seforim.tabs.TabStateManager
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforim.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.StateKeys
import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class SearchUiState(
    val query: String = "",
    val near: Int = 5,
    val isLoading: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    val scopeCategoryPath: List<Category> = emptyList(),
    val scopeBook: Book? = null,
    // Scroll/anchor persistence
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
    val anchorId: Long = -1L,
    val anchorIndex: Int = 0,
    val scrollToAnchorTimestamp: Long = 0L,
    val textSize: Float = AppSettings.DEFAULT_TEXT_SIZE,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false
)

class SearchResultViewModel(
    savedStateHandle: SavedStateHandle,
    private val stateManager: TabStateManager,
    private val repository: SeforimRepository,
    private val navigator: Navigator,
    private val titleUpdateManager: TabTitleUpdateManager,
    private val cache: SearchResultsCache,
    private val tabsViewModel: TabsViewModel
) : TabAwareViewModel(
    tabId = savedStateHandle.get<String>(StateKeys.TAB_ID) ?: "",
    stateManager = stateManager
) {
    private val tabId: String = savedStateHandle.get<String>(StateKeys.TAB_ID) ?: ""

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    private var currentJob: kotlinx.coroutines.Job? = null

    // Pagination cursors/state
    private var currentKey: SearchParamsKey? = null
    private var nextOffset: Int = 0 // for global/book
    private var allowedBooks: List<Long> = emptyList()
    private val perBookOffset: MutableMap<Long, Int> = mutableMapOf()

    init {
        val initialQuery = savedStateHandle.get<String>("searchQuery")
            ?: stateManager.getState<String>(tabId, SearchStateKeys.QUERY)
            ?: ""
        val initialNear = stateManager.getState<Int>(tabId, SearchStateKeys.NEAR) ?: 5
        val initialScrollIndex = stateManager.getState<Int>(tabId, SearchStateKeys.SCROLL_INDEX) ?: 0
        val initialScrollOffset = stateManager.getState<Int>(tabId, SearchStateKeys.SCROLL_OFFSET) ?: 0
        val initialAnchorId = stateManager.getState<Long>(tabId, SearchStateKeys.ANCHOR_ID) ?: -1L
        val initialAnchorIndex = stateManager.getState<Int>(tabId, SearchStateKeys.ANCHOR_INDEX) ?: 0
        _uiState.value = _uiState.value.copy(
            query = initialQuery,
            near = initialNear,
            scrollIndex = initialScrollIndex,
            scrollOffset = initialScrollOffset,
            anchorId = initialAnchorId,
            anchorIndex = initialAnchorIndex,
            textSize = AppSettings.getTextSize()
        )

        // Update tab title to the query (TabsViewModel also handles initial title)
        if (initialQuery.isNotBlank()) {
            titleUpdateManager.updateTabTitle(tabId, initialQuery, TabType.SEARCH)
            executeSearch()
        }

        // Observe user text size setting and reflect into UI state
        viewModelScope.launch {
            AppSettings.textSizeFlow.collect { size ->
                _uiState.value = _uiState.value.copy(textSize = size)
            }
        }

        // Cancel ongoing search when tab is not selected
        viewModelScope.launch {
            tabsViewModel.selectedTabIndex.collect { idx ->
                val tabs = tabsViewModel.tabs.value
                val selectedTabId = tabs.getOrNull(idx)?.destination?.tabId
                if (selectedTabId != tabId) {
                    currentJob?.cancel()
                    _uiState.value = _uiState.value.copy(isLoading = false, isLoadingMore = false)
                }
            }
        }
    }

    private suspend fun continueInitialFetchFromCached() {
        val key = currentKey ?: return
        val BATCH = 20
        val MAX_TOTAL = 200
        val filterCategoryId = stateManager.getState<Long>(tabId, SearchStateKeys.FILTER_CATEGORY_ID)
        val filterBookId = stateManager.getState<Long>(tabId, SearchStateKeys.FILTER_BOOK_ID)
        val fts = buildNearQuery(_uiState.value.query.trim(), _uiState.value.near)
        val acc = _uiState.value.results.toMutableList()

        suspend fun emitUpdate() {
            _uiState.value = _uiState.value.copy(
                results = acc.toList(),
                scrollToAnchorTimestamp = System.currentTimeMillis()
            )
            cache.put(key, SearchCacheEntry(
                results = acc.toList(),
                nextOffset = nextOffset,
                allowedBooks = allowedBooks,
                perBookOffset = perBookOffset.toMap(),
                hasMore = true
            )
            )
        }

        when {
            filterBookId != null && filterBookId > 0 -> {
                var offset = nextOffset
                while (acc.size < MAX_TOTAL) {
                    val page = repository.searchInBookWithOperators(filterBookId, fts, limit = BATCH, offset = offset)
                    if (page.isEmpty()) break
                    acc += page
                    offset += page.size
                    nextOffset = offset
                    emitUpdate()
                }
            }
            filterCategoryId != null && filterCategoryId > 0 -> {
                var offset = nextOffset
                while (acc.size < MAX_TOTAL) {
                    val page = repository.searchInCategoryWithOperators(filterCategoryId, fts, limit = BATCH, offset = offset)
                    if (page.isEmpty()) break
                    acc += page
                    offset += page.size
                    nextOffset = offset
                    emitUpdate()
                }
            }
            else -> {
                var offset = nextOffset
                while (acc.size < MAX_TOTAL) {
                    val page = repository.searchWithOperators(fts, limit = BATCH, offset = offset)
                    if (page.isEmpty()) break
                    acc += page
                    offset += page.size
                    nextOffset = offset
                    emitUpdate()
                }
            }
        }

        val hasMore = acc.size >= MAX_TOTAL
        _uiState.value = _uiState.value.copy(hasMore = hasMore, isLoading = false)
        cache.put(key, SearchCacheEntry(
            results = acc.toList(),
            nextOffset = nextOffset,
            allowedBooks = allowedBooks,
            perBookOffset = perBookOffset.toMap(),
            hasMore = hasMore
        )
        )
    }

    fun setNear(near: Int) {
        _uiState.value = _uiState.value.copy(near = near)
        stateManager.saveState(tabId, SearchStateKeys.NEAR, near)
    }

    fun executeSearch() {
        val q = _uiState.value.query.trim()
        if (q.isBlank()) return
        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, results = emptyList(), hasMore = false)
            stateManager.saveState(tabId, SearchStateKeys.QUERY, q)
            try {
                val near = _uiState.value.near
                val filterCategoryId = stateManager.getState<Long>(tabId, SearchStateKeys.FILTER_CATEGORY_ID)
                val filterBookId = stateManager.getState<Long>(tabId, SearchStateKeys.FILTER_BOOK_ID)

                val key = SearchParamsKey(
                    query = q,
                    near = near,
                    filterCategoryId = filterCategoryId,
                    filterBookId = filterBookId
                )
                currentKey = key
                nextOffset = 0
                allowedBooks = emptyList()
                perBookOffset.clear()

                // If we have cached partial/full results, resume from there
                cache.get(key)?.let { entry ->
                    currentKey = key
                    nextOffset = entry.nextOffset
                    allowedBooks = entry.allowedBooks
                    perBookOffset.clear(); perBookOffset.putAll(entry.perBookOffset)

                    val scopePath = when {
                        filterCategoryId != null && filterCategoryId > 0 -> buildCategoryPath(filterCategoryId)
                        else -> emptyList()
                    }
                    val scopeBook = when {
                        filterBookId != null && filterBookId > 0 -> repository.getBook(filterBookId)
                        else -> null
                    }
                    _uiState.value = _uiState.value.copy(
                        results = entry.results,
                        scopeCategoryPath = scopePath,
                        scopeBook = scopeBook,
                        isLoading = entry.hasMore,
                        hasMore = entry.hasMore,
                        scrollToAnchorTimestamp = System.currentTimeMillis()
                    )
                    if (entry.hasMore) {
                        continueInitialFetchFromCached()
                    }
                    return@launch
                }

                val fts = buildNearQuery(q, near)
                val acc = mutableListOf<SearchResult>()
                val BATCH = 20
                val MAX_TOTAL = 200

                // Populate scope meta once
                val scopePath = when {
                    filterCategoryId != null && filterCategoryId > 0 -> buildCategoryPath(filterCategoryId)
                    else -> emptyList()
                }
                val scopeBook = when {
                    filterBookId != null && filterBookId > 0 -> repository.getBook(filterBookId)
                    else -> null
                }

                suspend fun emitUpdate() {
                    _uiState.value = _uiState.value.copy(
                        results = acc.toList(),
                        scopeCategoryPath = scopePath,
                        scopeBook = scopeBook,
                        // signal UI to try restoration (e.g., if anchor just became available)
                        scrollToAnchorTimestamp = System.currentTimeMillis()
                    )
                    cache.put(key, SearchCacheEntry(
                        results = acc.toList(),
                        nextOffset = nextOffset,
                        allowedBooks = allowedBooks,
                        perBookOffset = perBookOffset.toMap(),
                        hasMore = true
                    )
                    )
                }

                when {
                    filterBookId != null && filterBookId > 0 -> {
                        var offset = 0
                        while (acc.size < MAX_TOTAL) {
                            val page = repository.searchInBookWithOperators(filterBookId, fts, limit = BATCH, offset = offset)
                            if (page.isEmpty()) break
                            acc += page
                            offset += page.size
                            emitUpdate()
                        }
                        nextOffset = offset
                    }
                    filterCategoryId != null && filterCategoryId > 0 -> {
                        var offset = 0
                        while (acc.size < MAX_TOTAL) {
                            val remaining = MAX_TOTAL - acc.size
                            val page = repository.searchInCategoryWithOperators(filterCategoryId, fts, limit = minOf(BATCH, remaining), offset = offset)
                            if (page.isEmpty()) break
                            acc += page
                            offset += page.size
                            emitUpdate()
                        }
                        nextOffset = offset
                        allowedBooks = emptyList()
                        perBookOffset.clear()
                    }
                    else -> {
                        var offset = 0
                        while (acc.size < MAX_TOTAL) {
                            val remaining = MAX_TOTAL - acc.size
                            val page = repository.searchWithOperators(fts, limit = minOf(BATCH, remaining), offset = offset)
                            if (page.isEmpty()) break
                            acc += page
                            offset += page.size
                            emitUpdate()
                        }
                        nextOffset = offset
                    }
                }

                // Determine if more results likely exist: only if we reached MAX_TOTAL
                val hasMore = acc.size >= MAX_TOTAL
                // Cache final results
                cache.put(key, SearchCacheEntry(
                    results = acc.toList(),
                    nextOffset = nextOffset,
                    allowedBooks = allowedBooks,
                    perBookOffset = perBookOffset.toMap(),
                    hasMore = hasMore
                )
                )
                _uiState.value = _uiState.value.copy(hasMore = hasMore)

            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun loadMore() {
        val key = currentKey ?: return
        // guard against concurrent loads
        if (_uiState.value.isLoading || _uiState.value.isLoadingMore) return
        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            try {
                val BATCH = 20
                val ADDITIONAL = 200
                val acc = _uiState.value.results.toMutableList()
                val fts = buildNearQuery(_uiState.value.query.trim(), _uiState.value.near)

                var added = 0
                val filterCategoryId = stateManager.getState<Long>(tabId, SearchStateKeys.FILTER_CATEGORY_ID)
                val filterBookId = stateManager.getState<Long>(tabId, SearchStateKeys.FILTER_BOOK_ID)

                suspend fun emitUpdate() {
                    _uiState.value = _uiState.value.copy(
                        results = acc.toList(),
                        scrollToAnchorTimestamp = System.currentTimeMillis()
                    )
                    cache.put(key, SearchCacheEntry(
                        results = acc.toList(),
                        nextOffset = nextOffset,
                        allowedBooks = allowedBooks,
                        perBookOffset = perBookOffset.toMap(),
                        hasMore = true
                    )
                    )
                }

                when {
                    filterBookId != null && filterBookId > 0 -> {
                        var offset = nextOffset
                        while (added < ADDITIONAL) {
                            val remaining = ADDITIONAL - added
                            val page = repository.searchInBookWithOperators(filterBookId, fts, limit = minOf(BATCH, remaining), offset = offset)
                            if (page.isEmpty()) break
                            acc += page
                            offset += page.size
                            added += page.size
                            emitUpdate()
                        }
                        nextOffset = offset
                    }
                    filterCategoryId != null && filterCategoryId > 0 -> {
                        var offset = nextOffset
                        while (added < ADDITIONAL) {
                            val remaining = ADDITIONAL - added
                            val page = repository.searchInCategoryWithOperators(filterCategoryId, fts, limit = minOf(BATCH, remaining), offset = offset)
                            if (page.isEmpty()) break
                            acc += page
                            offset += page.size
                            added += page.size
                            emitUpdate()
                        }
                        nextOffset = offset
                        allowedBooks = emptyList()
                        perBookOffset.clear()
                    }
                    else -> {
                        var offset = nextOffset
                        while (added < ADDITIONAL) {
                            val remaining = ADDITIONAL - added
                            val page = repository.searchWithOperators(fts, limit = minOf(BATCH, remaining), offset = offset)
                            if (page.isEmpty()) break
                            acc += page
                            offset += page.size
                            added += page.size
                            emitUpdate()
                        }
                        nextOffset = offset
                    }
                }

                // Update cache with expanded list
                cache.put(key, SearchCacheEntry(
                    results = acc.toList(),
                    nextOffset = nextOffset,
                    allowedBooks = allowedBooks,
                    perBookOffset = perBookOffset.toMap(),
                    hasMore = added >= ADDITIONAL
                )
                )
                // Update hasMore: only if we fully added 200 more
                val hasMore = added >= ADDITIONAL
                _uiState.value = _uiState.value.copy(
                    results = acc.toList(),
                    hasMore = hasMore
                )
            } finally {
                _uiState.value = _uiState.value.copy(isLoadingMore = false)
            }
        }
    }

    fun onScroll(anchorId: Long, anchorIndex: Int, index: Int, offset: Int) {
        // Save to TabStateManager for persistence
        stateManager.saveState(tabId, SearchStateKeys.SCROLL_INDEX, index)
        stateManager.saveState(tabId, SearchStateKeys.SCROLL_OFFSET, offset)
        stateManager.saveState(tabId, SearchStateKeys.ANCHOR_ID, anchorId)
        stateManager.saveState(tabId, SearchStateKeys.ANCHOR_INDEX, anchorIndex)

        _uiState.value = _uiState.value.copy(
            scrollIndex = index,
            scrollOffset = offset,
            anchorId = anchorId,
            anchorIndex = anchorIndex
        )
    }

    private suspend fun collectBookIdsUnderCategory(categoryId: Long): Set<Long> {
        val result = mutableSetOf<Long>()
        suspend fun dfs(catId: Long) {
            repository.getBooksByCategory(catId).forEach { result += it.id }
            repository.getCategoryChildren(catId).forEach { child -> dfs(child.id) }
        }
        dfs(categoryId)
        return result
    }

    private suspend fun buildCategoryPath(categoryId: Long): List<Category> {
        val path = mutableListOf<Category>()
        var currentId: Long? = categoryId
        while (currentId != null) {
            val cat = repository.getCategory(currentId) ?: break
            path += cat
            currentId = cat.parentId
        }
        return path.asReversed()
    }

    fun openResult(result: SearchResult) {
        viewModelScope.launch {
            // Pre-initialize new BookContent tab with selected book and anchor to reduce flicker
            val newTabId = UUID.randomUUID().toString()
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

    /**
     * Opens a search result either in the current tab (default) or a new tab when requested.
     * - Current tab: pre-save selected book and anchor on this tabId, then replace destination.
     * - New tab: keep existing behavior (pre-init and navigate to new tab).
     */
    fun openResult(result: SearchResult, openInNewTab: Boolean) {
        if (openInNewTab) {
            openResult(result)
            return
        }
        viewModelScope.launch {
            // Prepare current tab for BookContent with anchor to avoid flicker
            repository.getBook(result.bookId)?.let { book ->
                stateManager.saveState(tabId, StateKeys.SELECTED_BOOK, book)
            }
            stateManager.saveState(tabId, StateKeys.CONTENT_ANCHOR_ID, result.lineId)

            // Swap current tab destination to BookContent while preserving tabId
            tabsViewModel.replaceCurrentTabDestination(
                TabsDestination.BookContent(
                    bookId = result.bookId,
                    tabId = tabId,
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
