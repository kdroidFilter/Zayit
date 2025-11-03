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
import io.github.kdroidfilter.seforimapp.framework.search.LuceneSearchService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.drop
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

private const val PARALLEL_FILTER_THRESHOLD = 2_000

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
    private val lucene: LuceneSearchService,
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
        private const val STEADY_BATCH_SIZE = 100
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
    private val depthByBookId: MutableMap<Long, Int> = mutableMapOf()
    private val exactRawMatchByLineId: MutableMap<Long, Boolean> = mutableMapOf()

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
    private val cacheMutex = Mutex()

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
        combine(scopeTocIdFlow, scopeBookIdFlow) { tocId, bookId -> tocId to bookId }
            .debounce(100)
            .mapLatest { (tocId, bookId) ->
                if (tocId == null) return@mapLatest emptySet<Long>()
                val bid = bookId ?: runCatching { repository.getTocEntry(tocId)?.bookId }.getOrNull()
                if (bid == null) return@mapLatest emptySet<Long>()
                ensureTocCountingCaches(bid)
                collectLineIdsForTocSubtree(tocId, bid)
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
                    tocId != null -> results.parallelFilterCores { it.lineId in allowedLineIds }
                    bookId != null -> results.parallelFilterCores { it.bookId == bookId }
                    allowedBooks.isNotEmpty() -> results.parallelFilterCores { it.bookId in allowedBooks }
                    else -> results
                }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Emits true whenever a filter key changes (category/book/toc), and becomes false
    // after the next visibleResultsFlow emission reflecting that change.
    private val filterKeyFlow = combine(scopeBookIdFlow, scopeCatIdFlow, scopeTocIdFlow) { bookId, catId, tocId ->
        Triple(bookId, catId, tocId)
    }.distinctUntilChanged()

    val isFilteringFlow: StateFlow<Boolean> =
        filterKeyFlow
            .drop(1) // ignore initial state on first subscription
            .flatMapLatest {
                kotlinx.coroutines.flow.flow {
                    emit(true)
                    // Wait for the next recomputation of visible results
                    visibleResultsFlow.drop(1).first()
                    emit(false)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Category and TOC aggregates are updated incrementally from fetch loops
    val categoryAggFlow: StateFlow<CategoryAgg> = _categoryAgg.asStateFlow()
    val tocCountsFlow: StateFlow<Map<Long, Int>> = _tocCounts.asStateFlow()

    private val _tocTree = MutableStateFlow<TocTree?>(null)
    val tocTreeFlow: StateFlow<TocTree?> = _tocTree.asStateFlow()

    // Helper to combine 4 values strongly typed
    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
    private data class Quint<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

    private var currentTocBookId: Long? = null

    // Bulk caches for TOC counting within a scoped book
    private var cachedCountsBookId: Long? = null
    private var lineIdToTocId: Map<Long, Long> = emptyMap()
    private var tocParentById: Map<Long, Long?> = emptyMap()
    private val countsMutex = Mutex()

    init {
        // Prefer TabStateManager value (persisted across sessions) over the nav argument,
        // so the top bar reflects the most recent query even if the destination query is stale.
        val initialQuery = stateManager.getState<String>(tabId, SearchStateKeys.QUERY)
            ?: savedStateHandle.get<String>("searchQuery")
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
        // Try memory cache first, then persistent cache on disk
        val cached = SearchTabCache.get(tabId) ?: SearchTabPersistentCache.load(tabId)?.also { SearchTabCache.put(tabId, it) }
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

        // On tab switch: only interrupt search when RAM saver is enabled; always cancel on tab close
        viewModelScope.launch {
            // Observe currently selected tab to pause/resume
            tabsViewModel.selectedTabIndex.collect { idx ->
                val tabs = tabsViewModel.tabs.value
                val selectedTabId = tabs.getOrNull(idx)?.destination?.tabId
                if (selectedTabId != tabId) {
                    val ramSaver = AppSettings.isRamSaverEnabled()
                    if (ramSaver) {
                        // Pause current search when RAM saver is active
                        if (_uiState.value.isLoading || _uiState.value.isLoadingMore) {
                            wasPausedByTabSwitch = true
                            currentJob?.cancel()
                            _uiState.value = _uiState.value.copy(isLoading = false, isLoadingMore = false)
                        }
                        // Save a fresh snapshot (cropped) for instant restoration
                        val snap = buildSnapshot(_uiState.value.results)
                        SearchTabCache.put(tabId, snap)
                        SearchTabPersistentCache.save(tabId, snap)
                    }
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
                    SearchTabPersistentCache.clear(tabId)
                }
            }
        }
    }

    // Caching continuation removed: searches are executed fresh.

    fun setNear(near: Int) {
        _uiState.value = _uiState.value.copy(near = near)
        stateManager.saveState(tabId, SearchStateKeys.NEAR, near)
    }

    /**
     * Update the search query in UI state and persist it for this tab.
     * Does not trigger a search by itself; callers should invoke [executeSearch].
     */
    fun setQuery(query: String) {
        val q = query.trim()
        _uiState.value = _uiState.value.copy(query = q)
        stateManager.saveState(tabId, SearchStateKeys.QUERY, q)
        if (q.isNotEmpty()) {
            // Keep the tab title synced with the current query
            titleUpdateManager.updateTabTitle(tabId, q, TabType.SEARCH)
        }
    }

    fun executeSearch() {
        val q = _uiState.value.query.trim()
        if (q.isBlank()) return
        // New search: clear any previous streaming job and reset scroll/anchor state
        currentJob?.cancel()
        // Reset persisted scroll/anchor so restoration targets the top for fresh results
        stateManager.saveState(tabId, SearchStateKeys.SCROLL_INDEX, 0)
        stateManager.saveState(tabId, SearchStateKeys.SCROLL_OFFSET, 0)
        stateManager.saveState(tabId, SearchStateKeys.ANCHOR_ID, -1L)
        stateManager.saveState(tabId, SearchStateKeys.ANCHOR_INDEX, 0)
        // Reflect the reset in the in-memory UI state immediately
        _uiState.value = _uiState.value.copy(
            scrollIndex = 0,
            scrollOffset = 0,
            anchorId = -1L,
            anchorIndex = 0
        )
        // Drop any cached snapshot for this tab to avoid restoring stale results
        SearchTabCache.clear(tabId)
        SearchTabPersistentCache.clear(tabId)
        currentJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            _uiState.value = _uiState.value.copy(isLoading = true, results = emptyList(), hasMore = false, progressCurrent = 0, progressTotal = null)
            // Reset aggregates and counts for a clean run
            countsMutex.withLock {
                categoryCountsAcc.clear()
                bookCountsAcc.clear()
                booksForCategoryAcc.clear()
                tocCountsAcc.clear()
                _categoryAgg.value = CategoryAgg(emptyMap(), emptyMap(), emptyMap())
                _tocCounts.value = emptyMap()
            }
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

                // Lucene: we don't pre-count; keep indeterminate until results stream
                val initialProgressTotal: Long? = null
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
                    // Ensure bulk caches for counts are ready for this book
                    ensureTocCountingCaches(book.id)
                }

                // Coalesce UI updates to reduce recompositions and copying
                var lastEmitNanos = 0L
                var lastEmittedSize = 0
                val EMIT_MIN_INTERVAL_NS = 75L * 1_000_000L
                val EMIT_RESULTS_STEP = 250
                fun maybeEmitUpdate(force: Boolean = false) {
                    val now = System.nanoTime()
                    val sizeDelta = acc.size - lastEmittedSize
                    if (force || sizeDelta >= EMIT_RESULTS_STEP || (now - lastEmitNanos) >= EMIT_MIN_INTERVAL_NS) {
                        // Sort by rank desc, then exact raw match, then category depth asc
                        val sorted = acc.sortedWith(compareByDescending<SearchResult> { it.rank }
                            .thenByDescending { exactRawMatchByLineId[it.lineId] == true }
                            .thenBy { depthByBookId[it.bookId] ?: Int.MAX_VALUE }
                            .thenBy { it.lineIndex })
                        _uiState.value = _uiState.value.copy(
                            results = sorted,
                            scrollToAnchorTimestamp = System.currentTimeMillis(),
                            progressCurrent = acc.size
                        )
                        lastEmitNanos = now
                        lastEmittedSize = acc.size
                    }
                }

                when {
                    fetchTocId != null && fetchTocId > 0 -> {
                        val toc = repository.getTocEntry(fetchTocId)
                    if (toc != null) {
                        // Bulk: compute allowed lineIds using preloaded caches for this book
                        ensureTocCountingCaches(toc.bookId)
                        val allowedLineIds = collectLineIdsForTocSubtree(toc.id, toc.bookId)
                        val bookId = toc.bookId
                        var offset = 0
                        // Prime first page
                        var hits = lucene.searchInBook(q, near, bookId, limit = batchSizeFor(acc.size), offset = offset)
                        while (hits.isNotEmpty()) {
                            val currentHits = hits
                            val nextOffset = offset + currentHits.size
                            // Prefetch next page in parallel while we process current
                            val nextDeferred = async(Dispatchers.Default) {
                                lucene.searchInBook(q, near, bookId, limit = batchSizeFor(acc.size + currentHits.size), offset = nextOffset)
                            }
                            // Process current page
                            val page = toResultsWithDbSnippets(currentHits, q, near)
                            val filtered = page.filter { it.lineId in allowedLineIds }
                            acc += filtered
                            updateAggregatesForPage(filtered)
                            uiState.value.scopeBook?.id?.let { updateTocCountsForPage(filtered, it) }
                            offset = nextOffset
                            maybeEmitUpdate()
                            // Advance
                            hits = nextDeferred.await()
                        }
                        }
                    }
                    fetchBookId != null && fetchBookId > 0 -> {
                        var offset = 0
                        var hits = lucene.searchInBook(q, near, fetchBookId, limit = batchSizeFor(acc.size), offset = offset)
                        while (hits.isNotEmpty()) {
                            val currentHits = hits
                            val nextOffset = offset + currentHits.size
                            val nextDeferred = async(Dispatchers.Default) {
                                lucene.searchInBook(q, near, fetchBookId, limit = batchSizeFor(acc.size + currentHits.size), offset = nextOffset)
                            }
                            val page = toResultsWithDbSnippets(currentHits, q, near)
                            acc += page
                            updateAggregatesForPage(page)
                            uiState.value.scopeBook?.id?.let { updateTocCountsForPage(page, it) }
                            offset = nextOffset
                            maybeEmitUpdate()
                            hits = nextDeferred.await()
                        }
                    }
                    fetchCategoryId != null && fetchCategoryId > 0 -> {
                        // Expand to all books under the category tree
                        val bookIdsUnder = collectBookIdsUnderCategory(fetchCategoryId)
                        if (bookIdsUnder.isEmpty()) {
                            // Nothing to search in
                        } else {
                            var offset = 0
                            var hits = lucene.searchInBooks(q, near, bookIdsUnder, limit = batchSizeFor(acc.size), offset = offset)
                            while (hits.isNotEmpty()) {
                                val currentHits = hits
                                val nextOffset = offset + currentHits.size
                                val nextDeferred = async(Dispatchers.Default) {
                                    lucene.searchInBooks(q, near, bookIdsUnder, limit = batchSizeFor(acc.size + currentHits.size), offset = nextOffset)
                                }
                                val page = toResultsWithDbSnippets(currentHits, q, near)
                                acc += page
                                updateAggregatesForPage(page)
                                uiState.value.scopeBook?.id?.let { updateTocCountsForPage(page, it) }
                                offset = nextOffset
                                maybeEmitUpdate()
                                hits = nextDeferred.await()
                            }
                        }
                    }
                    else -> {
                        var offset = 0
                        var hits = lucene.searchAllText(q, near, limit = batchSizeFor(acc.size), offset = offset)
                        while (hits.isNotEmpty()) {
                            val currentHits = hits
                            val nextOffset = offset + currentHits.size
                            val nextDeferred = async(Dispatchers.Default) {
                                lucene.searchAllText(q, near, limit = batchSizeFor(acc.size + currentHits.size), offset = nextOffset)
                            }
                            val page = toResultsWithDbSnippets(currentHits, q, near)
                            acc += page
                            updateAggregatesForPage(page)
                            uiState.value.scopeBook?.id?.let { updateTocCountsForPage(page, it) }
                            offset = nextOffset
                            maybeEmitUpdate()
                            hits = nextDeferred.await()
                        }
                    }
                }

                // No more pages left; we fetched everything for this query
                val hasMore = false
                maybeEmitUpdate(force = true)
                _uiState.value = _uiState.value.copy(hasMore = hasMore, progressCurrent = acc.size)
                // Persist a snapshot of the full results so cold-boot restore is instant
                runCatching {
                    val snap = buildSnapshot(_uiState.value.results)
                    SearchTabCache.put(tabId, snap)
                    SearchTabPersistentCache.save(tabId, snap)
                }

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
            val snap = buildSnapshot(uiState.value.results)
            SearchTabCache.put(tabId, snap)
            SearchTabPersistentCache.save(tabId, snap)
        } else {
            SearchTabCache.clear(tabId)
            SearchTabPersistentCache.clear(tabId)
        }
        cancelSearch()
    }

    private fun buildSnapshot(results: List<SearchResult>): SearchTabCache.Snapshot {
        val catAgg = _categoryAgg.value
        val treeSnap = _tocTree.value?.let { t ->
            SearchTabCache.TocTreeSnapshot(t.rootEntries, t.children)
        }
        return SearchTabCache.Snapshot(
            results = results,
            categoryAgg = SearchTabCache.CategoryAggSnapshot(
                categoryCounts = catAgg.categoryCounts,
                bookCounts = catAgg.bookCounts,
                booksForCategory = catAgg.booksForCategory
            ),
            tocCounts = _tocCounts.value,
            tocTree = treeSnap
        )
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

        val parallel = results.size >= PARALLEL_FILTER_THRESHOLD && Runtime.getRuntime().availableProcessors() > 1
        if (parallel) {
            // Process results in parallel chunks to speed up counting and resolution
            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
            val total = results.size
            val chunkSize = (total + cores - 1) / cores
            val snapshots = coroutineScope {
                val tasks = mutableListOf<kotlinx.coroutines.Deferred<Quad<Map<Long, Int>, Map<Long, Book>, Map<Long, Int>, Map<Long, Category>>>>()
                var start = 0
                while (start < total) {
                    val s = start
                    val e = kotlin.math.min(start + chunkSize, total)
                    tasks += async(Dispatchers.Default) {
                        val localBookCounts = mutableMapOf<Long, Int>()
                        val localBooksById = mutableMapOf<Long, Book>()
                        val localCategoryCounts = mutableMapOf<Long, Int>()
                        val localCategoriesById = mutableMapOf<Long, Category>()
                        // local caches to avoid duplicate repo hits within chunk
                        val localBookCache = mutableMapOf<Long, Book>()
                        val localCatPathCache = mutableMapOf<Long, List<Category>>()

                        suspend fun resolveBook(bookId: Long): Book? {
                            localBooksById[bookId]?.let { return it }
                            localBookCache[bookId]?.let { return it }
                            val inGlobal = bookCache[bookId]
                            if (inGlobal != null) return inGlobal
                            val obj = repository.getBook(bookId)
                            if (obj != null) {
                                localBookCache[bookId] = obj
                            }
                            return obj
                        }

                        suspend fun resolveCategoryPath(categoryId: Long): List<Category> {
                            localCatPathCache[categoryId]?.let { return it }
                            val inGlobal = categoryPathCache[categoryId]
                            if (inGlobal != null) return inGlobal
                            val built = buildCategoryPath(categoryId)
                            localCatPathCache[categoryId] = built
                            return built
                        }

                        var i = s
                        while (i < e) {
                            val res = results[i]
                            val book = resolveBook(res.bookId)
                            if (book != null) {
                                localBooksById[book.id] = book
                                localBookCounts[book.id] = (localBookCounts[book.id] ?: 0) + 1
                                val path = resolveCategoryPath(book.categoryId)
                                for (cat in path) {
                                    localCategoriesById[cat.id] = cat
                                    localCategoryCounts[cat.id] = (localCategoryCounts[cat.id] ?: 0) + 1
                                }
                            }
                            i++
                        }
                        Quad(localBookCounts, localBooksById, localCategoryCounts, localCategoriesById)
                    }
                    start = e
                }
                tasks.awaitAll()
            }
            // Merge snapshots into global maps (single-threaded merge)
            for (snap in snapshots) {
                for ((k, v) in snap.a) bookCounts[k] = (bookCounts[k] ?: 0) + v
                for ((k, v) in snap.b) booksById.putIfAbsent(k, v)
                for ((k, v) in snap.c) categoryCounts[k] = (categoryCounts[k] ?: 0) + v
                for ((k, v) in snap.d) categoriesById.putIfAbsent(k, v)
            }
        } else {
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
            // Refresh TOC tree for this book and ensure counting caches
            if (book != null && currentTocBookId != book.id) {
                val tree = runCatching { buildTocTreeForBook(book.id) }.getOrNull()
                if (tree != null) {
                    _tocTree.value = tree
                    currentTocBookId = book.id
                }
            }
            if (book != null) ensureTocCountingCaches(book.id)
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
            // Refresh TOC tree for this book and ensure counting caches
            if (scopeBook != null && currentTocBookId != scopeBook.id) {
                val tree = runCatching { buildTocTreeForBook(scopeBook.id) }.getOrNull()
                if (tree != null) {
                    _tocTree.value = tree
                    currentTocBookId = scopeBook.id
                }
            }
            scopeBook?.let { ensureTocCountingCaches(it.id) }

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
            val bookId = scopeBook?.id
                ?: runCatching { repository.getTocEntry(scopeToc)?.bookId }.getOrNull()
                ?: return all
            ensureTocCountingCaches(bookId)
            val allowedLineIds = collectLineIdsForTocSubtree(scopeToc, bookId)
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
        // Bulk: single pass using closure table + join in BookQueries
        val books = runCatching { repository.getBooksUnderCategoryTree(categoryId) }.getOrDefault(emptyList())
        return books.mapTo(mutableSetOf()) { it.id }
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

    /**
     * Convert Lucene hits to SearchResult items, fetching snippet text from the DB.
     * We do not store raw line text in the Lucene index, so for display we read
     * the original HTML line content from SQLite via the repository.
     */
    private suspend fun toResultsWithDbSnippets(
        hits: List<io.github.kdroidfilter.seforimapp.framework.search.LuceneSearchService.LineHit>,
        rawQuery: String,
        near: Int
    ): List<SearchResult> {
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val shouldParallelize = hits.size >= PARALLEL_FILTER_THRESHOLD && cores > 1
        if (!shouldParallelize) {
            val out = ArrayList<SearchResult>(hits.size)
            for (hit in hits) {
                val raw = runCatching { repository.getLine(hit.lineId)?.content }.getOrNull() ?: ""
                val rawCleanSingle = Jsoup.clean(raw, Safelist.none())
                val minSourceLen = 280
                val neighborWindow = 4
                val aggregateSource: String = if (rawCleanSingle.length >= minSourceLen) rawCleanSingle else run {
                    val start = (hit.lineIndex - neighborWindow).coerceAtLeast(0)
                    val end = hit.lineIndex + neighborWindow
                    val neighbors = runCatching { repository.getLines(hit.bookId, start, end) }.getOrDefault(emptyList())
                    if (neighbors.isNotEmpty()) neighbors.joinToString(" ") { Jsoup.clean(it.content, Safelist.none()) } else rawCleanSingle
                }
                val snippet = runCatching { lucene.buildSnippetFromRaw(aggregateSource, rawQuery, near) }.getOrDefault(rawCleanSingle)
                if (depthByBookId[hit.bookId] == null) {
                    val catId = runCatching { repository.getBook(hit.bookId)?.categoryId }.getOrNull()
                    if (catId != null) {
                        val depth = runCatching { repository.getCategoryDepth(catId) }.getOrDefault(Int.MAX_VALUE)
                        depthByBookId[hit.bookId] = depth
                    }
                }
                val isExact = rawQuery.trim().let { q -> q.isNotEmpty() && rawCleanSingle.contains(q) }
                exactRawMatchByLineId[hit.lineId] = isExact
                val scoreBoost = if (isExact) 1e-3 else 0.0
                out += SearchResult(
                    bookId = hit.bookId,
                    bookTitle = hit.bookTitle,
                    lineId = hit.lineId,
                    lineIndex = hit.lineIndex,
                    snippet = snippet,
                    rank = hit.score.toDouble() + scoreBoost
                )
            }
            return out
        }

        // Parallel path
        val maxWorkers = cores.coerceAtMost(8)
        val total = hits.size
        val chunkSize = (total + maxWorkers - 1) / maxWorkers
        val chunks = (0 until total step chunkSize).map { start -> start until kotlin.math.min(start + chunkSize, total) }
        val results = coroutineScope {
            val tasks = chunks.map { range ->
                async(Dispatchers.Default) {
                    val localItems = ArrayList<SearchResult>(range.last - range.first + 1)
                    val localExact = HashMap<Long, Boolean>()
                    val localDepth = HashMap<Long, Int>()
                    for (i in range) {
                        val hit = hits[i]
                        val raw = runCatching { repository.getLine(hit.lineId)?.content }.getOrNull() ?: ""
                        val rawCleanSingle = Jsoup.clean(raw, Safelist.none())
                        val minSourceLen = 280
                        val neighborWindow = 4
                        val aggregateSource: String = if (rawCleanSingle.length >= minSourceLen) rawCleanSingle else run {
                            val start = (hit.lineIndex - neighborWindow).coerceAtLeast(0)
                            val end = hit.lineIndex + neighborWindow
                            val neighbors = runCatching { repository.getLines(hit.bookId, start, end) }.getOrDefault(emptyList())
                            if (neighbors.isNotEmpty()) neighbors.joinToString(" ") { Jsoup.clean(it.content, Safelist.none()) } else rawCleanSingle
                        }
                        val snippet = runCatching { lucene.buildSnippetFromRaw(aggregateSource, rawQuery, near) }.getOrDefault(rawCleanSingle)
                        if (depthByBookId[hit.bookId] == null && !localDepth.containsKey(hit.bookId)) {
                            val catId = runCatching { repository.getBook(hit.bookId)?.categoryId }.getOrNull()
                            if (catId != null) {
                                val depth = runCatching { repository.getCategoryDepth(catId) }.getOrDefault(Int.MAX_VALUE)
                                localDepth[hit.bookId] = depth
                            }
                        }
                        val isExact = rawQuery.trim().let { q -> q.isNotEmpty() && rawCleanSingle.contains(q) }
                        localExact[hit.lineId] = isExact
                        val scoreBoost = if (isExact) 1e-3 else 0.0
                        localItems += SearchResult(
                            bookId = hit.bookId,
                            bookTitle = hit.bookTitle,
                            lineId = hit.lineId,
                            lineIndex = hit.lineIndex,
                            snippet = snippet,
                            rank = hit.score.toDouble() + scoreBoost
                        )
                    }
                    Triple(localItems, localExact, localDepth)
                }
            }
            tasks.awaitAll()
        }
        // Merge caches atomically
        cacheMutex.withLock {
            for ((_, exact, depth) in results) {
                exactRawMatchByLineId.putAll(exact)
                for ((b, d) in depth) if (!depthByBookId.containsKey(b)) depthByBookId[b] = d
            }
        }
        // Flatten items preserving chunk order
        return results.flatMap { it.first }
    }

    private suspend fun collectLineIdsForTocSubtree(tocId: Long, bookId: Long): Set<Long> {
        // Build the set of tocEntry ids in the subtree
        val subtreeTocIds = getTocSubtreeTocIds(tocId, bookId)
        if (subtreeTocIds.isEmpty()) return emptySet()
        // Use preloaded lineId -> tocId mapping to collect all lineIds for these tocIds
        return lineIdToTocId.filterValues { it in subtreeTocIds }.keys
    }

    private suspend fun getTocSubtreeTocIds(rootTocId: Long, bookId: Long): Set<Long> {
        val result = mutableSetOf<Long>()
        // Prefer the in-memory TOC tree if it matches the book
        val tree = _tocTree.value
        if (tree != null && currentTocBookId == bookId) {
            val childrenMap = tree.children
            fun dfs(id: Long) {
                result += id
                val children = childrenMap[id].orEmpty()
                for (child in children) dfs(child.id)
            }
            dfs(rootTocId)
            return result
        }
        // Fallback: build children map from repository for the book and DFS
        val all = runCatching { repository.getBookToc(bookId) }.getOrElse { emptyList() }
        val byParent = all.filter { it.parentId != null }.groupBy { it.parentId!! }
        fun dfs(id: Long) {
            result += id
            val children = byParent[id].orEmpty()
            for (child in children) dfs(child.id)
        }
        dfs(rootTocId)
        return result
    }

    private suspend fun updateAggregatesForPage(page: List<SearchResult>) {
        countsMutex.withLock {
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
    }

    private suspend fun updateTocCountsForPage(page: List<SearchResult>, scopeBookId: Long) {
        val subset = page.filter { it.bookId == scopeBookId }
        if (subset.isEmpty()) return
        // Ensure caches match the scoped book
        ensureTocCountingCaches(scopeBookId)
        countsMutex.withLock {
            for (res in subset) {
                val tocId = lineIdToTocId[res.lineId] ?: continue
                var current: Long? = tocId
                var guard = 0
                while (current != null && guard++ < 500) {
                    tocCountsAcc[current] = (tocCountsAcc[current] ?: 0) + 1
                    current = tocParentById[current]
                }
            }
            _tocCounts.value = tocCountsAcc.toMap()
        }
    }

    private suspend fun recomputeTocCountsForBook(bookId: Long, results: List<SearchResult>) {
        countsMutex.withLock {
            tocCountsAcc.clear()
        }
        updateTocCountsForPage(results, bookId)
    }

    private suspend fun ensureTocCountingCaches(bookId: Long) {
        if (cachedCountsBookId == bookId && lineIdToTocId.isNotEmpty() && tocParentById.isNotEmpty()) return
        // Build lineId -> tocId map for the book
        val mappings = runCatching { repository.getLineTocMappingsForBook(bookId) }.getOrElse { emptyList() }
        lineIdToTocId = mappings.associate { it.lineId to it.tocEntryId }
        // Build tocId -> parentId map
        val parentMap = mutableMapOf<Long, Long?>()
        val tree = _tocTree.value
        if (tree != null && currentTocBookId == bookId) {
            // Build parent map from existing cached tree
            fun dfs(parentId: Long?, entries: List<TocEntry>) {
                for (e in entries) {
                    parentMap[e.id] = parentId
                    val children = tree.children[e.id].orEmpty()
                    if (children.isNotEmpty()) dfs(e.id, children)
                }
            }
            dfs(null, tree.rootEntries)
        } else {
            val toc = runCatching { repository.getBookToc(bookId) }.getOrElse { emptyList() }
            toc.forEach { entry -> parentMap[entry.id] = entry.parentId }
        }
        tocParentById = parentMap
        cachedCountsBookId = bookId
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
        countsMutex.withLock {
            categoryCountsAcc.clear()
            bookCountsAcc.clear()
            booksForCategoryAcc.clear()
        }
        updateAggregatesForPage(results)
        // Rebuild TOC aggregates only if a book scope is selected
        uiState.value.scopeBook?.id?.let { recomputeTocCountsForBook(it, results) }
    }
}

// In-file helpers for efficient parallel filtering on CPU cores
private suspend fun <T> List<T>.parallelFilterCores(predicate: (T) -> Boolean): List<T> {
    val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    if (this.size < PARALLEL_FILTER_THRESHOLD || cores == 1) {
        return this.filter(predicate)
    }
    val total = this.size
    val chunkSize = (total + cores - 1) / cores
    return coroutineScope {
        val tasks = mutableListOf<kotlinx.coroutines.Deferred<List<T>>>()
        var start = 0
        while (start < total) {
            val s = start
            val e = kotlin.math.min(start + chunkSize, total)
            tasks += async(Dispatchers.Default) {
                val sub = ArrayList<T>(e - s)
                var i = s
                while (i < e) {
                    val v = this@parallelFilterCores[i]
                    if (predicate(v)) sub.add(v)
                    i++
                }
                sub
            }
            start = e
        }
        tasks.awaitAll().flatten()
    }
}
