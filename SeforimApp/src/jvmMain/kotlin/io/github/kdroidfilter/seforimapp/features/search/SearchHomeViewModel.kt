package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.search.LuceneLookupSearchService
import io.github.kdroidfilter.seforimapp.framework.session.SearchPersistedState
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Navigation events emitted by SearchHomeViewModel.
 * The UI layer is responsible for handling these events and performing actual navigation.
 */
sealed class SearchHomeNavigationEvent {
    /**
     * Navigate to search results screen.
     * @param query The search query
     * @param tabId The current tab ID
     */
    data class NavigateToSearch(
        val query: String,
        val tabId: String,
    ) : SearchHomeNavigationEvent()

    /**
     * Navigate to book content screen.
     * @param bookId The book to open
     * @param tabId The current tab ID
     * @param lineId Optional line ID to scroll to
     */
    data class NavigateToBookContent(
        val bookId: Long,
        val tabId: String,
        val lineId: Long?,
    ) : SearchHomeNavigationEvent()
}

@Immutable
data class CategorySuggestionDto(
    val category: Category,
    val path: List<String>,
)

@Immutable
data class BookSuggestionDto(
    val book: Book,
    val path: List<String>,
)

@Immutable
data class TocSuggestionDto(
    val toc: TocEntry,
    val path: List<String>,
)

@Immutable
data class SearchHomeUiState(
    val selectedFilter: SearchFilter = SearchFilter.TEXT,
    val globalExtended: Boolean = false,
    val suggestionsVisible: Boolean = false,
    val isReferenceLoading: Boolean = false,
    val categorySuggestions: List<CategorySuggestionDto> = emptyList(),
    val bookSuggestions: List<BookSuggestionDto> = emptyList(),
    val tocSuggestionsVisible: Boolean = false,
    val isTocLoading: Boolean = false,
    val tocSuggestions: List<TocSuggestionDto> = emptyList(),
    val selectedScopeCategory: Category? = null,
    val selectedScopeBook: Book? = null,
    val selectedScopeToc: TocEntry? = null,
    val userDisplayName: String = "",
    val userCommunityCode: String? = null,
    val tocPreviewHints: List<String> = emptyList(),
    val pairedReferenceHints: List<Pair<String, String>> = emptyList(),
)

