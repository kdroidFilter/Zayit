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
    val scopeTocId: Long? = null,
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

    // Caches to speed up breadcrumb building for search results
    private val bookCache: MutableMap<Long, Book> = mutableMapOf()
    private val categoryPathCache: MutableMap<Long, List<Category>> = mutableMapOf()
    private val tocPathCache: MutableMap<Long, List<io.github.kdroidfilter.seforimlibrary.core.models.TocEntry>> = mutableMapOf()

    // Data structures for results tree
    data class SearchTreeBook(val book: Book, val count: Int)
    data class SearchTreeCategory(
        val category: Category,
        val count: Int,
        val children: List<SearchTreeCategory>,
        val books: List<SearchTreeBook>
    )

    data class TocTree(
        val rootEntries: List<io.github.kdroidfilter.seforimlibrary.core.models.TocEntry>,
        val children: Map<Long, List<io.github.kdroidfilter.seforimlibrary.core.models.TocEntry>>
    )

    init {
        val initialQuery = savedStateHandle.get<String>("searchQuery")
            ?: stateManager.getState<String>(tabId, SearchStateKeys.QUERY)
            ?: ""
        val initialNear = stateManager.getState<Int>(tabId, SearchStateKeys.NEAR) ?: 5
        val initialScrollIndex = stateManager.getState<Int>(tabId, SearchStateKeys.SCROLL_INDEX) ?: 0
        val initialScrollOffset = stateManager.getState<Int>(tabId, SearchStateKeys.SCROLL_OFFSET) ?: 0
        val initialAnchorId = stateManager.getState<Long>(tabId, SearchStateKeys.ANCHOR_ID) ?: -1L
        val initialAnchorIndex = stateManager.getState<Int>(tabId, SearchStateKeys.ANCHOR_INDEX) ?: 0
        val initialFilterBookId = stateManager.getState<Long>(tabId, SearchStateKeys.FILTER_BOOK_ID)
        val initialFilterTocId = stateManager.getState<Long>(tabId, SearchStateKeys.FILTER_TOC_ID)
        _uiState.value = _uiState.value.copy(
            query = initialQuery,
            near = initialNear,
            scrollIndex = initialScrollIndex,
            scrollOffset = initialScrollOffset,
            anchorId = initialAnchorId,
            anchorIndex = initialAnchorIndex,
            textSize = AppSettings.getTextSize(),
            scopeTocId = initialFilterTocId?.takeIf { it > 0 }
        )

        if (initialFilterBookId != null && initialFilterBookId > 0) {
            viewModelScope.launch {
                val book = repository.getBook(initialFilterBookId)
                _uiState.value = _uiState.value.copy(scopeBook = book)
            }
        }

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
        val BATCH = 50
        val filterCategoryId = stateManager.getState<Long>(tabId, SearchStateKeys.FILTER_CATEGORY_ID)
        val filterBookId = stateManager.getState<Long>(tabId, SearchStateKeys.FILTER_BOOK_ID)
        val filterTocId = stateManager.getState<Long>(tabId, SearchStateKeys.FILTER_TOC_ID)
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
            filterTocId != null && filterTocId > 0 -> {
                val toc = repository.getTocEntry(filterTocId)
                if (toc != null) {
                    val allowedLineIds = collectLineIdsForTocSubtree(toc.id)
                    val bookId = toc.bookId
                    var offset = nextOffset
                    while (true) {
                        val page = repository.searchInBookWithOperators(bookId, fts, limit = BATCH, offset = offset)
                        if (page.isEmpty()) break
                        val filtered = page.filter { it.lineId in allowedLineIds }
                        acc += filtered
                        offset += page.size
                        nextOffset = offset
                        emitUpdate()
                    }
                }
            }
            filterBookId != null && filterBookId > 0 -> {
                var offset = nextOffset
                while (true) {
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
                while (true) {
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
                while (true) {
                    val page = repository.searchWithOperators(fts, limit = BATCH, offset = offset)
                    if (page.isEmpty()) break
                    acc += page
                    offset += page.size
                    nextOffset = offset
                    emitUpdate()
                }
            }
        }

        val hasMore = false
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
                val filterTocId = stateManager.getState<Long>(tabId, SearchStateKeys.FILTER_TOC_ID)

                val key = SearchParamsKey(
                    query = q,
                    near = near,
                    filterCategoryId = filterCategoryId,
                    filterBookId = filterBookId,
                    filterTocId = filterTocId
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
                val BATCH = 100

                // Populate scope meta once, and set it before streaming results
                val initialScopePath = when {
                    filterCategoryId != null && filterCategoryId > 0 -> buildCategoryPath(filterCategoryId)
                    else -> emptyList()
                }
                val initialScopeBook = when {
                    filterBookId != null && filterBookId > 0 -> repository.getBook(filterBookId)
                    else -> null
                }
                _uiState.value = _uiState.value.copy(
                    scopeCategoryPath = initialScopePath,
                    scopeBook = initialScopeBook
                )

                suspend fun emitUpdate() {
                    _uiState.value = _uiState.value.copy(
                        results = acc.toList(),
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
                    filterTocId != null && filterTocId > 0 -> {
                        val toc = repository.getTocEntry(filterTocId)
                        if (toc != null) {
                            val allowedLineIds = collectLineIdsForTocSubtree(toc.id)
                            val bookId = toc.bookId
                            var offset = 0
                            while (true) {
                                val page = repository.searchInBookWithOperators(bookId, fts, limit = BATCH, offset = offset)
                                if (page.isEmpty()) break
                                val filtered = page.filter { it.lineId in allowedLineIds }
                                acc += filtered
                                offset += page.size
                                emitUpdate()
                            }
                            nextOffset = offset
                        }
                    }
                    filterBookId != null && filterBookId > 0 -> {
                        var offset = 0
                        while (true) {
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
                        while (true) {
                            val page = repository.searchInCategoryWithOperators(filterCategoryId, fts, limit = BATCH, offset = offset)
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
                        while (true) {
                            val page = repository.searchWithOperators(fts, limit = BATCH, offset = offset)
                            if (page.isEmpty()) break
                            acc += page
                            offset += page.size
                            emitUpdate()
                        }
                        nextOffset = offset
                    }
                }

                // No more pages left; we fetched everything for this query
                val hasMore = false
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
                // With unlimited initial fetch, there is nothing to load.
                val BATCH = 0
                val ADDITIONAL = 0
                val acc = _uiState.value.results.toMutableList()
                val fts = ""

                var added = 0
                val filterCategoryId = stateManager.getState<Long>(tabId, SearchStateKeys.FILTER_CATEGORY_ID)
                val filterBookId = stateManager.getState<Long>(tabId, SearchStateKeys.FILTER_BOOK_ID)
                val filterTocId = stateManager.getState<Long>(tabId, SearchStateKeys.FILTER_TOC_ID)

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
                        hasMore = false
                    )
                    )
                }

                when {
                    filterTocId != null && filterTocId > 0 -> {
                        // No-op; everything is already loaded
                    }
                    filterBookId != null && filterBookId > 0 -> {
                        // No-op; everything is already loaded
                    }
                    filterCategoryId != null && filterCategoryId > 0 -> {
                        // No-op; everything is already loaded
                    }
                    else -> {
                        // No-op; everything is already loaded
                    }
                }

                // Update cache with expanded list
                cache.put(key, SearchCacheEntry(
                    results = acc.toList(),
                    nextOffset = nextOffset,
                    allowedBooks = allowedBooks,
                    perBookOffset = perBookOffset.toMap(),
                    hasMore = false
                )
                )
                // Update hasMore: only if we fully added 200 more
                val hasMore = false
                _uiState.value = _uiState.value.copy(
                    results = acc.toList(),
                    hasMore = hasMore
                )
            } finally {
                _uiState.value = _uiState.value.copy(isLoadingMore = false)
            }
        }
    }

    fun cancelSearch() {
        currentJob?.cancel()
        _uiState.value = _uiState.value.copy(isLoading = false, isLoadingMore = false)
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

    /**
     * Compute a category/book tree with per-node counts based on the current results list.
     * Categories accumulate counts from their descendant books. Only categories and books that
     * appear in the current results are included.
     */
    suspend fun buildSearchResultTree(): List<SearchTreeCategory> {
        val results = uiState.value.results
        if (results.isEmpty()) return emptyList()

        // Accumulate counts and collect entities
        val bookCounts = mutableMapOf<Long, Int>()
        val booksById = mutableMapOf<Long, Book>()
        val categoryCounts = mutableMapOf<Long, Int>()
        val categoriesById = mutableMapOf<Long, Category>()

        suspend fun resolveBook(bookId: Long): Book? {
            return bookCache[bookId] ?: repository.getBook(bookId)?.also { bookCache[bookId] = it }
        }

        suspend fun resolveCategoryPath(categoryId: Long): List<Category> {
            return categoryPathCache[categoryId] ?: buildCategoryPath(categoryId).also {
                categoryPathCache[categoryId] = it
            }
        }

        for (res in results) {
            val book = resolveBook(res.bookId) ?: continue
            booksById[book.id] = book
            bookCounts[book.id] = (bookCounts[book.id] ?: 0) + 1

            val path = resolveCategoryPath(book.categoryId)
            for (cat in path) {
                categoriesById[cat.id] = cat
                categoryCounts[cat.id] = (categoryCounts[cat.id] ?: 0) + 1
            }
        }

        if (categoriesById.isEmpty()) return emptyList()

        // Build parent -> children map for encountered categories
        val childrenByParent: MutableMap<Long?, MutableList<Category>> = mutableMapOf()
        for (cat in categoriesById.values) {
            val list = childrenByParent.getOrPut(cat.parentId) { mutableListOf() }
            list += cat
        }

        // For deterministic ordering, sort by title
        childrenByParent.values.forEach { it.sortBy { c -> c.title } }

        fun buildNode(cat: Category): SearchTreeCategory {
            val childCats = childrenByParent[cat.id].orEmpty().map { buildNode(it) }
            val booksInCat = booksById.values
                .filter { it.categoryId == cat.id }
                .sortedBy { it.title }
                .map { b -> SearchTreeBook(b, bookCounts[b.id] ?: 0) }
            return SearchTreeCategory(
                category = cat,
                count = categoryCounts[cat.id] ?: 0,
                children = childCats,
                books = booksInCat
            )
        }

        // Roots are categories whose parent is either null or not in the encountered set
        val encounteredIds = categoriesById.keys
        val roots = categoriesById.values
            .filter { it.parentId == null || it.parentId !in encounteredIds }
            .sortedBy { it.title }
            .map { buildNode(it) }
        return roots
    }

    /** Apply a category filter and re-run the same search query. */
    fun filterByCategoryId(categoryId: Long) {
        viewModelScope.launch {
            // Persist filters but do not re-query; we filter client-side
            stateManager.saveState(tabId, SearchStateKeys.FILTER_CATEGORY_ID, categoryId)
            stateManager.saveState(tabId, SearchStateKeys.FILTER_BOOK_ID, 0L)
            stateManager.saveState(tabId, SearchStateKeys.FILTER_TOC_ID, 0L)
            val scopePath = buildCategoryPath(categoryId)
            _uiState.value = _uiState.value.copy(
                scopeCategoryPath = scopePath,
                scopeBook = null,
                scopeTocId = null,
                scrollIndex = 0,
                scrollOffset = 0,
                scrollToAnchorTimestamp = System.currentTimeMillis()
            )
        }
    }

    /** Apply a book filter and re-run the same search query. */
    fun filterByBookId(bookId: Long) {
        viewModelScope.launch {
            // Persist filters but do not re-query; we filter client-side
            stateManager.saveState(tabId, SearchStateKeys.FILTER_CATEGORY_ID, 0L)
            stateManager.saveState(tabId, SearchStateKeys.FILTER_BOOK_ID, bookId)
            stateManager.saveState(tabId, SearchStateKeys.FILTER_TOC_ID, 0L)
            val book = runCatching { repository.getBook(bookId) }.getOrNull()
            _uiState.value = _uiState.value.copy(
                scopeBook = book,
                scopeCategoryPath = emptyList(),
                scopeTocId = null,
                scrollIndex = 0,
                scrollOffset = 0,
                scrollToAnchorTimestamp = System.currentTimeMillis()
            )
        }
    }

    fun filterByTocId(tocId: Long) {
        viewModelScope.launch {
            stateManager.saveState(tabId, SearchStateKeys.FILTER_TOC_ID, tocId)
            _uiState.value = _uiState.value.copy(
                scopeTocId = tocId,
                scrollIndex = 0,
                scrollOffset = 0,
                scrollToAnchorTimestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * Returns results filtered by the current scope (book or category path) without hitting the DB.
     */
    suspend fun getVisibleResults(): List<SearchResult> {
        val state = uiState.value
        val all = state.results
        val scopeBook = state.scopeBook
        val scopeCat = state.scopeCategoryPath.lastOrNull()
        val scopeToc = state.scopeTocId
        if (scopeBook == null && scopeCat == null && scopeToc == null) return all
        if (scopeToc != null) {
            val allowedLineIds = collectLineIdsForTocSubtree(scopeToc)
            return all.filter { it.lineId in allowedLineIds }
        }
        if (scopeBook != null) return all.filter { it.bookId == scopeBook.id }
        if (scopeCat != null) {
            val allowedBooks = collectBookIdsUnderCategory(scopeCat.id)
            return all.filter { it.bookId in allowedBooks }
        }
        return all
    }

    /**
     * Compute aggregated counts for each TOC entry of the currently selected book, based on
     * the full results list (ignoring current TOC filter to keep navigation informative).
     */
    suspend fun computeTocCountsForSelectedBook(): Map<Long, Int> {
        val bookId = uiState.value.scopeBook?.id ?: return emptyMap()
        val relevant = uiState.value.results.filter { it.bookId == bookId }
        val counts = mutableMapOf<Long, Int>()
        for (res in relevant) {
            val tocId = runCatching { repository.getTocEntryIdForLine(res.lineId) }.getOrNull() ?: continue
            // Walk up parents to aggregate per ancestor as well
            var current: Long? = tocId
            var guard = 0
            while (current != null && guard++ < 500) {
                counts[current] = (counts[current] ?: 0) + 1
                val parent = runCatching { repository.getTocEntry(current) }.getOrNull()?.parentId
                current = parent
            }
        }
        return counts
    }

    /**
     * Returns the TOC structure (roots + children map) for the current scope book, or null.
     */
    suspend fun getTocStructureForScopeBook(): TocTree? {
        val bookId = uiState.value.scopeBook?.id ?: return null
        val all = runCatching { repository.getBookToc(bookId) }.getOrElse { emptyList() }
        val byParent = all.groupBy { it.parentId ?: -1L }
        val roots = byParent[-1L] ?: all.filter { it.parentId == null }
        // Build children map keyed by real IDs only
        val children: Map<Long, List<io.github.kdroidfilter.seforimlibrary.core.models.TocEntry>> = all
            .filter { it.parentId != null }
            .groupBy { it.parentId!! }
        return TocTree(rootEntries = roots, children = children)
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

    /**
     * Compute breadcrumb pieces for a given search result: category path, book, and TOC path to the line.
     * Returns a list of display strings in order. Uses lightweight caches to avoid repeated lookups.
     */
    suspend fun getBreadcrumbPiecesFor(result: SearchResult): List<String> {
        val pieces = mutableListOf<String>()

        // Resolve book (cached)
        val book = bookCache[result.bookId] ?: repository.getBook(result.bookId)?.also {
            bookCache[result.bookId] = it
        } ?: return listOf() // If no book, nothing to show

        // Category path for the book (cached by categoryId)
        val categories = categoryPathCache[book.categoryId] ?: buildCategoryPath(book.categoryId).also {
            categoryPathCache[book.categoryId] = it
        }
        pieces += categories.map { it.title }

        // Book title
        pieces += book.title

        // TOC path to the line (cached by lineId)
        val tocEntries = tocPathCache[result.lineId] ?: run {
            val tocId = runCatching { repository.getTocEntryIdForLine(result.lineId) }.getOrNull()
            if (tocId != null) {
                val path = mutableListOf<io.github.kdroidfilter.seforimlibrary.core.models.TocEntry>()
                var current: Long? = tocId
                var guard = 0
                while (current != null && guard++ < 200) {
                    val entry = repository.getTocEntry(current)
                    if (entry != null) {
                        path.add(0, entry)
                        current = entry.parentId
                    } else break
                }
                path
            } else emptyList()
        }.also { path ->
            // Cache by lineId for future calls
            tocPathCache[result.lineId] = path
        }

        if (tocEntries.isNotEmpty()) {
            // Drop first TOC if it equals book title to avoid duplication
            val adjusted = if (tocEntries.first().text == book.title) tocEntries.drop(1) else tocEntries
            pieces += adjusted.map { it.text }
        }

        return pieces
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

    private suspend fun collectLineIdsForTocSubtree(tocId: Long): Set<Long> {
        val result = mutableSetOf<Long>()
        suspend fun dfs(id: Long) {
            runCatching { repository.getLineIdsForTocEntry(id) }.getOrNull()?.let { result += it }
            val children = runCatching { repository.getTocChildren(id) }.getOrNull().orEmpty()
            for (child in children) dfs(child.id)
        }
        dfs(tocId)
        return result
    }
}
