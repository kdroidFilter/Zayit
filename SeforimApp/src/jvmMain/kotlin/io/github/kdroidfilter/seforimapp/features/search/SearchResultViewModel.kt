@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)

package io.github.kdroidfilter.seforimapp.features.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
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
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.flowOn
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
    val isLoadingMore: Boolean = false,
    val progressCurrent: Int = 0,
    val progressTotal: Long? = null
)

class SearchResultViewModel(
    savedStateHandle: SavedStateHandle,
    private val stateManager: TabStateManager,
    private val repository: SeforimRepository,
    private val titleUpdateManager: TabTitleUpdateManager,
    private val tabsViewModel: TabsViewModel
) : TabAwareViewModel(
    tabId = savedStateHandle.get<String>(StateKeys.TAB_ID) ?: "",
    stateManager = stateManager
) {
    // Key representing the current search parameters (no result caching).
    private data class SearchParamsKey(
        val query: String,
        val near: Int,
        val filterCategoryId: Long?,
        val filterBookId: Long?,
        val filterTocId: Long?
    )
    // Batching policy: fetch WARMUP_BATCH_SIZE until WARMUP_LIMIT results, then STEADY_BATCH_SIZE
    private companion object {
        private const val WARMUP_LIMIT = 500
        private const val WARMUP_BATCH_SIZE = 20
        private const val STEADY_BATCH_SIZE = 1000
    }

    private fun batchSizeFor(currentCount: Int): Int =
        if (currentCount < WARMUP_LIMIT) WARMUP_BATCH_SIZE else STEADY_BATCH_SIZE
    private val tabId: String = savedStateHandle.get<String>(StateKeys.TAB_ID) ?: ""

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    private var currentJob: kotlinx.coroutines.Job? = null

    // Pagination cursors/state
    private var currentKey: SearchParamsKey? = null

    // Caches to speed up breadcrumb building for search results
    private val bookCache: MutableMap<Long, Book> = mutableMapOf()
    private val categoryPathCache: MutableMap<Long, List<Category>> = mutableMapOf()
    private val tocPathCache: MutableMap<Long, List<TocEntry>> = mutableMapOf()

    // Data structures for results tree
    data class SearchTreeBook(val book: Book, val count: Int)
    data class SearchTreeCategory(
        val category: Category,
        val count: Int,
        val children: List<SearchTreeCategory>,
        val books: List<SearchTreeBook>
    )

    data class TocTree(
        val rootEntries: List<TocEntry>,
        val children: Map<Long, List<TocEntry>>
    )

    data class CategoryAgg(
        val categoryCounts: Map<Long, Int>,
        val bookCounts: Map<Long, Int>,
        val booksForCategory: Map<Long, List<Book>>
    )

    // Aggregates accumulators used to update flows incrementally per fetched page
    private val categoryCountsAcc: MutableMap<Long, Int> = mutableMapOf()
    private val bookCountsAcc: MutableMap<Long, Int> = mutableMapOf()
    private val booksForCategoryAcc: MutableMap<Long, MutableSet<Book>> = mutableMapOf()
    private val tocCountsAcc: MutableMap<Long, Int> = mutableMapOf()

    private val _categoryAgg = MutableStateFlow(CategoryAgg(emptyMap(), emptyMap(), emptyMap()))
    private val _tocCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    private var wasPausedByTabSwitch: Boolean = false

    // Allowed sets computed only when scope changes (Debounce 300ms on scope)
    private val scopeBookIdFlow = uiState.map { it.scopeBook?.id }.distinctUntilChanged()
    private val scopeCatIdFlow = uiState.map { it.scopeCategoryPath.lastOrNull()?.id }.distinctUntilChanged()
    private val scopeTocIdFlow = uiState.map { it.scopeTocId }.distinctUntilChanged()

    private val allowedBooksFlow: StateFlow<Set<Long>> =
        scopeCatIdFlow
            .debounce(100)
            .mapLatest { catId ->
                if (catId == null) emptySet() else collectBookIdsUnderCategory(catId)
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val allowedLineIdsFlow: StateFlow<Set<Long>> =
        scopeTocIdFlow
            .debounce(100)
            .mapLatest { tocId ->
                if (tocId == null) emptySet() else collectLineIdsForTocSubtree(tocId)
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // Visible results update immediately per page; filtering uses precomputed allowed sets when available
    val visibleResultsFlow: StateFlow<List<SearchResult>> =
        combine(
            uiState.map { it.results },
            scopeBookIdFlow,
            allowedBooksFlow,
            allowedLineIdsFlow,
            scopeTocIdFlow
        ) { results, bookId, allowedBooks, allowedLineIds, tocId -> Quint(results, bookId, allowedBooks, allowedLineIds, tocId) }
            .debounce(50)
            .mapLatest { q ->
                val results = q.a
                val bookId = q.b
                val allowedBooks = q.c
                val allowedLineIds = q.d
                val tocId = q.e
                when {
                    tocId != null -> results.filter { it.lineId in allowedLineIds }
                    bookId != null -> results.filter { it.bookId == bookId }
                    allowedBooks.isNotEmpty() -> results.filter { it.bookId in allowedBooks }
                    else -> results
                }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Category and TOC aggregates are updated incrementally from fetch loops
    val categoryAggFlow: StateFlow<CategoryAgg> = _categoryAgg.asStateFlow()
    val tocCountsFlow: StateFlow<Map<Long, Int>> = _tocCounts.asStateFlow()

    private val _tocTree = MutableStateFlow<TocTree?>(null)
    val tocTreeFlow: StateFlow<TocTree?> = _tocTree.asStateFlow()

    // Helper to combine 4 values strongly typed
    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
    private data class Quint<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

    private var currentTocBookId: Long? = null

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
        val datasetScope = stateManager.getState<String>(tabId, SearchStateKeys.DATASET_SCOPE) ?: "global"
        val fetchCategoryId = stateManager.getState<Long>(tabId, SearchStateKeys.FETCH_CATEGORY_ID)
        val fetchBookId = stateManager.getState<Long>(tabId, SearchStateKeys.FETCH_BOOK_ID)
        val fetchTocId = stateManager.getState<Long>(tabId, SearchStateKeys.FETCH_TOC_ID)
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

        // Restore scope book from either book filter or TOC filter
        when {
            initialFilterBookId != null && initialFilterBookId > 0 -> {
                viewModelScope.launch {
                    val book = repository.getBook(initialFilterBookId)
                    _uiState.value = _uiState.value.copy(scopeBook = book)
                }
            }
            initialFilterTocId != null && initialFilterTocId > 0 -> {
                viewModelScope.launch {
                    val toc = repository.getTocEntry(initialFilterTocId)
                    val book = toc?.let { repository.getBook(it.bookId) }
                    _uiState.value = _uiState.value.copy(scopeBook = book)
                }
            }
        }

        // Update tab title to the query (TabsViewModel also handles initial title)
        if (initialQuery.isNotBlank()) {
            titleUpdateManager.updateTabTitle(tabId, initialQuery, TabType.SEARCH)
        }

        // Try to restore a full snapshot for this tab without redoing the search.
        val cached = SearchTabCache.get(tabId)
        if (cached != null) {
            // Adopt cached results and aggregates; keep filters and scroll from state manager
            _uiState.value = _uiState.value.copy(
                results = cached.results,
                isLoading = false,
                hasMore = false,
                progressCurrent = cached.results.size,
                progressTotal = cached.results.size.toLong(),
                // trigger scroll restoration once items are present
                scrollToAnchorTimestamp = System.currentTimeMillis()
            )
            // Immediately restore aggregates and toc counts so the tree and TOC show counts without delay
            _categoryAgg.value = CategoryAgg(
                categoryCounts = cached.categoryAgg.categoryCounts,
                bookCounts = cached.categoryAgg.bookCounts,
                booksForCategory = cached.categoryAgg.booksForCategory
            )
            _tocCounts.value = cached.tocCounts
            // Restore TOC tree if present
            cached.tocTree?.let { snap ->
                _tocTree.value = TocTree(snap.rootEntries, snap.children)
            }
            // Reconstruct currentKey from dataset fetch scope (not view filters)
            currentKey = SearchParamsKey(
                query = _uiState.value.query,
                near = _uiState.value.near,
                filterCategoryId = fetchCategoryId?.takeIf { datasetScope == "category" && it > 0 },
                filterBookId = fetchBookId?.takeIf { (datasetScope == "book" || datasetScope == "toc") && it > 0 },
                filterTocId = fetchTocId?.takeIf { datasetScope == "toc" && it > 0 }
            )
            // Restore category path if needed (async, non-blocking)
            viewModelScope.launch {
                // Restore category scope path if a category filter is persisted
                val filterCategoryId = stateManager.getState<Long>(tabId, SearchStateKeys.FILTER_CATEGORY_ID)
                if (filterCategoryId != null && filterCategoryId > 0) {
                    val path = runCatching { buildCategoryPath(filterCategoryId) }.getOrDefault(emptyList())
                    _uiState.value = _uiState.value.copy(scopeCategoryPath = path)
                }
            }
        } else if (initialQuery.isNotBlank()) {
            // Fresh VM with no snapshot – run the search
            executeSearch()
        }

        // Observe user text size setting and reflect into UI state
        viewModelScope.launch {
            AppSettings.textSizeFlow.collect { size ->
                _uiState.value = _uiState.value.copy(textSize = size)
            }
        }

        // Pause/resume when switching tabs; cancel when tab is closed
        viewModelScope.launch {
            // Observe currently selected tab to pause/resume
            tabsViewModel.selectedTabIndex.collect { idx ->
                val tabs = tabsViewModel.tabs.value
                val selectedTabId = tabs.getOrNull(idx)?.destination?.tabId
                if (selectedTabId != tabId) {
                    // Pause current search
                    if (_uiState.value.isLoading || _uiState.value.isLoadingMore) {
                        wasPausedByTabSwitch = true
                        currentJob?.cancel()
                        _uiState.value = _uiState.value.copy(isLoading = false, isLoadingMore = false)
                    }
                    // Save a fresh snapshot of current results and aggregates for instant restoration
                    val catAgg = _categoryAgg.value
                    val treeSnap = _tocTree.value?.let { t ->
                        SearchTabCache.TocTreeSnapshot(t.rootEntries, t.children)
                    }
                    val snapshot = SearchTabCache.Snapshot(
                        results = _uiState.value.results,
                        categoryAgg = SearchTabCache.CategoryAggSnapshot(
                            categoryCounts = catAgg.categoryCounts,
                            bookCounts = catAgg.bookCounts,
                            booksForCategory = catAgg.booksForCategory
                        ),
                        tocCounts = _tocCounts.value,
                        tocTree = treeSnap
                    )
                    SearchTabCache.put(tabId, snapshot)
                } else {
                    // Do not auto-resume search on tab reselect. Keep current results/snapshot only.
                    wasPausedByTabSwitch = false
                }
            }
        }

        viewModelScope.launch {
            // Observe tabs list and cancel search if this tab gets closed
            tabsViewModel.tabs.collect { tabs ->
                val exists = tabs.any { it.destination.tabId == tabId }
                if (!exists) {
                    // Tab was closed; stop work and clear any cached snapshot to free memory
                    cancelSearch()
                    SearchTabCache.clear(tabId)
                }
            }
        }
    }

    // Caching continuation removed: searches are executed fresh.

    fun setNear(near: Int) {
        _uiState.value = _uiState.value.copy(near = near)
        stateManager.saveState(tabId, SearchStateKeys.NEAR, near)
    }

    fun executeSearch() {
        val q = _uiState.value.query.trim()
        if (q.isBlank()) return
        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, results = emptyList(), hasMore = false, progressCurrent = 0, progressTotal = null)
            stateManager.saveState(tabId, SearchStateKeys.QUERY, q)
            try {
                val near = _uiState.value.near
                // Use dataset fetch scope for DB queries; view filters are applied client-side
                val datasetScope = stateManager.getState<String>(tabId, SearchStateKeys.DATASET_SCOPE) ?: "global"
                val fetchCategoryId = stateManager.getState<Long>(tabId, SearchStateKeys.FETCH_CATEGORY_ID)?.takeIf { datasetScope == "category" && it > 0 }
                val fetchBookId = stateManager.getState<Long>(tabId, SearchStateKeys.FETCH_BOOK_ID)?.takeIf { (datasetScope == "book" || datasetScope == "toc") && it > 0 }
                val fetchTocId = stateManager.getState<Long>(tabId, SearchStateKeys.FETCH_TOC_ID)?.takeIf { datasetScope == "toc" && it > 0 }

                val key = SearchParamsKey(
                    query = q,
                    near = near,
                    filterCategoryId = fetchCategoryId,
                    filterBookId = fetchBookId,
                    filterTocId = fetchTocId
                )
                currentKey = key

                val fts = buildNearQuery(q, near)
                // Initialize progress total when determinable
                val initialProgressTotal: Long? = when {
                    fetchTocId != null && fetchTocId > 0 -> null // expensive to count over subtree; show indeterminate
                    fetchBookId != null && fetchBookId > 0 -> runCatching { repository.countSearchResultsInBook(fts, fetchBookId) }.getOrNull()
                    fetchCategoryId != null && fetchCategoryId > 0 -> runCatching { repository.countSearchResultsInCategory(fts, fetchCategoryId) }.getOrNull()
                    else -> runCatching { repository.countSearchResults(fts) }.getOrNull()
                }
                _uiState.value = _uiState.value.copy(progressTotal = initialProgressTotal, progressCurrent = 0)
                val acc = mutableListOf<SearchResult>()

                // Populate scope meta once, and set it before streaming results
                val initialScopePath = when {
                    // UI scope comes from view filters, not fetch filters
                    stateManager.getState<Long>(tabId, SearchStateKeys.FILTER_CATEGORY_ID)?.let { it > 0 } == true -> buildCategoryPath(stateManager.getState<Long>(tabId, SearchStateKeys.FILTER_CATEGORY_ID)!!)
                    else -> emptyList()
                }
                val initialScopeBook = when {
                    stateManager.getState<Long>(tabId, SearchStateKeys.FILTER_BOOK_ID)?.let { it > 0 } == true -> repository.getBook(stateManager.getState<Long>(tabId, SearchStateKeys.FILTER_BOOK_ID)!!)
                    else -> null
                }
                _uiState.value = _uiState.value.copy(
                    scopeCategoryPath = initialScopePath,
                    scopeBook = initialScopeBook
                )
                // Prepare TOC tree for the scoped book so the panel is ready without recomputation
                initialScopeBook?.let { book ->
                    if (currentTocBookId != book.id) {
                        val tree = buildTocTreeForBook(book.id)
                        _tocTree.value = tree
                        currentTocBookId = book.id
                    }
                }

                suspend fun emitUpdate() {
                    _uiState.value = _uiState.value.copy(
                        results = acc.toList(),
                        // signal UI to try restoration (e.g., if anchor just became available)
                        scrollToAnchorTimestamp = System.currentTimeMillis(),
                        progressCurrent = acc.size
                    )
                }

                when {
                    fetchTocId != null && fetchTocId > 0 -> {
                        val toc = repository.getTocEntry(fetchTocId)
                        if (toc != null) {
                            val allowedLineIds = collectLineIdsForTocSubtree(toc.id)
                            val bookId = toc.bookId
                            var offset = 0
                            while (true) {
                                val currentBatch = batchSizeFor(acc.size)
                                val page = repository.searchInBookWithOperators(bookId, fts, limit = currentBatch, offset = offset)
                                if (page.isEmpty()) break
                                val filtered = page.filter { it.lineId in allowedLineIds }
                                acc += filtered
                                updateAggregatesForPage(filtered)
                                uiState.value.scopeBook?.id?.let { updateTocCountsForPage(filtered, it) }
                                offset += page.size
                                emitUpdate()
                            }
                        }
                    }
                    fetchBookId != null && fetchBookId > 0 -> {
                        var offset = 0
                        while (true) {
                            val currentBatch = batchSizeFor(acc.size)
                            val page = repository.searchInBookWithOperators(fetchBookId, fts, limit = currentBatch, offset = offset)
                            if (page.isEmpty()) break
                            acc += page
                            updateAggregatesForPage(page)
                            uiState.value.scopeBook?.id?.let { updateTocCountsForPage(page, it) }
                            offset += page.size
                            emitUpdate()
                        }
                    }
                    fetchCategoryId != null && fetchCategoryId > 0 -> {
                        var offset = 0
                        while (true) {
                            val currentBatch = batchSizeFor(acc.size)
                            val page = repository.searchInCategoryWithOperators(fetchCategoryId, fts, limit = currentBatch, offset = offset)
                            if (page.isEmpty()) break
                            acc += page
                            updateAggregatesForPage(page)
                            uiState.value.scopeBook?.id?.let { updateTocCountsForPage(page, it) }
                            offset += page.size
                            emitUpdate()
                        }
                    }
                    else -> {
                        var offset = 0
                        while (true) {
                            val currentBatch = batchSizeFor(acc.size)
                            val page = repository.searchWithOperators(fts, limit = currentBatch, offset = offset)
                            if (page.isEmpty()) break
                            acc += page
                            updateAggregatesForPage(page)
                            uiState.value.scopeBook?.id?.let { updateTocCountsForPage(page, it) }
                            offset += page.size
                            emitUpdate()
                        }
                    }
                }

                // No more pages left; we fetched everything for this query
                val hasMore = false
                _uiState.value = _uiState.value.copy(hasMore = hasMore, progressCurrent = acc.size)

            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun loadMore() { /* no-op: initial fetch loads all results; no cache/pagination */ }

    fun cancelSearch() {
        currentJob?.cancel()
        _uiState.value = _uiState.value.copy(isLoading = false, isLoadingMore = false)
    }

    override fun onCleared() {
        super.onCleared()
        // If the tab still exists, persist a lightweight snapshot so it can be restored
        // without re-searching. If it was closed, clear any cached snapshot to free memory.
        val stillExists = runCatching {
            tabsViewModel.tabs.value.any { it.destination.tabId == tabId }
        }.getOrDefault(false)
        if (stillExists) {
            val catAgg = _categoryAgg.value
            val treeSnap = _tocTree.value?.let { t ->
                SearchTabCache.TocTreeSnapshot(t.rootEntries, t.children)
            }
            val snapshot = SearchTabCache.Snapshot(
                results = uiState.value.results,
                categoryAgg = SearchTabCache.CategoryAggSnapshot(
                    categoryCounts = catAgg.categoryCounts,
                    bookCounts = catAgg.bookCounts,
                    booksForCategory = catAgg.booksForCategory
                ),
                tocCounts = _tocCounts.value,
                tocTree = treeSnap
            )
            SearchTabCache.put(tabId, snapshot)
        } else {
            SearchTabCache.clear(tabId)
        }
        cancelSearch()
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

    /** Apply a category filter. Re-query if current dataset is restricted. */
    fun filterByCategoryId(categoryId: Long) {
        viewModelScope.launch {
            // Persist new filters
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

            // If current results were fetched with a restricted scope (book/TOC/other category),
            // the dataset does not include other categories' results. In that case, re-run the search.
            val key = currentKey
            val mustRequery = when {
                key == null -> false // nothing fetched yet; client-side filtering is fine
                (key.filterTocId ?: 0L) > 0L -> true
                (key.filterBookId ?: 0L) > 0L -> true
                (key.filterCategoryId ?: 0L) > 0L && key.filterCategoryId != categoryId -> true
                else -> false
            }
            if (mustRequery) executeSearch()
        }
    }

    /** Apply a book filter. Re-query if current dataset is restricted to another scope. */
    fun filterByBookId(bookId: Long) {
        viewModelScope.launch {
            // Persist new filters
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
            // Refresh TOC tree for this book
            if (book != null && currentTocBookId != book.id) {
                val tree = runCatching { buildTocTreeForBook(book.id) }.getOrNull()
                if (tree != null) {
                    _tocTree.value = tree
                    currentTocBookId = book.id
                }
            }
            val key = currentKey
            val mustRequery = when {
                key == null -> false
                (key.filterTocId ?: 0L) > 0L -> true
                (key.filterBookId ?: 0L) > 0L && key.filterBookId != bookId -> true
                (key.filterCategoryId ?: 0L) > 0L -> true
                else -> false
            }
            if (mustRequery) {
                executeSearch()
            } else {
                // Recompute TOC counts from current results for responsiveness
                val current = uiState.value.results
                if (book != null) {
                    recomputeTocCountsForBook(book.id, current)
                }
            }
        }
    }

    fun filterByTocId(tocId: Long) {
        viewModelScope.launch {
            val toc = runCatching { repository.getTocEntry(tocId) }.getOrNull()
            val bookIdFromToc = toc?.bookId
            // Persist new filters: ensure book filter matches the TOC's book for proper restoration
            stateManager.saveState(tabId, SearchStateKeys.FILTER_TOC_ID, tocId)
            if (bookIdFromToc != null && bookIdFromToc > 0) {
                stateManager.saveState(tabId, SearchStateKeys.FILTER_BOOK_ID, bookIdFromToc)
            }

            val scopeBook = if (bookIdFromToc != null) runCatching { repository.getBook(bookIdFromToc) }.getOrNull() else null
            _uiState.value = _uiState.value.copy(
                scopeBook = scopeBook,
                scopeTocId = tocId,
                scopeCategoryPath = emptyList(),
                scrollIndex = 0,
                scrollOffset = 0,
                scrollToAnchorTimestamp = System.currentTimeMillis()
            )
            // Refresh TOC tree for this book
            if (scopeBook != null && currentTocBookId != scopeBook.id) {
                val tree = runCatching { buildTocTreeForBook(scopeBook.id) }.getOrNull()
                if (tree != null) {
                    _tocTree.value = tree
                    currentTocBookId = scopeBook.id
                }
            }

            // If current dataset is already TOC-scoped to a different entry or book-scoped to another book, re-query.
            val key = currentKey
            val mustRequery = when {
                key == null -> false
                (key.filterTocId ?: 0L) > 0L && key.filterTocId != tocId -> true
                (key.filterBookId ?: 0L) > 0L && bookIdFromToc != null && key.filterBookId != bookIdFromToc -> true
                else -> false
            }
            if (mustRequery) {
                executeSearch()
            } else if (scopeBook != null) {
                // Recompute TOC counts from current results for responsiveness
                val current = uiState.value.results
                recomputeTocCountsForBook(scopeBook.id, current)
            }
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
        val children: Map<Long, List<TocEntry>> = all
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
                val path = mutableListOf<TocEntry>()
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

            tabsViewModel.openTab(
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

    private suspend fun updateAggregatesForPage(page: List<SearchResult>) {
        for (res in page) {
            val book = bookCache[res.bookId] ?: repository.getBook(res.bookId)?.also { bookCache[res.bookId] = it } ?: continue
            bookCountsAcc[book.id] = (bookCountsAcc[book.id] ?: 0) + 1
            val path = categoryPathCache[book.categoryId] ?: buildCategoryPath(book.categoryId).also { categoryPathCache[book.categoryId] = it }
            for (cat in path) {
                categoryCountsAcc[cat.id] = (categoryCountsAcc[cat.id] ?: 0) + 1
            }
            val set = booksForCategoryAcc.getOrPut(book.categoryId) { mutableSetOf() }
            set += book
        }
        _categoryAgg.value = CategoryAgg(
            categoryCounts = categoryCountsAcc.toMap(),
            bookCounts = bookCountsAcc.toMap(),
            booksForCategory = booksForCategoryAcc.mapValues { it.value.toList() }
        )
    }

    private suspend fun updateTocCountsForPage(page: List<SearchResult>, scopeBookId: Long) {
        val subset = page.filter { it.bookId == scopeBookId }
        if (subset.isEmpty()) return
        for (res in subset) {
            val tocId = runCatching { repository.getTocEntryIdForLine(res.lineId) }.getOrNull() ?: continue
            var current: Long? = tocId
            var guard = 0
            while (current != null && guard++ < 500) {
                tocCountsAcc[current] = (tocCountsAcc[current] ?: 0) + 1
                current = runCatching { repository.getTocEntry(current) }.getOrNull()?.parentId
            }
        }
        _tocCounts.value = tocCountsAcc.toMap()
    }

    private suspend fun recomputeTocCountsForBook(bookId: Long, results: List<SearchResult>) {
        tocCountsAcc.clear()
        updateTocCountsForPage(results, bookId)
    }

    private suspend fun buildTocTreeForBook(bookId: Long): TocTree {
        val all = runCatching { repository.getBookToc(bookId) }.getOrElse { emptyList() }
        val byParent = all.groupBy { it.parentId ?: -1L }
        val roots = byParent[-1L] ?: all.filter { it.parentId == null }
        val children = all.filter { it.parentId != null }.groupBy { it.parentId!! }
        return TocTree(roots, children)
    }

    private suspend fun rebuildAggregatesFromResults(results: List<SearchResult>) {
        // Rebuild category/book aggregates
        categoryCountsAcc.clear()
        bookCountsAcc.clear()
        booksForCategoryAcc.clear()
        updateAggregatesForPage(results)
        // Rebuild TOC aggregates only if a book scope is selected
        uiState.value.scopeBook?.id?.let { recomputeTocCountsForBook(it, results) }
    }
}