@OptIn(FlowPreview::class)
class SearchHomeViewModel(
    private val persistedStore: TabPersistedStateStore,
    private val repository: SeforimRepository,
    private val lookup: LuceneLookupSearchService,
    private val settings: Settings,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchHomeUiState())
    val uiState: StateFlow<SearchHomeUiState> = _uiState.asStateFlow()

    // Navigation events channel - UI collects and handles navigation
    private val _navigationEvents = Channel<SearchHomeNavigationEvent>(Channel.BUFFERED)
    val navigationEvents = _navigationEvents.receiveAsFlow()

    private val referenceQuery = MutableStateFlow("")
    private val tocQuery = MutableStateFlow("")

    private val MIN_BOOK_PREFIX_LEN = 2 // minimum characters before triggering book predictive queries
    private val MIN_TOC_PREFIX_LEN = 1 // minimum characters before triggering TOC predictive queries
    private val MAX_BOOK_PREDICTIVE = 120 // tighter ceiling to avoid heavy allocations
    private val MAX_TOC_PREDICTIVE = 300 // TOC suggestions stay bounded

    // Lightweight, thread-safe LRU caches to avoid repeated DB hits when typing fast
    private class LruCache<K, V>(
        private val maxSize: Int,
    ) : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maxSize
    }

    private val categoryDepthCache = LruCache<Long, Int>(512)
    private val categoryPathCache = LruCache<Long, List<String>>(512)
    private val tocPathCache = LruCache<Long, List<String>>(2048)
    private val tocCache = mutableMapOf<Long, List<TocSuggestionDto>>()

    private fun matchRank(
        text: String,
        query: String,
    ): Int =
        when {
            text.equals(query, ignoreCase = true) -> 0
            text.startsWith(query, ignoreCase = true) -> 1
            text.contains(query, ignoreCase = true) -> 2
            else -> 3
        }

    private suspend fun getCategoryDepthCached(catId: Long): Int {
        synchronized(categoryDepthCache) { categoryDepthCache[catId]?.let { return it } }
        val depth =
            withContext(Dispatchers.IO) {
                runCatching { repository.getCategoryDepth(catId) }.getOrDefault(Int.MAX_VALUE)
            }
        synchronized(categoryDepthCache) { categoryDepthCache[catId] = depth }
        return depth
    }

    private suspend fun buildCategoryPathTitlesCached(catId: Long): List<String> {
        synchronized(categoryPathCache) { categoryPathCache[catId]?.let { return it } }
        val path = withContext(Dispatchers.IO) { runCatching { buildCategoryPathTitles(catId) }.getOrDefault(emptyList()) }
        synchronized(categoryPathCache) { categoryPathCache[catId] = path }
        return path
    }

    private suspend fun buildTocPathTitlesCached(entry: TocEntry): List<String> {
        val key = entry.id
        synchronized(tocPathCache) { tocPathCache[key]?.let { return it } }
        val path = withContext(Dispatchers.IO) { runCatching { buildTocPathTitles(entry) }.getOrDefault(emptyList()) }
        synchronized(tocPathCache) { tocPathCache[key] = path }
        return path
    }

    init {
        // Build display name from injected Settings
        runCatching {
            val firstName: String = settings["user_first_name", ""]
            val lastName: String = settings["user_last_name", ""]
            val displayName = "$firstName $lastName".trim()
            _uiState.value = _uiState.value.copy(userDisplayName = displayName)
        }
        // Observe changes in user profile and keep display name in sync
        viewModelScope.launch {
            AppSettings.userFirstNameFlow
                .combine(AppSettings.userLastNameFlow) { f, l -> "$f $l".trim() }
                .distinctUntilChanged()
                .collect { displayName ->
                    _uiState.value = _uiState.value.copy(userDisplayName = displayName)
                }
        }
        viewModelScope.launch {
            AppSettings.userCommunityCodeFlow
                .collect { code ->
                    _uiState.value = _uiState.value.copy(userCommunityCode = code)
                }
        }
        // Debounced suggestions based on reference query
        viewModelScope.launch {
            referenceQuery
                .debounce(120)
                .distinctUntilChanged()
                .collectLatest { qRaw ->
                    val q = qRaw.trim()
                    val qNorm = sanitizeHebrewForAcronym(q)
                    if (q.isBlank()) {
                        _uiState.value =
                            _uiState.value.copy(
                                isReferenceLoading = false,
                                categorySuggestions = emptyList(),
                                bookSuggestions = emptyList(),
                                suggestionsVisible = false,
                            )
                    } else {
                        val startLoading = q.length >= MIN_BOOK_PREFIX_LEN
                        _uiState.value =
                            _uiState.value.copy(
                                isReferenceLoading = startLoading,
                                suggestionsVisible = true,
                            )
                        val result =
                            withContext(Dispatchers.Default) {
                                coroutineScope {
                                    val pattern = "%$q%"

                                    // Helper ranks by quick string match only (cheap)
                                    fun catTitleRank(title: String): Int =
                                        when {
                                            title.equals(q, ignoreCase = true) -> 0
                                            title.startsWith(q, ignoreCase = true) -> 1
                                            title.contains(q, ignoreCase = true) -> 2
                                            else -> 3
                                        }

                                    fun titleRank(title: String): Int =
                                        when {
                                            title.equals(q, ignoreCase = true) -> 0
                                            title.startsWith(q, ignoreCase = true) -> 1
                                            title.contains(q, ignoreCase = true) -> 2
                                            else -> 3
                                        }

                                    // Categories: fetch, cheap-rank, compute depth for top-N, then build paths for final
                                    val catsDeferred =
                                        async(Dispatchers.IO) {
                                            val catsRaw =
                                                repository
                                                    .findCategoriesByTitleLike(pattern, limit = 50)
                                                    .filter { it.title.isNotBlank() }
                                                    .distinctBy { it.id }
                                            val topForDepth =
                                                catsRaw
                                                    .sortedBy { catTitleRank(it.title) }
                                                    .take(24)
                                            val withDepth =
                                                topForDepth.map { cat ->
                                                    // Depth via cache for ranking
                                                    val depth = getCategoryDepthCached(cat.id)
                                                    cat to depth
                                                }
                                            val topFinal =
                                                withDepth
                                                    .sortedWith(
                                                        compareBy<Pair<Category, Int>> { it.second }
                                                            .thenBy { catTitleRank(it.first.title) },
                                                    ).take(12)
                                                    .map { it.first }
                                            // Build display paths only for final items
                                            topFinal.map { cat ->
                                                val path = buildCategoryPathTitlesCached(cat.id)
                                                CategorySuggestionDto(cat, path.ifEmpty { listOf(cat.title) })
                                            }
                                        }

                                    // Books: enforce 2-char minimum; if shorter, return empty suggestions for books
                                    val booksDeferred =
                                        async(Dispatchers.Default) {
                                            if (q.length < MIN_BOOK_PREFIX_LEN) {
                                                emptyList<BookSuggestionDto>()
                                            } else {
                                                val bookHits = lookup.searchBooksWithScoring(qNorm, limit = MAX_BOOK_PREDICTIVE)
                                                bookHits
                                                    // Already sorted by score in searchBooksWithScoring, no need to re-sort
                                                    .take(MAX_BOOK_PREDICTIVE)
                                                    .map { hit ->
                                                        val book =
                                                            Book(
                                                                id = hit.id,
                                                                categoryId = hit.categoryId,
                                                                sourceId = 0,
                                                                title = hit.title,
                                                                order = hit.orderIndex.toFloat(),
                                                                isBaseBook = hit.isBaseBook,
                                                            )
                                                        val catPath = buildCategoryPathTitlesCached(book.categoryId)
                                                        BookSuggestionDto(book, catPath + book.title)
                                                    }
                                            }
                                        }

                                    val cats = catsDeferred.await()
                                    val books = booksDeferred.await()
                                    cats to books
                                }
                            }

                        val (catSuggestions, bookSuggestions) = result
                        _uiState.value =
                            _uiState.value.copy(
                                isReferenceLoading = false,
                                categorySuggestions = catSuggestions,
                                bookSuggestions = bookSuggestions,
                                suggestionsVisible = true,
                            )
                    }
                }
        }

        // Debounced suggestions for TOC query (only when a book is selected)
        viewModelScope.launch {
            tocQuery
                .debounce(120)
                .distinctUntilChanged()
                .collectLatest { qRaw ->
                    val q = qRaw.trim()
                    val book = _uiState.value.selectedScopeBook
                    val cached = book?.let { tocCache[it.id] }.orEmpty()
                    when {
                        book == null ->
                            _uiState.value =
                                _uiState.value.copy(
                                    tocSuggestions = emptyList(),
                                    tocSuggestionsVisible = false,
                                    isTocLoading = false,
                                )
                        q.length < MIN_TOC_PREFIX_LEN ->
                            _uiState.value =
                                _uiState.value.copy(
                                    tocSuggestions = cached,
                                    tocSuggestionsVisible = cached.isNotEmpty(),
                                    isTocLoading = false,
                                )
                        else -> {
                            _uiState.value =
                                _uiState.value.copy(
                                    isTocLoading = true,
                                    tocSuggestionsVisible = true,
                                )
                            val suggestions =
                                cached
                                    .asSequence()
                                    .filter { it.toc.text.contains(q, ignoreCase = true) }
                                    .sortedWith(
                                        compareBy<TocSuggestionDto> { matchRank(it.toc.text, q) }
                                            .thenBy { it.toc.level }
                                            .thenBy { it.toc.text.length },
                                    ).toList()
                            _uiState.value =
                                _uiState.value.copy(
                                    tocSuggestions = suggestions,
                                    tocSuggestionsVisible = true,
                                    isTocLoading = false,
                                )
                        }
                    }
                }
        }
    }

    fun onReferenceQueryChanged(query: String) {
        referenceQuery.value = query
        if (query.isBlank()) {
            _uiState.value =
                _uiState.value.copy(
                    selectedScopeCategory = null,
                    selectedScopeBook = null,
                    selectedScopeToc = null,
                    tocPreviewHints = emptyList(),
                    isReferenceLoading = false,
                )
        }
    }

    fun onTocQueryChanged(query: String) {
        tocQuery.value = query
        if (query.isBlank()) {
            _uiState.value =
                _uiState.value.copy(
                    selectedScopeToc = null,
                    tocSuggestionsVisible = _uiState.value.tocSuggestions.isNotEmpty(),
                    isTocLoading = false,
                )
        }
    }

    fun onPickCategory(category: Category) {
        _uiState.value =
            _uiState.value.copy(
                selectedScopeCategory = category,
                selectedScopeBook = null,
                selectedScopeToc = null,
                suggestionsVisible = false,
                tocSuggestionsVisible = false,
                tocSuggestions = emptyList(),
                tocPreviewHints = emptyList(),
                isReferenceLoading = false,
                isTocLoading = false,
            )
    }

    fun onPickBook(book: Book) {
        // Update synchronously first
        _uiState.value =
            _uiState.value.copy(
                selectedScopeCategory = null,
                selectedScopeBook = book,
                selectedScopeToc = null,
                suggestionsVisible = false,
                tocSuggestionsVisible = false,
                tocSuggestions = emptyList(),
                tocPreviewHints = emptyList(),
                isReferenceLoading = false,
                isTocLoading = true,
            )
        // Load preview hints and initial TOC suggestions asynchronously
        viewModelScope.launch {
            val tocEntries =
                tocCache[book.id] ?: withContext(Dispatchers.Default) {
                    val entries = runCatching { repository.getBookToc(book.id) }.getOrElse { emptyList() }
                    val built = mutableListOf<TocSuggestionDto>()
                    val sorted =
                        entries
                            .asSequence()
                            .filter { it.text.isNotBlank() }
                            .sortedWith(compareBy<TocEntry> { it.level }.thenBy { it.text })
                            .toList()
                    for (toc in sorted) {
                        val path = buildTocPathTitlesCached(toc).filter { it.isNotBlank() }
                        if (path.isNotEmpty()) {
                            built += TocSuggestionDto(toc, path)
                        }
                    }
                    tocCache[book.id] = built
                    built
                }
            val preview =
                tocEntries
                    .mapNotNull { it.toc.text.takeIf { t -> t.isNotBlank() } }
                    .distinct()
                    .take(5)
                    .toList()
            val initialSuggestions = tocEntries.take(MAX_TOC_PREDICTIVE)
            _uiState.value =
                _uiState.value.copy(
                    tocPreviewHints = preview,
                    tocSuggestions = initialSuggestions,
                    tocSuggestionsVisible = initialSuggestions.isNotEmpty(),
                    isTocLoading = false,
                )
        }
    }

    fun onPickToc(toc: TocEntry) {
        _uiState.value =
            _uiState.value.copy(
                selectedScopeToc = toc,
                tocSuggestionsVisible = false,
                isTocLoading = false,
            )
    }

    fun onFilterChange(filter: SearchFilter) {
        _uiState.value = _uiState.value.copy(selectedFilter = filter)
    }

    fun onGlobalExtendedChange(extended: Boolean) {
        _uiState.value = _uiState.value.copy(globalExtended = extended)
    }

    /**
     * Dismisses all suggestion popups. Called by the UI layer when navigating away from Home.
     */
    fun dismissSuggestions() {
        _uiState.value =
            _uiState.value.copy(
                suggestionsVisible = false,
                tocSuggestionsVisible = false,
                isReferenceLoading = false,
                isTocLoading = false,
            )
    }

    suspend fun submitSearch(
        query: String,
        currentTabId: String,
    ) {
        // Apply selected scope only (view filters) and persist dataset scope for fetch
        val selected = _uiState.value
        val datasetScope: String
        val filterCategoryId: Long
        val filterBookId: Long
        val filterTocId: Long
        val fetchCategoryId: Long
        val fetchBookId: Long
        val fetchTocId: Long
        when {
            selected.selectedScopeToc != null -> {
                val toc = selected.selectedScopeToc
                datasetScope = "toc"
                filterCategoryId = 0L
                filterBookId = toc.bookId
                filterTocId = toc.id
                fetchCategoryId = 0L
                fetchBookId = toc.bookId
                fetchTocId = toc.id
            }
            selected.selectedScopeBook != null -> {
                val book = selected.selectedScopeBook
                datasetScope = "book"
                filterCategoryId = 0L
                filterBookId = book.id
                filterTocId = 0L
                fetchCategoryId = 0L
                fetchBookId = book.id
                fetchTocId = 0L
            }
            selected.selectedScopeCategory != null -> {
                val cat = selected.selectedScopeCategory
                datasetScope = "category"
                filterCategoryId = cat.id
                filterBookId = 0L
                filterTocId = 0L
                fetchCategoryId = cat.id
                fetchBookId = 0L
                fetchTocId = 0L
            }
            else -> {
                datasetScope = "global"
                filterCategoryId = 0L
                filterBookId = 0L
                filterTocId = 0L
                fetchCategoryId = 0L
                fetchBookId = 0L
                fetchTocId = 0L
            }
        }

        persistedStore.update(currentTabId) { current ->
            val nextSearch =
                (current.search ?: SearchPersistedState()).copy(
                    query = query,
                    globalExtended = selected.globalExtended,
                    datasetScope = datasetScope,
                    filterCategoryId = filterCategoryId,
                    filterBookId = filterBookId,
                    filterTocId = filterTocId,
                    fetchCategoryId = fetchCategoryId,
                    fetchBookId = fetchBookId,
                    fetchTocId = fetchTocId,
                    selectedCategoryIds = emptySet(),
                    selectedBookIds = emptySet(),
                    selectedTocIds = emptySet(),
                    scrollIndex = 0,
                    scrollOffset = 0,
                    anchorId = -1L,
                    anchorIndex = 0,
                    snapshot = null,
                    breadcrumbs = emptyMap(),
                )
            current.copy(search = nextSearch)
        }

        // Clear any previous cached search snapshot for this tab to avoid
        // reusing stale results when a new search is submitted.
        SearchTabCache.clear(currentTabId)

        // Emit navigation event - UI layer handles actual navigation
        _navigationEvents.send(SearchHomeNavigationEvent.NavigateToSearch(query, currentTabId))
    }

    /**
     * Opens the selected reference (book/TOC) in the current tab.
     * - If a TOC entry is selected, tries to open at its first line.
     * - Otherwise opens the selected book at its beginning.
     * @param currentTabId The ID of the current tab where navigation should occur
     */
    suspend fun openSelectedReferenceInCurrentTab(currentTabId: String) {
        val selectedToc = _uiState.value.selectedScopeToc
        val selectedBook = _uiState.value.selectedScopeBook

        // Resolve book and optional line anchor
        val book =
            when {
                selectedBook != null -> selectedBook
                selectedToc != null -> runCatching { repository.getBookCore(selectedToc.bookId) }.getOrNull()
                else -> null
            } ?: return

        val anchorLineId: Long? =
            when (selectedToc) {
                null -> null
                else -> runCatching { repository.getLineIdsForTocEntry(selectedToc.id).firstOrNull() }.getOrNull()
            }

        // Pre-seed minimal state so the BookContent shell can show a loader instead of flashing Home.
        persistedStore.update(currentTabId) { current ->
            current.copy(bookContent = current.bookContent.copy(selectedBookId = book.id))
        }

        // Emit navigation event - UI layer handles actual navigation
        _navigationEvents.send(
            SearchHomeNavigationEvent.NavigateToBookContent(
                bookId = book.id,
                tabId = currentTabId,
                lineId = anchorLineId,
            ),
        )
    }

    private suspend fun buildCategoryPathTitles(catId: Long): List<String> {
        val path = mutableListOf<String>()
        var currentId: Long? = catId
        val safety = 64
        var guard = 0
        while (currentId != null && guard++ < safety) {
            val c = repository.getCategory(currentId) ?: break
            path += c.title
            currentId = c.parentId
        }
        return path.asReversed()
    }

    // Sanitization aligned with the generator’s acronym normalization, but minimal and local to app
    private fun sanitizeHebrewForAcronym(input: String): String {
        if (input.isBlank()) return ""
        var s = input.trim()
        // Remove Hebrew diacritics: teamim U+0591–U+05AF
        s = s.replace("[\u0591-\u05AF]".toRegex(), "")
        // Remove nikud signs (set incl. meteg U+05BD and QAMATZ QATAN U+05C7)
        val nikud = "[\u05B0\u05B1\u05B2\u05B3\u05B4\u05B5\u05B6\u05B7\u05B8\u05B9\u05BB\u05BC\u05BD\u05C1\u05C2\u05C7]".toRegex()
        s = s.replace(nikud, "")
        // Replace maqaf (U+05BE) with space
        s = s.replace('\u05BE', ' ')
        // Remove gershayim (U+05F4) and geresh (U+05F3)
        s = s.replace("\u05F4", "").replace("\u05F3", "")
        // Collapse whitespace
        s = s.replace("\\s+".toRegex(), " ").trim()
        return s
    }

    // Build an FTS5 MATCH string with prefix search, quoting tokens safely and
    // dropping punctuation-only tokens to avoid syntax errors (e.g., near ">").
    private fun toFtsPrefixQuery(tokens: List<String>): String {
        fun hasWordChar(s: String): Boolean = s.any { it.isLetterOrDigit() }
        return tokens
            .map { it.trim() }
            .filter { it.isNotEmpty() && hasWordChar(it) }
            .joinToString(" ") { token ->
                val base = token.trim().trimEnd('*')
                val escaped = base.replace("\"", "\"\"")
                "\"$escaped\"*"
            }
    }

    private suspend fun buildTocPathTitles(entry: TocEntry): List<String> {
        val bookTitle = runCatching { repository.getBookCore(entry.bookId)?.title }.getOrNull()
        val tocTitles = mutableListOf<String>()
        var current: TocEntry? = entry
        val safety = 128
        var guard = 0
        while (current != null && guard++ < safety) {
            tocTitles += current.text
            current = current.parentId?.let { pid -> runCatching { repository.getTocEntry(pid) }.getOrNull() }
        }
        val path = tocTitles.asReversed()
        val combined = if (bookTitle != null) listOf(bookTitle) + path else path
        return dedupAdjacent(combined)
    }

    private fun dedupAdjacent(parts: List<String>): List<String> {
        if (parts.isEmpty()) return parts

        fun extends(
            prev: String,
            next: String,
        ): Boolean {
            val a = prev.trim()
            val b = next.trim()
            if (b.length <= a.length) return false
            if (!b.startsWith(a)) return false
            val ch = b[a.length]
            return ch == ',' || ch == ' ' || ch == ':' || ch == '-' || ch == '—'
        }
        val out = ArrayList<String>(parts.size)
        for (p in parts) {
            if (out.isEmpty()) {
                out += p
            } else {
                val last = out.last()
                when {
                    p == last -> { /* skip */ }
                    extends(last, p) -> out[out.lastIndex] = p
                    else -> out += p
                }
            }
        }
        return out
    }
}
