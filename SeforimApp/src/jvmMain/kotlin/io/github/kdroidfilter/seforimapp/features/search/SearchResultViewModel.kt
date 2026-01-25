@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)

package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactoryKey
import io.github.kdroidfilter.seforim.tabs.*
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.StateKeys
import io.github.kdroidfilter.seforimapp.features.search.domain.BuildSearchTreeUseCase
import io.github.kdroidfilter.seforimapp.features.search.domain.GetBreadcrumbPiecesUseCase
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.kdroidfilter.seforimapp.framework.session.SearchPersistedState
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Arrays
import java.util.UUID
import kotlin.collections.ArrayDeque

private const val PARALLEL_FILTER_THRESHOLD = 2_000
private const val LAZY_PAGE_SIZE = 25

@Stable
data class SearchUiState(
    val query: String = "",
    val globalExtended: Boolean = false,
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
    val progressTotal: Long? = null,
)

@AssistedInject
class SearchResultViewModel(
    @Assisted savedStateHandle: SavedStateHandle,
    private val persistedStore: TabPersistedStateStore,
    private val repository: SeforimRepository,
    private val lucene: SearchEngine,
    private val titleUpdateManager: TabTitleUpdateManager,
    private val tabsViewModel: TabsViewModel,
) : ViewModel() {
    @AssistedFactory
    @ViewModelAssistedFactoryKey(SearchResultViewModel::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ViewModelAssistedFactory {
        override fun create(extras: CreationExtras): SearchResultViewModel = create(extras.createSavedStateHandle())

        fun create(
            @Assisted savedStateHandle: SavedStateHandle,
        ): SearchResultViewModel
    }

    internal val tabId: String = savedStateHandle.get<String>(StateKeys.TAB_ID) ?: ""

    private fun persistedSearchState(): SearchPersistedState = persistedStore.get(tabId)?.search ?: SearchPersistedState()

    private fun updatePersistedSearch(transform: (SearchPersistedState) -> SearchPersistedState) {
        persistedStore.update(tabId) { current ->
            val next = transform(current.search ?: SearchPersistedState())
            current.copy(search = next)
        }
    }

    private val getBreadcrumbPieces = GetBreadcrumbPiecesUseCase(repository)
    private val buildSearchTreeUseCase = BuildSearchTreeUseCase(repository)

    // MVI events for SearchResultViewModel
    sealed class SearchResultEvents {
        data class SetCategoryChecked(
            val categoryId: Long,
            val checked: Boolean,
        ) : SearchResultEvents()

        data class SetBookChecked(
            val bookId: Long,
            val checked: Boolean,
        ) : SearchResultEvents()

        data class SetTocChecked(
            val tocId: Long,
            val checked: Boolean,
        ) : SearchResultEvents()

        data class EnsureScopeBookForToc(
            val bookId: Long,
        ) : SearchResultEvents()

        data object ClearScopeBookIfNoneChecked : SearchResultEvents()

        data class FilterByTocId(
            val tocId: Long,
        ) : SearchResultEvents()

        data class FilterByBookId(
            val bookId: Long,
        ) : SearchResultEvents()

        data class FilterByCategoryId(
            val categoryId: Long,
        ) : SearchResultEvents()

        data class SetQuery(
            val query: String,
        ) : SearchResultEvents()

        data object ExecuteSearch : SearchResultEvents()

        data object CancelSearch : SearchResultEvents()

        data class OnScroll(
            val anchorId: Long,
            val anchorIndex: Int,
            val index: Int,
            val offset: Int,
        ) : SearchResultEvents()

        data class OpenResult(
            val result: SearchResult,
            val openInNewTab: Boolean,
        ) : SearchResultEvents()

        data class RequestBreadcrumb(
            val result: SearchResult,
        ) : SearchResultEvents()

        // Hint from UI about visibility, to gate heavy computations (e.g., search tree)
        data class SetUiVisible(
            val visible: Boolean,
        ) : SearchResultEvents()

        // Toggle global search scope (base-only vs extended)
        data class SetGlobalExtended(
            val extended: Boolean,
        ) : SearchResultEvents()
    }

    fun onEvent(event: SearchResultEvents) {
        when (event) {
            is SearchResultEvents.SetCategoryChecked -> setCategoryChecked(event.categoryId, event.checked)
            is SearchResultEvents.SetBookChecked -> setBookChecked(event.bookId, event.checked)
            is SearchResultEvents.SetTocChecked -> setTocChecked(event.tocId, event.checked)
            is SearchResultEvents.EnsureScopeBookForToc -> ensureScopeBookForToc(event.bookId)
            is SearchResultEvents.ClearScopeBookIfNoneChecked -> clearScopeBookIfNoBookCheckboxSelected()
            is SearchResultEvents.FilterByTocId -> filterByTocId(event.tocId)
            is SearchResultEvents.FilterByBookId -> filterByBookId(event.bookId)
            is SearchResultEvents.FilterByCategoryId -> filterByCategoryId(event.categoryId)
            is SearchResultEvents.SetQuery -> setQuery(event.query)
            is SearchResultEvents.ExecuteSearch -> executeSearch()
            is SearchResultEvents.CancelSearch -> cancelSearch()
            is SearchResultEvents.OnScroll -> onScroll(event.anchorId, event.anchorIndex, event.index, event.offset)
            is SearchResultEvents.OpenResult -> openResult(event.result, event.openInNewTab)
            is SearchResultEvents.RequestBreadcrumb ->
                viewModelScope.launch {
                    val pieces = runCatching { getBreadcrumbPiecesFor(event.result) }.getOrDefault(emptyList())
                    if (pieces.isNotEmpty()) {
                        val next = _breadcrumbs.value + (event.result.lineId to pieces)
                        _breadcrumbs.value = next
                        updatePersistedSearch { it.copy(breadcrumbs = next) }
                    }
                }
            is SearchResultEvents.SetUiVisible -> _uiVisible.value = event.visible
            is SearchResultEvents.SetGlobalExtended -> {
                _uiState.value = _uiState.value.copy(globalExtended = event.extended)
                updatePersistedSearch { it.copy(globalExtended = event.extended) }
            }
        }
    }

    // Key representing the current search parameters (no result caching).
    private data class SearchParamsKey(
        val query: String,
        val filterCategoryId: Long?,
        val filterBookId: Long?,
        val filterTocId: Long?,
    )

    // Batching policy:
    // - 20 for the first 100 results (best time-to-first-results)
    // - then 100 until 500
    // - then 5,000; then 10,000; then double each step up to 200,000
    private companion object {
        private const val DEFAULT_NEAR = 5
        private const val STAGE1_LIMIT = 100
        private const val STAGE2_LIMIT = 500
        private const val STAGE3_LIMIT = 5_000
        private const val STAGE4_LIMIT = 10_000
        private const val STAGE5_LIMIT = 20_000
        private const val STAGE6_LIMIT = 40_000
        private const val STAGE7_LIMIT = 80_000
        private const val STAGE8_LIMIT = 160_000

        private const val STAGE1_BATCH = 20
        private const val STAGE2_BATCH = 100
        private const val STAGE3_BATCH = 5_000
        private const val STAGE4_BATCH = 10_000
        private const val STAGE5_BATCH = 20_000
        private const val STAGE6_BATCH = 40_000
        private const val STAGE7_BATCH = 80_000
        private const val STAGE8_BATCH = 160_000
        private const val STAGE9_BATCH = 200_000
    }

    private fun batchSizeFor(
        currentCount: Int,
        warmup: Boolean = false,
    ): Int =
        when {
            warmup -> STAGE1_BATCH
            currentCount < STAGE1_LIMIT -> STAGE1_BATCH
            currentCount < STAGE2_LIMIT -> STAGE2_BATCH
            currentCount < STAGE3_LIMIT -> STAGE3_BATCH
            currentCount < STAGE4_LIMIT -> STAGE4_BATCH
            currentCount < STAGE5_LIMIT -> STAGE5_BATCH
            currentCount < STAGE6_LIMIT -> STAGE6_BATCH
            currentCount < STAGE7_LIMIT -> STAGE7_BATCH
            currentCount < STAGE8_LIMIT -> STAGE8_BATCH
            else -> STAGE9_BATCH
        }

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    private var currentJob: Job? = null

    // Lazy loading: keep session open for on-demand pagination
    private var currentSession: SearchSession? = null
    private var currentTocAllowedLineIds: Set<Long> = emptySet()
    private var currentSearchQuery: String = ""
    private val lazyLoadMutex = Mutex()

    // Pagination cursors/state
    private var currentKey: SearchParamsKey? = null

    // Caches to speed up breadcrumb building for search results
    private val bookCache: MutableMap<Long, Book> = mutableMapOf()
    private val categoryPathCache: MutableMap<Long, List<Category>> = mutableMapOf()
    private val tocPathCache: MutableMap<Long, List<TocEntry>> = mutableMapOf()

    // Data structures for results tree
    data class SearchTreeBook(
        val book: Book,
        val count: Int,
    )

    data class SearchTreeCategory(
        val category: Category,
        val count: Int,
        val children: List<SearchTreeCategory>,
        val books: List<SearchTreeBook>,
    )

    data class TocTree(
        val rootEntries: List<TocEntry>,
        val children: Map<Long, List<TocEntry>>,
    )

    data class CategoryAgg(
        val categoryCounts: Map<Long, Int>,
        val bookCounts: Map<Long, Int>,
        val booksForCategory: Map<Long, List<Book>>,
    )

    // Aggregates accumulators used to update flows incrementally per fetched page
    private val categoryCountsAcc: MutableMap<Long, Int> = mutableMapOf()
    private val bookCountsAcc: MutableMap<Long, Int> = mutableMapOf()
    private val booksForCategoryAcc: MutableMap<Long, MutableSet<Book>> = mutableMapOf()
    private val tocCountsAcc: MutableMap<Long, Int> = mutableMapOf()
    private val countsMutex = Mutex()
    private val indexMutex = Mutex()

    private val _categoryAgg = MutableStateFlow(CategoryAgg(emptyMap(), emptyMap(), emptyMap()))
    private val _tocCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    private val _breadcrumbs = MutableStateFlow<Map<Long, List<String>>>(emptyMap())

    // Flag to indicate facets have been computed, so tree doesn't need to be rebuilt from results
    private var facetsComputed = false
    val breadcrumbsFlow: StateFlow<Map<Long, List<String>>> = _breadcrumbs.asStateFlow()

    // Whether the Search UI is currently visible/active. Used to gate heavy flows at startup.
    private val _uiVisible = MutableStateFlow(false)

    // Allowed sets computed only when scope changes (Debounce 300ms on scope)
    private val scopeBookIdFlow = uiState.map { it.scopeBook?.id }.distinctUntilChanged()
    private val scopeCatIdFlow = uiState.map { it.scopeCategoryPath.lastOrNull()?.id }.distinctUntilChanged()
    private val scopeTocIdFlow = uiState.map { it.scopeTocId }.distinctUntilChanged()

    // Use conditional debounce: no delay when null (to immediately clear filters)
    private val allowedBooksFlow: StateFlow<Set<Long>> =
        scopeCatIdFlow
            .debounce { catId -> if (catId == null) 0L else 100L }
            .mapLatest { catId ->
                if (catId == null) emptySet() else collectBookIdsUnderCategory(catId)
            }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // Multi-select filters (checkboxes)
    private val _selectedCategoryIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _selectedBookIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _selectedTocIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedCategoryIdsFlow: StateFlow<Set<Long>> = _selectedCategoryIds.asStateFlow()
    val selectedBookIdsFlow: StateFlow<Set<Long>> = _selectedBookIds.asStateFlow()
    val selectedTocIdsFlow: StateFlow<Set<Long>> = _selectedTocIds.asStateFlow()

    // Derived unions for multi-select
    // Use conditional debounce: no delay when empty (to immediately clear filters)
    private val multiAllowedBooksFlow: StateFlow<Set<Long>> =
        _selectedCategoryIds
            .debounce { ids -> if (ids.isEmpty()) 0L else 100L }
            .mapLatest { ids ->
                if (ids.isEmpty()) {
                    emptySet()
                } else {
                    val acc = mutableSetOf<Long>()
                    for (id in ids) {
                        acc += collectBookIdsUnderCategory(id)
                    }
                    acc
                }
            }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // Visible results update immediately per page; filtering uses precomputed allowed sets when available
    private val baseScopeFlow: StateFlow<Quad<List<SearchResult>, Long?, Set<Long>, Long?>> =
        combine(
            uiState.map { it.results },
            scopeBookIdFlow,
            allowedBooksFlow,
            scopeTocIdFlow,
        ) { results, bookId, allowedBooks, tocId ->
            Quad(results, bookId, allowedBooks, tocId)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Quad(emptyList(), null, emptySet(), null))

    private val extraMultiFlow: StateFlow<Triple<Set<Long>, Set<Long>, Set<Long>>> =
        combine(selectedBookIdsFlow, multiAllowedBooksFlow, selectedTocIdsFlow) { selBooks, multiBooks, selectedTocs ->
            Triple(selBooks, multiBooks, selectedTocs)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Triple(emptySet(), emptySet(), emptySet()))

    private val rawVisibleFlow: Flow<List<SearchResult>> =
        combine(baseScopeFlow, extraMultiFlow) { base, extra -> Pair(base, extra) }
            .distinctUntilChanged()
            .mapLatest { (base, extra) ->
                withContext(Dispatchers.Default) {
                    val results = base.a
                    val bookId = base.b
                    val allowedBooks = base.c
                    val tocId = base.d
                    val selectedBooks = extra.first
                    val multiBooks = extra.second
                    val multiLines = extra.third
                    val out =
                        fastFilterVisibleResults(
                            results = results,
                            bookId = bookId,
                            allowedBooks = allowedBooks,
                            tocActive = tocId != null,
                            selectedBooks = selectedBooks,
                            multiBooks = multiBooks,
                            selectedTocIds = multiLines,
                            scopeTocId = tocId,
                        )
                    ArrayList(out)
                }
            }

    val visibleResultsFlow: StateFlow<List<SearchResult>> =
        rawVisibleFlow
            .debounce { if (_uiState.value.isLoading) 0 else 50 }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Emits true whenever a filter key changes (category/book/toc), and becomes false
    // after the next visibleResultsFlow emission reflecting that change.
    private val filterKeyBase: StateFlow<Triple<Long?, Long?, Long?>> =
        combine(scopeBookIdFlow, scopeCatIdFlow, scopeTocIdFlow) { bookId, catId, tocId ->
            Triple(bookId, catId, tocId)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Triple(null, null, null))

    private val filterKeyExtra: StateFlow<Triple<Set<Long>, Set<Long>, Set<Long>>> =
        combine(selectedBookIdsFlow, selectedCategoryIdsFlow, selectedTocIdsFlow) { selBooks, selCats, selTocs ->
            Triple(selBooks, selCats, selTocs)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Triple(emptySet(), emptySet(), emptySet()))

    private val filterKeyFlow =
        combine(filterKeyBase, filterKeyExtra) { base, extra ->
            Sext(base.first, base.second, base.third, extra.first, extra.second, extra.third)
        }.distinctUntilChanged()

    val isFilteringFlow: StateFlow<Boolean> =
        filterKeyFlow
            .drop(1) // ignore initial state on first subscription
            .flatMapLatest {
                // Show overlay until visible results emission changes either size or identity
                val initial = Pair(visibleResultsFlow.value.size, System.identityHashCode(visibleResultsFlow.value))
                flow {
                    emit(true)
                    visibleResultsFlow
                        .map { Pair(it.size, System.identityHashCode(it)) }
                        .distinctUntilChanged()
                        .filter { it != initial }
                        .first()
                    emit(false)
                }
            }.distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Category and TOC aggregates are updated incrementally from fetch loops
    val categoryAggFlow: StateFlow<CategoryAgg> = _categoryAgg.asStateFlow()
    val tocCountsFlow: StateFlow<Map<Long, Int>> = _tocCounts.asStateFlow()

    private val _tocTree = MutableStateFlow<TocTree?>(null)
    val tocTreeFlow: StateFlow<TocTree?> = _tocTree.asStateFlow()
    private val _searchTree = MutableStateFlow<List<SearchTreeCategory>>(emptyList())
    val searchTreeFlow: StateFlow<List<SearchTreeCategory>> = _searchTree.asStateFlow()

    init {
        // Compute search tree when visible and results change; emits into _searchTree
        // Skip if facets have been computed (tree is built from facets directly)
        viewModelScope.launch {
            _uiVisible
                .flatMapLatest { visible ->
                    if (!visible) {
                        // Don't clear tree when tab becomes invisible - just stop emitting
                        kotlinx.coroutines.flow.emptyFlow()
                    } else {
                        uiState
                            .map { it.results }
                            .debounce(100)
                            .mapLatest {
                                // Skip rebuild if facets are computed (tree already built)
                                if (facetsComputed) {
                                    _searchTree.value
                                } else {
                                    buildSearchResultTree()
                                }
                            }.flowOn(Dispatchers.Default)
                    }
                }.collect { tree -> _searchTree.value = tree }
        }
    }

    // Helper to combine 4 values strongly typed
    private data class Quad<A, B, C, D>(
        val a: A,
        val b: B,
        val c: C,
        val d: D,
    )

    private data class Sext<A, B, C, D, E, F>(
        val a: A,
        val b: B,
        val c: C,
        val d: D,
        val e: E,
        val f: F,
    )

    private var currentTocBookId: Long? = null

    // --- Fast filtering index helpers ---
    private data class ResultsIndex(
        val identity: Int,
        val bookToIndices: Map<Long, IntArray>,
        val lineIdToIndex: Map<Long, Int>,
    )

    @Volatile private var resultsIndex: ResultsIndex? = null
    private var tocIndicesIdentity: Int = -1
    private val tocIndicesCache: MutableMap<Long, IntArray> = mutableMapOf()

    private suspend fun ensureResultsIndex(results: List<SearchResult>) {
        val identity = System.identityHashCode(results)
        val current = resultsIndex
        if (current != null && current.identity == identity) return
        indexMutex.withLock {
            val cur2 = resultsIndex
            if (cur2 != null && cur2.identity == identity) return@withLock
            val bookMap = HashMap<Long, MutableList<Int>>()
            val lineMap = HashMap<Long, Int>(results.size * 2)
            var i = 0
            for (r in results) {
                bookMap.getOrPut(r.bookId) { ArrayList() }.add(i)
                lineMap[r.lineId] = i
                i++
            }
            val finalBook = HashMap<Long, IntArray>(bookMap.size)
            for ((k, v) in bookMap) {
                val arr = IntArray(v.size)
                var idx = 0
                for (n in v) {
                    arr[idx++] = n
                }
                finalBook[k] = arr
            }
            resultsIndex = ResultsIndex(identity = identity, bookToIndices = finalBook, lineIdToIndex = lineMap)
            tocIndicesIdentity = identity
            tocIndicesCache.clear()
        }
    }

    private suspend fun fastFilterVisibleResults(
        results: List<SearchResult>,
        bookId: Long?,
        allowedBooks: Set<Long>,
        tocActive: Boolean,
        selectedBooks: Set<Long>,
        multiBooks: Set<Long>,
        selectedTocIds: Set<Long>,
        scopeTocId: Long?,
    ): List<SearchResult> {
        if (results.isEmpty()) return emptyList()
        if (!tocActive &&
            bookId == null &&
            allowedBooks.isEmpty() &&
            selectedBooks.isEmpty() &&
            multiBooks.isEmpty() &&
            selectedTocIds.isEmpty()
        ) {
            return results
        }
        ensureResultsIndex(results)
        val index = resultsIndex ?: return results

        suspend fun fromBookIds(bookIds: Set<Long>): List<SearchResult> {
            if (bookIds.isEmpty()) return emptyList()
            if (bookIds.size == 1) {
                val bid = bookIds.first()
                val arr = index.bookToIndices[bid] ?: return emptyList()
                return ArrayList<SearchResult>(arr.size).apply {
                    var i = 0
                    while (i < arr.size) {
                        add(results[arr[i]])
                        i++
                    }
                }
            }
            val arrays = ArrayList<IntArray>(bookIds.size)
            for (bid in bookIds) index.bookToIndices[bid]?.let { arrays.add(it) }
            if (arrays.isEmpty()) return emptyList()
            val merged = mergeSortedIndicesParallel(arrays)
            return ArrayList<SearchResult>(merged.size).apply {
                var i = 0
                while (i < merged.size) {
                    add(results[merged[i]])
                    i++
                }
            }
        }

        // Union semantics across active filters (categories/books/TOC/selected lines)
        val toMerge = ArrayList<IntArray>(6)
        if (selectedTocIds.isNotEmpty()) {
            val tocArrays = mutableListOf<IntArray>()
            for (tocId in selectedTocIds) {
                val bid = tocBookCache.getOrPut(tocId) { runCatching { repository.getTocEntry(tocId)?.bookId }.getOrNull() ?: -1L }
                if (bid > 0) {
                    val arr = indicesForTocSubtree(tocId, bid, index)
                    if (arr.isNotEmpty()) tocArrays.add(arr)
                }
            }
            if (tocArrays.isNotEmpty()) {
                val merged = mergeSortedIndicesParallel(tocArrays)
                if (merged.isNotEmpty()) toMerge.add(merged)
            }
        }
        if (selectedBooks.isNotEmpty()) {
            val arr = mergeSortedIndicesParallel(selectedBooks.mapNotNull { index.bookToIndices[it] })
            if (arr.isNotEmpty()) toMerge.add(arr)
        }
        if (multiBooks.isNotEmpty()) {
            val arr = mergeSortedIndicesParallel(multiBooks.mapNotNull { index.bookToIndices[it] })
            if (arr.isNotEmpty()) toMerge.add(arr)
        }
        if (tocActive && scopeTocId != null) {
            val bid =
                bookId
                    ?: tocBookCache.getOrPut(scopeTocId) { runCatching { repository.getTocEntry(scopeTocId)?.bookId }.getOrNull() ?: -1L }
            if (bid > 0) {
                val arr = indicesForTocSubtree(scopeTocId, bid, index)
                if (arr.isNotEmpty()) toMerge.add(arr)
            }
        }
        if (bookId != null) {
            index.bookToIndices[bookId]?.let { if (it.isNotEmpty()) toMerge.add(it) }
        }
        if (toMerge.isNotEmpty()) {
            val merged = mergeSortedIndicesParallel(toMerge)
            return ArrayList<SearchResult>(merged.size).apply {
                var i = 0
                while (i < merged.size) {
                    add(results[merged[i]])
                    i++
                }
            }
        }
        // fallback to scope allowedBooks only
        if (allowedBooks.isNotEmpty()) {
            val distinctBooks = index.bookToIndices.size
            return if (allowedBooks.size >= distinctBooks * 3 / 4) {
                results.parallelFilterByBook(allowedBooks)
            } else {
                val arr = mergeSortedIndicesParallel(allowedBooks.mapNotNull { index.bookToIndices[it] })
                ArrayList<SearchResult>(arr.size).apply {
                    var i = 0
                    while (i < arr.size) {
                        add(results[arr[i]])
                        i++
                    }
                }
            }
        }
        return results
    }

    private suspend fun indicesForTocSubtree(
        tocId: Long,
        bookId: Long,
        index: ResultsIndex,
    ): IntArray {
        if (tocIndicesIdentity != index.identity) {
            tocIndicesIdentity = index.identity
            tocIndicesCache.clear()
        }
        tocIndicesCache[tocId]?.let { return it }
        val tocIndex = ensureTocLineIndex(bookId)
        val lineIds = tocIndex.subtreeLineIds(tocId)
        if (lineIds.isEmpty()) return IntArray(0)
        var count = 0
        val tmp = IntArray(lineIds.size)
        for (lid in lineIds) {
            val idx = index.lineIdToIndex[lid]
            if (idx != null) tmp[count++] = idx
        }
        if (count == 0) return IntArray(0)
        val arr = if (count == tmp.size) tmp else tmp.copyOf(count)
        Arrays.parallelSort(arr)
        tocIndicesCache[tocId] = arr
        return arr
    }

    private suspend fun mergeSortedIndicesParallel(arrays: List<IntArray>): IntArray {
        if (arrays.isEmpty()) return IntArray(0)
        if (arrays.size == 1) return arrays[0]
        return coroutineScope {
            fun mergeTwo(
                a: IntArray,
                b: IntArray,
            ): IntArray {
                val out = IntArray(a.size + b.size)
                var i = 0
                var j = 0
                var k = 0
                var hasLast = false
                var last = 0
                while (i < a.size && j < b.size) {
                    val av = a[i]
                    val bv = b[j]
                    val v: Int
                    if (av <= bv) {
                        v = av
                        i++
                    } else {
                        v = bv
                        j++
                    }
                    if (!hasLast || v != last) {
                        out[k++] = v
                        last = v
                        hasLast = true
                    }
                }
                while (i < a.size) {
                    val v = a[i++]
                    if (!hasLast || v != last) {
                        out[k++] = v
                        last = v
                        hasLast = true
                    }
                }
                while (j < b.size) {
                    val v = b[j++]
                    if (!hasLast || v != last) {
                        out[k++] = v
                        last = v
                        hasLast = true
                    }
                }
                return if (k == out.size) out else out.copyOf(k)
            }

            suspend fun mergeRange(
                start: Int,
                end: Int,
            ): IntArray {
                val len = end - start
                return when {
                    len <= 0 -> IntArray(0)
                    len == 1 -> arrays[start]
                    len == 2 -> mergeTwo(arrays[start], arrays[start + 1])
                    else -> {
                        val mid = start + len / 2
                        val left = async(Dispatchers.Default) { mergeRange(start, mid) }
                        val right = async(Dispatchers.Default) { mergeRange(mid, end) }
                        mergeTwo(left.await(), right.await())
                    }
                }
            }
            mergeRange(0, arrays.size)
        }
    }

    private fun List<SearchResult>.parallelFilterByBook(allowed: Set<Long>): List<SearchResult> =
        run {
            val total = this.size
            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            if (total < PARALLEL_FILTER_THRESHOLD || cores == 1) return@run this.fastFilterByBookSequential(allowed)
            val chunk = (total + cores - 1) / cores
            runBlocking(Dispatchers.Default) {
                val tasks = ArrayList<Deferred<List<SearchResult>>>(cores)
                var start = 0
                while (start < total) {
                    val s = start
                    val e = kotlin.math.min(total, start + chunk)
                    tasks +=
                        async {
                            val sub = ArrayList<SearchResult>(e - s)
                            var i = s
                            while (i < e) {
                                val v = this@parallelFilterByBook[i]
                                if (v.bookId in allowed) sub.add(v)
                                i++
                            }
                            sub
                        }
                    start = e
                }
                tasks.awaitAll().flatten()
            }
        }

    private fun List<SearchResult>.fastFilterByBookSequential(allowed: Set<Long>): List<SearchResult> {
        val out = ArrayList<SearchResult>(this.size)
        for (r in this) if (r.bookId in allowed) out.add(r)
        return out
    }

    // Bulk caches for TOC counting within a scoped book
    private var cachedCountsBookId: Long? = null
    private var lineIdToTocId: Map<Long, Long> = emptyMap()
    private var tocParentById: Map<Long, Long?> = emptyMap()
    private val tocLineIndexCache: MutableMap<Long, TocLineIndex> = mutableMapOf()
    private val tocBookCache: MutableMap<Long, Long> = mutableMapOf()

    init {
        val persisted = persistedSearchState()
        val navQuery = savedStateHandle.get<String>("searchQuery") ?: ""
        val initialQuery = persisted.query.takeIf { it.isNotBlank() } ?: navQuery

        if (initialQuery.isNotBlank() && persisted.query != initialQuery) {
            updatePersistedSearch { it.copy(query = initialQuery) }
        }

        _selectedCategoryIds.value = persisted.selectedCategoryIds
        _selectedBookIds.value = persisted.selectedBookIds
        _selectedTocIds.value = persisted.selectedTocIds
        _breadcrumbs.value = persisted.breadcrumbs

        _uiState.value =
            _uiState.value.copy(
                query = initialQuery,
                globalExtended = persisted.globalExtended,
                scrollIndex = persisted.scrollIndex,
                scrollOffset = persisted.scrollOffset,
                anchorId = persisted.anchorId,
                anchorIndex = persisted.anchorIndex,
                textSize = AppSettings.getTextSize(),
                scopeTocId = persisted.filterTocId.takeIf { it > 0 },
            )

        val filterCategoryId = persisted.filterCategoryId.takeIf { it > 0 }
        val filterBookId = persisted.filterBookId.takeIf { it > 0 }
        val filterTocId = persisted.filterTocId.takeIf { it > 0 }

        // Restore scope book from either book filter or TOC filter.
        when {
            filterBookId != null -> {
                viewModelScope.launch {
                    val book = repository.getBookCore(filterBookId)
                    _uiState.value = _uiState.value.copy(scopeBook = book)
                }
            }
            filterTocId != null -> {
                viewModelScope.launch {
                    val toc = repository.getTocEntry(filterTocId)
                    val book = toc?.let { repository.getBookCore(it.bookId) }
                    _uiState.value = _uiState.value.copy(scopeBook = book)
                }
            }
        }

        // Restore category scope path if a category filter is persisted.
        if (filterCategoryId != null) {
            viewModelScope.launch {
                val path = runCatching { buildCategoryPath(filterCategoryId) }.getOrDefault(emptyList())
                _uiState.value = _uiState.value.copy(scopeCategoryPath = path)
            }
        }

        // Update tab title to the query (TabsViewModel also handles initial title)
        if (initialQuery.isNotBlank()) {
            titleUpdateManager.updateTabTitle(tabId, initialQuery, TabType.SEARCH)
        }

        // Try to restore a full snapshot for this tab without redoing the search.
        val cached = persisted.snapshot
        if (cached != null) {
            // Adopt cached results and aggregates; keep filters and scroll from persisted state.
            _uiState.value =
                _uiState.value.copy(
                    results = cached.results,
                    isLoading = false,
                    hasMore = false,
                    progressCurrent = cached.results.size,
                    progressTotal = cached.results.size.toLong(),
                    // trigger scroll restoration once items are present
                    scrollToAnchorTimestamp = System.currentTimeMillis(),
                )
            // Immediately restore aggregates and toc counts so the tree and TOC show counts without delay
            _categoryAgg.value =
                CategoryAgg(
                    categoryCounts = cached.categoryAgg.categoryCounts,
                    bookCounts = cached.categoryAgg.bookCounts,
                    booksForCategory = cached.categoryAgg.booksForCategory,
                )
            _tocCounts.value = cached.tocCounts
            // Restore TOC tree if present
            cached.tocTree?.let { snap ->
                _tocTree.value = TocTree(snap.rootEntries, snap.children)
            }
            // Restore precomputed search tree if present to avoid recomputation on cold restore
            cached.searchTree?.let { snapList ->
                fun mapNode(n: SearchTabCache.SearchTreeCategorySnapshot): SearchTreeCategory =
                    SearchTreeCategory(
                        category = n.category,
                        count = n.count,
                        children = n.children.map { mapNode(it) },
                        books = n.books.map { SearchTreeBook(it.book, it.count) },
                    )
                _searchTree.value = snapList.map { mapNode(it) }
            }
            // Reconstruct currentKey from fetch scope.
            val fetchCategoryId = persisted.fetchCategoryId.takeIf { it > 0 } ?: persisted.filterCategoryId.takeIf { it > 0 }
            val fetchBookId = persisted.fetchBookId.takeIf { it > 0 } ?: persisted.filterBookId.takeIf { it > 0 }
            val fetchTocId = persisted.fetchTocId.takeIf { it > 0 } ?: persisted.filterTocId.takeIf { it > 0 }
            currentKey =
                SearchParamsKey(
                    query = _uiState.value.query,
                    filterCategoryId = fetchCategoryId,
                    filterBookId = fetchBookId,
                    filterTocId = fetchTocId,
                )
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

        viewModelScope.launch {
            // Observe tabs list and cancel search if this tab gets closed
            tabsViewModel.tabs.collect { tabs ->
                val exists = tabs.any { it.destination.tabId == tabId }
                if (!exists) {
                    // Tab was closed; stop work.
                    cancelSearch()
                }
            }
        }
    }

    // Caching continuation removed: searches are executed fresh.

    /**
     * Update the search query in UI state and persist it for this tab.
     * Does not trigger a search by itself; callers should invoke [executeSearch].
     */
    fun setQuery(query: String) {
        val q = query.trim()
        _uiState.value = _uiState.value.copy(query = q)
        updatePersistedSearch { it.copy(query = q) }
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
        _breadcrumbs.value = emptyMap()
        // Reset persisted scroll/anchor so restoration targets the top for fresh results.
        updatePersistedSearch {
            it.copy(
                query = q,
                scrollIndex = 0,
                scrollOffset = 0,
                anchorId = -1L,
                anchorIndex = 0,
                snapshot = null,
                breadcrumbs = emptyMap(),
            )
        }
        _uiState.value =
            _uiState.value.copy(
                scrollIndex = 0,
                scrollOffset = 0,
                anchorId = -1L,
                anchorIndex = 0,
                scrollToAnchorTimestamp = System.currentTimeMillis(),
            )
        currentJob =
            viewModelScope.launch(Dispatchers.Default) {
                _uiState.value =
                    _uiState.value.copy(isLoading = true, results = emptyList(), hasMore = false, progressCurrent = 0, progressTotal = null)
                // Reset facetsComputed flag for new search
                facetsComputed = false
                // Reset aggregates and counts for a clean run
                countsMutex.withLock {
                    categoryCountsAcc.clear()
                    bookCountsAcc.clear()
                    booksForCategoryAcc.clear()
                    tocCountsAcc.clear()
                    _categoryAgg.value = CategoryAgg(emptyMap(), emptyMap(), emptyMap())
                    _tocCounts.value = emptyMap()
                }
                try {
                    val persisted = persistedSearchState()
                    val fetchCategoryId = persisted.fetchCategoryId.takeIf { it > 0 } ?: persisted.filterCategoryId.takeIf { it > 0 }
                    val fetchBookId = persisted.fetchBookId.takeIf { it > 0 } ?: persisted.filterBookId.takeIf { it > 0 }
                    val fetchTocId = persisted.fetchTocId.takeIf { it > 0 } ?: persisted.filterTocId.takeIf { it > 0 }
                    // Apply persisted/initial global-extended flag to UI state so toolbar reflects it
                    val extended = persisted.globalExtended
                    if (_uiState.value.globalExtended != extended) {
                        _uiState.value = _uiState.value.copy(globalExtended = extended)
                    }

                    currentKey =
                        SearchParamsKey(
                            query = q,
                            filterCategoryId = fetchCategoryId,
                            filterBookId = fetchBookId,
                            filterTocId = fetchTocId,
                        )

                    val acc = mutableListOf<SearchResult>()

                    val initialScopePath =
                        when {
                            persisted.filterCategoryId > 0 -> buildCategoryPath(persisted.filterCategoryId)
                            else -> emptyList()
                        }
                    val persistedScopeBook =
                        when {
                            persisted.filterBookId > 0 -> repository.getBookCore(persisted.filterBookId)
                            else -> null
                        }
                    val resolvedScopeBook =
                        persistedScopeBook ?: fetchBookId?.let { runCatching { repository.getBookCore(it) }.getOrNull() }
                    _uiState.value =
                        _uiState.value.copy(
                            scopeCategoryPath = initialScopePath,
                            scopeBook = resolvedScopeBook,
                        )
                    // Prepare TOC tree for the scoped book so the panel is ready without recomputation
                    resolvedScopeBook?.let { book ->
                        if (currentTocBookId != book.id) {
                            val tree = buildTocTreeForBook(book.id)
                            _tocTree.value = tree
                            currentTocBookId = book.id
                        }
                        // Ensure bulk caches for counts are ready for this book
                        ensureTocCountingCaches(book.id)
                    }

                    // Phase 1: Compute facets instantly for immediate tree display
                    val baseBookOnly = !_uiState.value.globalExtended
                    val facetsBookIds: Collection<Long>? =
                        when {
                            fetchTocId != null -> {
                                val toc = repository.getTocEntry(fetchTocId)
                                toc?.bookId?.let { listOf(it) }
                            }
                            fetchBookId != null -> listOf(fetchBookId)
                            fetchCategoryId != null -> collectBookIdsUnderCategory(fetchCategoryId)
                            else -> null // Use baseBookOnly parameter instead
                        }

                    val facets =
                        lucene.computeFacets(
                            query = q,
                            near = DEFAULT_NEAR,
                            bookIds = facetsBookIds,
                            baseBookOnly = baseBookOnly,
                        )

                    if (facets != null) {
                        // Set aggregates immediately
                        _categoryAgg.value =
                            CategoryAgg(
                                categoryCounts = facets.categoryCounts,
                                bookCounts = facets.bookCounts,
                                booksForCategory = emptyMap(), // Not needed for tree building
                            )
                        _uiState.value = _uiState.value.copy(progressTotal = facets.totalHits)

                        // Build tree from facets immediately
                        val tree =
                            buildSearchTreeUseCase.invoke(
                                facetCategoryCounts = facets.categoryCounts,
                                facetBookCounts = facets.bookCounts,
                            )
                        _searchTree.value = tree
                        facetsComputed = true
                    }

                    // Close any existing session before opening a new one
                    lazyLoadMutex.withLock {
                        currentSession?.close()
                        currentSession = null
                    }

                    val sessionInfo = prepareSearchSession(q, fetchCategoryId, fetchBookId, fetchTocId)
                    if (sessionInfo == null) {
                        _uiState.value = _uiState.value.copy(results = emptyList(), progressCurrent = 0, progressTotal = 0)
                        return@launch
                    }
                    val (session, tocAllowedLineIds) = sessionInfo

                    // Store session for lazy loading
                    lazyLoadMutex.withLock {
                        currentSession = session
                        currentTocAllowedLineIds = tocAllowedLineIds
                        currentSearchQuery = q
                    }

                    // Load only the first page
                    val firstPage = session.nextPage(LAZY_PAGE_SIZE)
                    if (firstPage == null) {
                        _uiState.value =
                            _uiState.value.copy(
                                results = emptyList(),
                                hasMore = false,
                                progressCurrent = 0,
                                progressTotal = 0,
                            )
                        return@launch
                    }

                    val filteredHits =
                        if (tocAllowedLineIds.isEmpty()) {
                            firstPage.hits
                        } else {
                            firstPage.hits.filter { it.lineId in tocAllowedLineIds }
                        }

                    // Update TOC counts for first page
                    if (filteredHits.isNotEmpty()) {
                        _uiState.value.scopeBook
                            ?.id
                            ?.let { updateTocCountsForHits(filteredHits, it) }
                    }

                    val results = hitsToResults(filteredHits, q)
                    _uiState.value =
                        _uiState.value.copy(
                            results = results,
                            hasMore = !firstPage.isLastPage,
                            progressCurrent = results.size,
                            progressTotal = firstPage.totalHits,
                        )
                } finally {
                    // Clear loading promptly; if a new visibleResults emission is pending, wait briefly
                    // but never block indefinitely (important when final results are empty and identical
                    // to the pre-stream empty list reference).
                    runCatching {
                        val initial = Pair(visibleResultsFlow.value.size, System.identityHashCode(visibleResultsFlow.value))
                        withTimeoutOrNull(300) {
                            visibleResultsFlow
                                .map { Pair(it.size, System.identityHashCode(it)) }
                                .distinctUntilChanged()
                                .filter { it != initial }
                                .first()
                        }
                    }
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
    }

    /**
     * Load the next page of results (lazy loading).
     * Called when user scrolls near the bottom of the list.
     */
    fun loadMore() {
        if (_uiState.value.isLoadingMore || !_uiState.value.hasMore) return

        viewModelScope.launch(Dispatchers.Default) {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            try {
                val session = lazyLoadMutex.withLock { currentSession } ?: return@launch
                val tocAllowedLineIds = currentTocAllowedLineIds
                val query = currentSearchQuery

                val page = session.nextPage(LAZY_PAGE_SIZE)
                if (page == null) {
                    _uiState.value = _uiState.value.copy(hasMore = false, isLoadingMore = false)
                    return@launch
                }

                val filteredHits =
                    if (tocAllowedLineIds.isEmpty()) {
                        page.hits
                    } else {
                        page.hits.filter { it.lineId in tocAllowedLineIds }
                    }

                // Update TOC counts for this page
                if (filteredHits.isNotEmpty()) {
                    _uiState.value.scopeBook
                        ?.id
                        ?.let { updateTocCountsForHits(filteredHits, it) }
                }

                val newResults = hitsToResults(filteredHits, query)
                val currentResults = _uiState.value.results
                _uiState.value =
                    _uiState.value.copy(
                        results = currentResults + newResults,
                        hasMore = !page.isLastPage,
                        progressCurrent = currentResults.size + newResults.size,
                        isLoadingMore = false,
                    )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingMore = false)
            }
        }
    }

    private suspend fun prepareSearchSession(
        query: String,
        fetchCategoryId: Long?,
        fetchBookId: Long?,
        fetchTocId: Long?,
    ): Pair<SearchSession, Set<Long>>? {
        var tocAllowedLineIds: Set<Long> = emptySet()
        val baseBookOnly = !_uiState.value.globalExtended
        val session: SearchSession? =
            when {
                fetchTocId != null -> {
                    val toc = repository.getTocEntry(fetchTocId) ?: return null
                    ensureTocCountingCaches(toc.bookId)
                    val lineIds = collectLineIdsForTocSubtree(toc.id, toc.bookId)
                    tocAllowedLineIds = lineIds
                    lucene.openSession(query, DEFAULT_NEAR, lineIds = lineIds, baseBookOnly = baseBookOnly)
                }
                fetchBookId != null -> lucene.openSession(query, DEFAULT_NEAR, bookIds = listOf(fetchBookId), baseBookOnly = baseBookOnly)
                fetchCategoryId != null -> {
                    val books = collectBookIdsUnderCategory(fetchCategoryId)
                    lucene.openSession(query, DEFAULT_NEAR, bookIds = books, baseBookOnly = baseBookOnly)
                }
                else -> {
                    // Use baseBookOnly parameter directly instead of fetching all base book IDs
                    lucene.openSession(query, DEFAULT_NEAR, baseBookOnly = baseBookOnly)
                }
            }
        val safeSession = session ?: return null
        return safeSession to tocAllowedLineIds
    }

    private fun hitsToResults(
        hits: List<LineHit>,
        rawQuery: String,
    ): List<SearchResult> {
        if (hits.isEmpty()) return emptyList()
        val trimmedQuery = rawQuery.trim()
        val checkExact = trimmedQuery.isNotEmpty()
        val out = ArrayList<SearchResult>(hits.size)
        for (hit in hits) {
            val snippetFromIndex =
                when {
                    hit.snippet.isNotBlank() -> hit.snippet
                    hit.rawText.isNotBlank() -> hit.rawText
                    else -> ""
                }
            val snippet = snippetFromIndex
            val scoreBoost = if (checkExact && hit.rawText.contains(trimmedQuery)) 1e-3 else 0.0
            out +=
                SearchResult(
                    bookId = hit.bookId,
                    bookTitle = hit.bookTitle,
                    lineId = hit.lineId,
                    lineIndex = hit.lineIndex,
                    snippet = snippet,
                    rank = hit.score.toDouble() + scoreBoost,
                )
        }
        return out
    }

    fun cancelSearch() {
        currentJob?.cancel()
        // Close session to release resources
        runCatching {
            currentSession?.close()
            currentSession = null
        }
        _uiState.value = _uiState.value.copy(isLoading = false, isLoadingMore = false, hasMore = false)
    }

    override fun onCleared() {
        super.onCleared()
        // If the tab still exists, persist a lightweight snapshot so it can be restored
        // without re-searching.
        val stillExists =
            runCatching {
                tabsViewModel.tabs.value.any { it.destination.tabId == tabId }
            }.getOrDefault(false)
        if (stillExists) {
            val snap = buildSnapshot(uiState.value.results)
            updatePersistedSearch { it.copy(snapshot = snap, breadcrumbs = _breadcrumbs.value) }
        }
        cancelSearch()
    }

    private fun buildSnapshot(results: List<SearchResult>): SearchTabCache.Snapshot {
        val catAgg = _categoryAgg.value
        val treeSnap =
            _tocTree.value?.let { t ->
                SearchTabCache.TocTreeSnapshot(t.rootEntries, t.children)
            }
        val searchTreeSnap: List<SearchTabCache.SearchTreeCategorySnapshot>? =
            runCatching {
                val cur = searchTreeFlow.value
                if (cur.isEmpty()) {
                    null
                } else {
                    fun mapNode(n: SearchTreeCategory): SearchTabCache.SearchTreeCategorySnapshot =
                        SearchTabCache.SearchTreeCategorySnapshot(
                            category = n.category,
                            count = n.count,
                            children = n.children.map { mapNode(it) },
                            books = n.books.map { b -> SearchTabCache.SearchTreeBookSnapshot(b.book, b.count) },
                        )
                    cur.map { mapNode(it) }
                }
            }.getOrNull()
        return SearchTabCache.Snapshot(
            results = results,
            categoryAgg =
                SearchTabCache.CategoryAggSnapshot(
                    categoryCounts = catAgg.categoryCounts,
                    bookCounts = catAgg.bookCounts,
                    booksForCategory = catAgg.booksForCategory,
                ),
            tocCounts = _tocCounts.value,
            tocTree = treeSnap,
            searchTree = searchTreeSnap,
        )
    }

    fun onScroll(
        anchorId: Long,
        anchorIndex: Int,
        index: Int,
        offset: Int,
    ) {
        updatePersistedSearch {
            it.copy(
                scrollIndex = index,
                scrollOffset = offset,
                anchorId = anchorId,
                anchorIndex = anchorIndex,
            )
        }

        _uiState.value =
            _uiState.value.copy(
                scrollIndex = index,
                scrollOffset = offset,
                anchorId = anchorId,
                anchorIndex = anchorIndex,
            )
    }

    /**
     * Compute a category/book tree with per-node counts based on the current results list.
     * Categories accumulate counts from their descendant books. Only categories and books that
     * appear in the current results are included.
     */
    suspend fun buildSearchResultTree(): List<SearchTreeCategory> = buildSearchTreeUseCase(uiState.value.results)

    /** Apply a category filter. Triggers a Lucene search with the filter for instant results. */
    fun filterByCategoryId(categoryId: Long) {
        viewModelScope.launch {
            // Clear checkbox selections when using direct filter
            _selectedCategoryIds.value = emptySet()
            _selectedBookIds.value = emptySet()
            _selectedTocIds.value = emptySet()

            updatePersistedSearch {
                it.copy(
                    datasetScope = "category",
                    filterCategoryId = categoryId,
                    filterBookId = 0L,
                    filterTocId = 0L,
                    fetchCategoryId = categoryId,
                    fetchBookId = 0L,
                    fetchTocId = 0L,
                    selectedCategoryIds = emptySet(),
                    selectedBookIds = emptySet(),
                    selectedTocIds = emptySet(),
                )
            }
            val scopePath = buildCategoryPath(categoryId)
            _uiState.value =
                _uiState.value.copy(
                    scopeCategoryPath = scopePath,
                    scopeBook = null,
                    scopeTocId = null,
                    scrollIndex = 0,
                    scrollOffset = 0,
                    scrollToAnchorTimestamp = System.currentTimeMillis(),
                )
            // Trigger Lucene search with category filter
            executeDirectFilterSearch(categoryId = categoryId)
        }
    }

    /** Apply a book filter. Triggers a Lucene search with the filter for instant results. */
    fun filterByBookId(bookId: Long) {
        viewModelScope.launch {
            // Clear checkbox selections when using direct filter
            _selectedCategoryIds.value = emptySet()
            _selectedBookIds.value = emptySet()
            _selectedTocIds.value = emptySet()

            updatePersistedSearch {
                it.copy(
                    datasetScope = "book",
                    filterCategoryId = 0L,
                    filterBookId = bookId,
                    filterTocId = 0L,
                    fetchCategoryId = 0L,
                    fetchBookId = bookId,
                    fetchTocId = 0L,
                    selectedCategoryIds = emptySet(),
                    selectedBookIds = emptySet(),
                    selectedTocIds = emptySet(),
                )
            }
            val book = runCatching { repository.getBookCore(bookId) }.getOrNull()
            _uiState.value =
                _uiState.value.copy(
                    scopeBook = book,
                    scopeCategoryPath = emptyList(),
                    scopeTocId = null,
                    scrollIndex = 0,
                    scrollOffset = 0,
                    scrollToAnchorTimestamp = System.currentTimeMillis(),
                )
            if (book != null && currentTocBookId != book.id) {
                val tree = runCatching { buildTocTreeForBook(book.id) }.getOrNull()
                if (tree != null) {
                    _tocTree.value = tree
                    currentTocBookId = book.id
                }
            }
            if (book != null) ensureTocCountingCaches(book.id)
            // Trigger Lucene search with book filter
            executeDirectFilterSearch(bookId = bookId)
        }
    }

    /** Apply a TOC filter. Triggers a Lucene search with the filter for instant results. */
    fun filterByTocId(tocId: Long) {
        viewModelScope.launch {
            // Clear checkbox selections when using direct filter
            _selectedCategoryIds.value = emptySet()
            _selectedBookIds.value = emptySet()
            _selectedTocIds.value = emptySet()

            val toc = runCatching { repository.getTocEntry(tocId) }.getOrNull()
            val bookIdFromToc = toc?.bookId
            updatePersistedSearch {
                it.copy(
                    datasetScope = "toc",
                    filterCategoryId = 0L,
                    filterBookId = bookIdFromToc ?: 0L,
                    filterTocId = tocId,
                    fetchCategoryId = 0L,
                    fetchBookId = bookIdFromToc ?: 0L,
                    fetchTocId = tocId,
                    selectedCategoryIds = emptySet(),
                    selectedBookIds = emptySet(),
                    selectedTocIds = emptySet(),
                )
            }

            val scopeBook = if (bookIdFromToc != null) runCatching { repository.getBookCore(bookIdFromToc) }.getOrNull() else null
            _uiState.value =
                _uiState.value.copy(
                    scopeBook = scopeBook,
                    scopeTocId = tocId,
                    scopeCategoryPath = emptyList(),
                    scrollIndex = 0,
                    scrollOffset = 0,
                    scrollToAnchorTimestamp = System.currentTimeMillis(),
                )
            if (scopeBook != null && currentTocBookId != scopeBook.id) {
                val tree = runCatching { buildTocTreeForBook(scopeBook.id) }.getOrNull()
                if (tree != null) {
                    _tocTree.value = tree
                    currentTocBookId = scopeBook.id
                }
            }
            scopeBook?.let { ensureTocCountingCaches(it.id) }
            // Trigger Lucene search with TOC filter
            executeDirectFilterSearch(tocId = tocId, bookId = bookIdFromToc)
        }
    }

    /**
     * Execute a Lucene search with a direct filter (category, book, or TOC).
     * Used by filterByXxx functions for instant filtering.
     * NOTE: Does NOT rebuild the tree - keeps the original tree structure for navigation.
     */
    private fun executeDirectFilterSearch(
        categoryId: Long? = null,
        bookId: Long? = null,
        tocId: Long? = null,
    ) {
        val q = _uiState.value.query.trim()
        if (q.isBlank()) return

        currentJob?.cancel()
        currentJob =
            viewModelScope.launch(Dispatchers.Default) {
                _uiState.value = _uiState.value.copy(isLoading = true)

                try {
                    val baseBookOnly = !_uiState.value.globalExtended

                    // Determine filter parameters
                    val bookIdsToFilter: Collection<Long>? =
                        when {
                            tocId != null -> null // Will use lineIds
                            bookId != null -> listOf(bookId)
                            categoryId != null -> collectBookIdsUnderCategory(categoryId)
                            else -> null // Use baseBookOnly parameter instead
                        }

                    val lineIdsToFilter: Collection<Long>? =
                        if (tocId != null && bookId != null) {
                            collectLineIdsForTocSubtree(tocId, bookId)
                        } else {
                            null
                        }

                    // Close existing session
                    lazyLoadMutex.withLock {
                        currentSession?.close()
                        currentSession = null
                    }

                    // Open search session with filter (use baseBookOnly for base-book-only search)
                    val session =
                        when {
                            lineIdsToFilter != null ->
                                lucene.openSession(
                                    q,
                                    DEFAULT_NEAR,
                                    lineIds = lineIdsToFilter,
                                    baseBookOnly = baseBookOnly,
                                )
                            bookIdsToFilter != null ->
                                lucene.openSession(
                                    q,
                                    DEFAULT_NEAR,
                                    bookIds = bookIdsToFilter,
                                    baseBookOnly = baseBookOnly,
                                )
                            else -> lucene.openSession(q, DEFAULT_NEAR, baseBookOnly = baseBookOnly)
                        }

                    if (session == null) {
                        _uiState.value =
                            _uiState.value.copy(
                                results = emptyList(),
                                hasMore = false,
                                progressCurrent = 0,
                                progressTotal = 0,
                            )
                        return@launch
                    }

                    lazyLoadMutex.withLock {
                        currentSession = session
                        currentTocAllowedLineIds = emptySet()
                        currentSearchQuery = q
                    }

                    // Load first page
                    val firstPage = session.nextPage(LAZY_PAGE_SIZE)
                    if (firstPage == null) {
                        _uiState.value =
                            _uiState.value.copy(
                                results = emptyList(),
                                hasMore = false,
                                progressCurrent = 0,
                            )
                        return@launch
                    }

                    // Update progress but DON'T rebuild tree - keep original tree for navigation
                    _uiState.value = _uiState.value.copy(progressTotal = firstPage.totalHits)

                    val results = hitsToResults(firstPage.hits, q)
                    _uiState.value =
                        _uiState.value.copy(
                            results = results,
                            hasMore = !firstPage.isLastPage,
                            progressCurrent = results.size,
                        )
                } finally {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
    }

    // Multi-select toggles for checkboxes
    fun setCategoryChecked(
        categoryId: Long,
        checked: Boolean,
    ) {
        viewModelScope.launch {
            val ids = mutableSetOf<Long>()
            ids += categoryId
            // Prefer current search tree to derive descendants (only categories present in results)
            val tree = runCatching { searchTreeFlow.value }.getOrElse { emptyList() }
            if (tree.isNotEmpty()) {
                fun findNode(list: List<SearchTreeCategory>): SearchTreeCategory? {
                    for (n in list) {
                        if (n.category.id == categoryId) return n
                        val f = findNode(n.children)
                        if (f != null) return f
                    }
                    return null
                }
                val start = findNode(tree)
                if (start != null) {
                    fun dfs(n: SearchTreeCategory) {
                        for (c in n.children) {
                            ids += c.category.id
                            dfs(c)
                        }
                    }
                    dfs(start)
                }
            } else {
                // Fallback to repository traversal
                val stack = ArrayDeque<Long>()
                stack.addLast(categoryId)
                var guard = 0
                while (stack.isNotEmpty() && guard++ < 10000) {
                    val current = stack.removeFirst()
                    val children = runCatching { repository.getCategoryChildren(current) }.getOrElse { emptyList() }
                    for (ch in children) {
                        if (ids.add(ch.id)) stack.addLast(ch.id)
                    }
                }
            }
            val next = _selectedCategoryIds.value.let { set -> if (checked) set + ids else set - ids }
            _selectedCategoryIds.value = next
            updatePersistedSearch { it.copy(selectedCategoryIds = next) }
            maybeClearFiltersIfNoneChecked()
            // Trigger filtered Lucene search
            executeFilteredSearch()
        }
    }

    fun setBookChecked(
        bookId: Long,
        checked: Boolean,
    ) {
        val next = _selectedBookIds.value.let { set -> if (checked) set + bookId else set - bookId }
        _selectedBookIds.value = next
        updatePersistedSearch { it.copy(selectedBookIds = next) }
        if (!checked) {
            maybeClearFiltersIfNoneChecked()
        }
        // Trigger filtered Lucene search
        executeFilteredSearch()
    }

    fun setTocChecked(
        tocId: Long,
        checked: Boolean,
    ) {
        val next = _selectedTocIds.value.let { set -> if (checked) set + tocId else set - tocId }
        _selectedTocIds.value = next
        updatePersistedSearch { it.copy(selectedTocIds = next) }
        if (!checked) {
            maybeClearFiltersIfNoneChecked()
        }
        // Trigger filtered Lucene search
        executeFilteredSearch()
    }

    private fun maybeClearFiltersIfNoneChecked() {
        val noneChecked = _selectedBookIds.value.isEmpty() && _selectedCategoryIds.value.isEmpty() && _selectedTocIds.value.isEmpty()
        if (!noneChecked) return
        // Clear view filters
        _uiState.value =
            _uiState.value.copy(
                scopeBook = null,
                scopeCategoryPath = emptyList(),
                scopeTocId = null,
            )
        tocBookCache.clear()
        updatePersistedSearch {
            it.copy(
                datasetScope = "global",
                filterCategoryId = 0L,
                filterBookId = 0L,
                filterTocId = 0L,
                fetchCategoryId = 0L,
                fetchBookId = 0L,
                fetchTocId = 0L,
            )
        }
    }

    /**
     * Execute a filtered Lucene search based on current checkbox selections.
     * This re-queries Lucene with the filter applied, which is instant due to indexing.
     * NOTE: Does NOT rebuild the tree - keeps the original tree structure for navigation.
     */
    private fun executeFilteredSearch() {
        val q = _uiState.value.query.trim()
        if (q.isBlank()) return

        currentJob?.cancel()
        currentJob =
            viewModelScope.launch(Dispatchers.Default) {
                _uiState.value = _uiState.value.copy(isLoading = true)

                try {
                    val selectedCats = _selectedCategoryIds.value
                    val selectedBooks = _selectedBookIds.value
                    val selectedTocs = _selectedTocIds.value
                    val baseBookOnly = !_uiState.value.globalExtended

                    // Build the set of book IDs to filter
                    val bookIdsToFilter = mutableSetOf<Long>()

                    // Add books from selected categories
                    for (catId in selectedCats) {
                        bookIdsToFilter += collectBookIdsUnderCategory(catId)
                    }

                    // Add directly selected books
                    bookIdsToFilter += selectedBooks

                    // Determine line IDs from selected TOCs
                    val lineIdsToFilter = mutableSetOf<Long>()
                    for (tocId in selectedTocs) {
                        val bookId =
                            tocBookCache.getOrPut(tocId) {
                                runCatching { repository.getTocEntry(tocId)?.bookId }.getOrNull() ?: -1L
                            }
                        if (bookId > 0) {
                            lineIdsToFilter += collectLineIdsForTocSubtree(tocId, bookId)
                        }
                    }

                    // If nothing selected, use global search with baseBookOnly filter
                    val hasFilters = bookIdsToFilter.isNotEmpty() || lineIdsToFilter.isNotEmpty()

                    // Close existing session
                    lazyLoadMutex.withLock {
                        currentSession?.close()
                        currentSession = null
                    }

                    // Open search session with filter (use baseBookOnly for base-book-only search)
                    val session =
                        when {
                            lineIdsToFilter.isNotEmpty() -> {
                                lucene.openSession(q, DEFAULT_NEAR, lineIds = lineIdsToFilter, baseBookOnly = baseBookOnly)
                            }
                            bookIdsToFilter.isNotEmpty() -> {
                                lucene.openSession(q, DEFAULT_NEAR, bookIds = bookIdsToFilter, baseBookOnly = baseBookOnly)
                            }
                            else -> {
                                // No checkbox filter - use baseBookOnly for non-extended search
                                lucene.openSession(q, DEFAULT_NEAR, baseBookOnly = baseBookOnly)
                            }
                        }

                    if (session == null) {
                        _uiState.value =
                            _uiState.value.copy(
                                results = emptyList(),
                                hasMore = false,
                                progressCurrent = 0,
                                progressTotal = 0,
                            )
                        return@launch
                    }

                    lazyLoadMutex.withLock {
                        currentSession = session
                        currentTocAllowedLineIds = emptySet()
                        currentSearchQuery = q
                    }

                    // Load first page
                    val firstPage = session.nextPage(LAZY_PAGE_SIZE)
                    if (firstPage == null) {
                        _uiState.value =
                            _uiState.value.copy(
                                results = emptyList(),
                                hasMore = false,
                                progressCurrent = 0,
                            )
                        return@launch
                    }

                    // Update progress but DON'T rebuild tree - keep original tree for navigation
                    _uiState.value = _uiState.value.copy(progressTotal = firstPage.totalHits)

                    val results = hitsToResults(firstPage.hits, q)
                    _uiState.value =
                        _uiState.value.copy(
                            results = results,
                            hasMore = !firstPage.isLastPage,
                            progressCurrent = results.size,
                            scrollIndex = 0,
                            scrollOffset = 0,
                            scrollToAnchorTimestamp = System.currentTimeMillis(),
                        )
                } finally {
                    _uiState.value = _uiState.value.copy(isLoading = false)
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
            val bookId =
                scopeBook?.id
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
     * Ensure a scope book is set so the TOC panel can appear, without changing
     * the dataset or clearing other filters. Also prepares TOC tree and counts.
     */
    fun ensureScopeBookForToc(bookId: Long) {
        viewModelScope.launch {
            val book = runCatching { repository.getBookCore(bookId) }.getOrNull() ?: return@launch
            if (uiState.value.scopeBook?.id == book.id) return@launch
            _uiState.value = _uiState.value.copy(scopeBook = book)
            if (currentTocBookId != book.id) {
                val tree = runCatching { buildTocTreeForBook(book.id) }.getOrNull()
                if (tree != null) {
                    _tocTree.value = tree
                    currentTocBookId = book.id
                }
            }
            ensureTocCountingCaches(book.id)
            recomputeTocCountsForBook(book.id, uiState.value.results)
        }
    }

    /**
     * If aucune case Livre n'est cochée (et pas de filtre TOC explicite),
     * retire le scopeBook pour masquer le panneau TOC côté recherche.
     * Ne touche pas aux filtres persistés (si l'utilisateur a sélectionné un livre via clic).
     */
    fun clearScopeBookIfNoBookCheckboxSelected() {
        viewModelScope.launch {
            val hasAnyChecked = _selectedBookIds.value.isNotEmpty()
            if (hasAnyChecked) return@launch
            val persistedFilterBook = persistedSearchState().filterBookId
            val hasExplicitToc = _uiState.value.scopeTocId != null
            if (persistedFilterBook <= 0L && !hasExplicitToc) {
                _uiState.value = _uiState.value.copy(scopeBook = null)
            }
        }
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
        val children: Map<Long, List<TocEntry>> =
            all
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
    suspend fun getBreadcrumbPiecesFor(result: SearchResult): List<String> = getBreadcrumbPieces(result)

    fun openResult(result: SearchResult) {
        viewModelScope.launch {
            val newTabId = UUID.randomUUID().toString()
            // Seed persisted state so the new tab can restore scroll/anchor deterministically.
            persistedStore.update(newTabId) { current ->
                current.copy(
                    bookContent =
                        current.bookContent.copy(
                            selectedBookId = result.bookId,
                            selectedLineId = result.lineId,
                            contentAnchorLineId = result.lineId,
                            contentAnchorIndex = 0,
                            contentScrollIndex = 0,
                            contentScrollOffset = 0,
                            isTocVisible = true,
                        ),
                    search = null,
                )
            }

            // Pre-configure find-in-page with the search query and smart mode enabled
            val searchQuery = _uiState.value.query
            if (searchQuery.length >= 2) {
                AppSettings.setFindQuery(newTabId, searchQuery)
                AppSettings.setFindSmartMode(newTabId, true)
                AppSettings.openFindBar(newTabId)
            }

            tabsViewModel.openTab(
                TabsDestination.BookContent(
                    bookId = result.bookId,
                    tabId = newTabId,
                    lineId = result.lineId,
                ),
            )
        }
    }

    /**
     * Opens a search result either in the current tab (default) or a new tab when requested.
     * - Current tab: pre-save selected book and anchor on this tabId, then replace destination.
     * - New tab: keep existing behavior (pre-init and navigate to new tab).
     */
    fun openResult(
        result: SearchResult,
        openInNewTab: Boolean,
    ) {
        if (openInNewTab) {
            openResult(result)
            return
        }
        viewModelScope.launch {
            persistedStore.update(tabId) { current ->
                current.copy(
                    bookContent =
                        current.bookContent.copy(
                            selectedBookId = result.bookId,
                            selectedLineId = result.lineId,
                            contentAnchorLineId = result.lineId,
                            contentAnchorIndex = 0,
                            contentScrollIndex = 0,
                            contentScrollOffset = 0,
                            isTocVisible = true,
                        ),
                )
            }

            // Pre-configure find-in-page with the search query and smart mode enabled
            val searchQuery = _uiState.value.query
            if (searchQuery.length >= 2) {
                AppSettings.setFindQuery(tabId, searchQuery)
                AppSettings.setFindSmartMode(tabId, true)
                AppSettings.openFindBar(tabId)
            }

            // Swap current tab destination to BookContent while preserving tabId
            tabsViewModel.replaceCurrentTabDestination(
                TabsDestination.BookContent(
                    bookId = result.bookId,
                    tabId = tabId,
                    lineId = result.lineId,
                ),
            )
        }
    }

    private suspend fun collectLineIdsForTocSubtree(
        tocId: Long,
        bookId: Long,
    ): Set<Long> {
        val index = ensureTocLineIndex(bookId)
        return index.subtreeLineIds(tocId).toSet()
    }

    private suspend fun getTocSubtreeTocIds(
        rootTocId: Long,
        bookId: Long,
    ): Set<Long> {
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

    private suspend fun updateAggregatesForHits(hits: List<LineHit>) {
        countsMutex.withLock {
            for (hit in hits) {
                val book = bookCache[hit.bookId] ?: repository.getBookCore(hit.bookId)?.also { bookCache[hit.bookId] = it } ?: continue
                bookCountsAcc[book.id] = (bookCountsAcc[book.id] ?: 0) + 1
                val path =
                    categoryPathCache[book.categoryId]
                        ?: buildCategoryPath(book.categoryId).also { categoryPathCache[book.categoryId] = it }
                for (cat in path) {
                    categoryCountsAcc[cat.id] = (categoryCountsAcc[cat.id] ?: 0) + 1
                }
                val set = booksForCategoryAcc.getOrPut(book.categoryId) { mutableSetOf() }
                set += book
            }
            _categoryAgg.value =
                CategoryAgg(
                    categoryCounts = categoryCountsAcc.toMap(),
                    bookCounts = bookCountsAcc.toMap(),
                    booksForCategory = booksForCategoryAcc.mapValues { it.value.toList() },
                )
        }
    }

    private suspend fun updateTocCountsForHits(
        hits: List<LineHit>,
        scopeBookId: Long,
    ) {
        val subset = hits.filter { it.bookId == scopeBookId }
        if (subset.isEmpty()) return
        ensureTocCountingCaches(scopeBookId)
        countsMutex.withLock {
            for (hit in subset) {
                val tocId = lineIdToTocId[hit.lineId] ?: continue
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

    private suspend fun updateAggregatesForPage(page: List<SearchResult>) {
        countsMutex.withLock {
            for (res in page) {
                val book = bookCache[res.bookId] ?: repository.getBookCore(res.bookId)?.also { bookCache[res.bookId] = it } ?: continue
                bookCountsAcc[book.id] = (bookCountsAcc[book.id] ?: 0) + 1
                val path =
                    categoryPathCache[book.categoryId]
                        ?: buildCategoryPath(book.categoryId).also { categoryPathCache[book.categoryId] = it }
                for (cat in path) {
                    categoryCountsAcc[cat.id] = (categoryCountsAcc[cat.id] ?: 0) + 1
                }
                val set = booksForCategoryAcc.getOrPut(book.categoryId) { mutableSetOf() }
                set += book
            }
            _categoryAgg.value =
                CategoryAgg(
                    categoryCounts = categoryCountsAcc.toMap(),
                    bookCounts = bookCountsAcc.toMap(),
                    booksForCategory = booksForCategoryAcc.mapValues { it.value.toList() },
                )
        }
    }

    private suspend fun updateTocCountsForPage(
        page: List<SearchResult>,
        scopeBookId: Long,
    ) {
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

    private suspend fun recomputeTocCountsForBook(
        bookId: Long,
        results: List<SearchResult>,
    ) {
        countsMutex.withLock {
            tocCountsAcc.clear()
        }
        updateTocCountsForPage(results, bookId)
    }

    private suspend fun ensureTocCountingCaches(bookId: Long) {
        if (cachedCountsBookId == bookId && lineIdToTocId.isNotEmpty() && tocParentById.isNotEmpty()) return
        val index = ensureTocLineIndex(bookId)
        lineIdToTocId = index.lineIdToTocId
        tocParentById = index.parent
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
        uiState.value.scopeBook
            ?.id
            ?.let { recomputeTocCountsForBook(it, results) }
    }

    private suspend fun ensureTocLineIndex(bookId: Long): TocLineIndex {
        tocLineIndexCache[bookId]?.let { return it }
        val mappings = runCatching { repository.getLineTocMappingsForBook(bookId) }.getOrElse { emptyList() }
        val grouped =
            mappings.groupBy { it.tocEntryId }.mapValues { entry ->
                entry.value
                    .map { it.lineId }
                    .sorted()
                    .toLongArray()
            }
        val tree = if (currentTocBookId == bookId) _tocTree.value else null
        val tocEntries =
            tree?.let { tree.rootEntries + tree.children.values.flatten() }
                ?: runCatching { repository.getBookToc(bookId) }.getOrElse { emptyList() }
        val children = mutableMapOf<Long, MutableList<Long>>()
        val parent = mutableMapOf<Long, Long?>()
        for (t in tocEntries) {
            parent[t.id] = t.parentId
            if (t.parentId != null) {
                children.getOrPut(t.parentId!!) { mutableListOf() }.add(t.id)
            }
        }
        val index =
            TocLineIndex(
                tocToLines = grouped,
                children = children.mapValues { it.value.toList() },
                parent = parent,
            )
        tocLineIndexCache[bookId] = index
        return index
    }

    private data class TocLineIndex(
        val tocToLines: Map<Long, LongArray>,
        val children: Map<Long, List<Long>>,
        val parent: Map<Long, Long?>,
        private val subtreeCache: MutableMap<Long, LongArray> = mutableMapOf(),
    ) {
        val lineIdToTocId: Map<Long, Long> =
            tocToLines
                .flatMap { (k, v) -> v.map { it to k } }
                .toMap()

        fun subtreeLineIds(tocId: Long): LongArray {
            subtreeCache[tocId]?.let { return it }
            val self = tocToLines[tocId] ?: LongArray(0)
            val childIds = children[tocId].orEmpty()
            if (childIds.isEmpty()) {
                subtreeCache[tocId] = self
                return self
            }
            val arrays = ArrayList<LongArray>(childIds.size + 1)
            if (self.isNotEmpty()) arrays.add(self)
            for (child in childIds) {
                val arr = subtreeLineIds(child)
                if (arr.isNotEmpty()) arrays.add(arr)
            }
            val merged = mergeLongArraysLocal(arrays)
            subtreeCache[tocId] = merged
            return merged
        }

        private fun mergeLongArraysLocal(arrays: List<LongArray>): LongArray {
            if (arrays.isEmpty()) return LongArray(0)
            if (arrays.size == 1) return arrays[0]
            var total = 0
            for (a in arrays) total += a.size
            if (total == 0) return LongArray(0)
            val out = LongArray(total)
            var pos = 0
            for (a in arrays) {
                System.arraycopy(a, 0, out, pos, a.size)
                pos += a.size
            }
            Arrays.parallelSort(out)
            var write = 0
            var i = 0
            while (i < out.size) {
                val v = out[i]
                if (write == 0 || out[write - 1] != v) {
                    out[write++] = v
                }
                i++
            }
            return if (write == out.size) out else out.copyOf(write)
        }
    }
}
