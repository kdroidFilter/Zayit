package io.github.kdroidfilter.seforimapp.features.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabStateManager
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimapp.framework.search.LuceneSearchService
import io.github.kdroidfilter.seforimapp.framework.search.LuceneLookupSearchService
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings

data class CategorySuggestionDto(val category: Category, val path: List<String>)
data class BookSuggestionDto(val book: Book, val path: List<String>)
data class TocSuggestionDto(val toc: TocEntry, val path: List<String>)

data class SearchHomeUiState(
    val selectedFilter: SearchFilter = SearchFilter.TEXT,
    val selectedLevelIndex: Int = 2,
    val suggestionsVisible: Boolean = false,
    val categorySuggestions: List<CategorySuggestionDto> = emptyList(),
    val bookSuggestions: List<BookSuggestionDto> = emptyList(),
    val tocSuggestionsVisible: Boolean = false,
    val tocSuggestions: List<TocSuggestionDto> = emptyList(),
    val selectedScopeCategory: Category? = null,
    val selectedScopeBook: Book? = null,
    val selectedScopeToc: TocEntry? = null,
    val userDisplayName: String = "",
    // Hints for the 2nd field placeholder when a book is selected
    val tocPreviewHints: List<String> = emptyList(),
    val pairedReferenceHints: List<Pair<String, String>> = emptyList()
)

class SearchHomeViewModel(
    private val tabsViewModel: TabsViewModel,
    private val stateManager: TabStateManager,
    private val repository: SeforimRepository,
    private val lucene: LuceneSearchService,
    private val lookup: LuceneLookupSearchService,
    private val settings: Settings
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchHomeUiState())
    val uiState: StateFlow<SearchHomeUiState> = _uiState.asStateFlow()

    private val referenceQuery = MutableStateFlow("")
    private val tocQuery = MutableStateFlow("")

    private val NEAR_LEVELS = listOf(0, 3, 5, 10, 20)

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
        // Debounced suggestions based on reference query
        viewModelScope.launch {
            referenceQuery
                .debounce(200)
                .distinctUntilChanged()
                .collect { qRaw ->
                    val q = qRaw.trim()
                    val qNorm = sanitizeHebrewForAcronym(q)
                    if (q.isBlank()) {
                        _uiState.value = _uiState.value.copy(
                            categorySuggestions = emptyList(),
                            bookSuggestions = emptyList(),
                            suggestionsVisible = false
                        )
                    } else {
                        val pattern = "%$q%"
                        // Categories
                        val catsRaw = repository
                            .findCategoriesByTitleLike(pattern, limit = 50)
                            .filter { it.title.isNotBlank() }
                            .distinctBy { it.id }
                        // Rank helper for category titles against raw query
                        fun catTitleRank(title: String): Int = when {
                            title.equals(q, ignoreCase = true) -> 0
                            title.startsWith(q, ignoreCase = true) -> 1
                            title.contains(q, ignoreCase = true) -> 2
                            else -> 3
                        }
                        val catItems = catsRaw.map { cat ->
                            val path = buildCategoryPathTitles(cat.id)
                            val depth = repository.getCategoryDepth(cat.id)
                            CategorySuggestionDto(cat, path.ifEmpty { listOf(cat.title) }) to depth
                        }
                        // Prioritize: 1) shallower hierarchy, 2) better title match
                        val catSuggestions = catItems
                            .sortedWith(
                                compareBy<Pair<CategorySuggestionDto, Int>> { it.second }
                                    .thenBy { catTitleRank(it.first.category.title) }
                            )
                            .map { it.first }
                            .take(12)
                        // Books via lookup index (title variants + acronyms + topics) with prefix per token
                        val bookIds = lookup.searchBooksPrefix(qNorm, limit = 50)
                        val lookupBooks = bookIds.mapNotNull { id ->
                            runCatching { repository.getBook(id) }.getOrNull()
                        }

                        // Fallback: broaden with simple title LIKE when lookup misses matches
                        // This improves coverage for partial/substring inputs and unusual tokenization.
                        val likeBooks = runCatching {
                            repository.findBooksByTitleLike("%$q%", limit = 50)
                                .filter { it.title.isNotBlank() }
                        }.getOrDefault(emptyList())

                        // Merge while preserving lookup order first, then LIKE results without duplicates
                        val bookCandidates = LinkedHashMap<Long, Book>()
                        lookupBooks.forEach { b -> bookCandidates.putIfAbsent(b.id, b) }
                        likeBooks.forEach { b -> bookCandidates.putIfAbsent(b.id, b) }

                        val bookItems = bookCandidates.values.map { book ->
                            val catPath = buildCategoryPathTitles(book.categoryId)
                            val depth = repository.getCategoryDepth(book.categoryId)
                            BookSuggestionDto(book, catPath + book.title) to depth
                        }

                        fun titleRank(title: String): Int = when {
                            title.equals(q, ignoreCase = true) -> 0
                            title.startsWith(q, ignoreCase = true) -> 1
                            title.contains(q, ignoreCase = true) -> 2
                            else -> 3
                        }

                        // Prioritize: 1) shallower hierarchy, 2) real title match
                        val bookSuggestions = bookItems
                            .sortedWith(
                                compareBy<Pair<BookSuggestionDto, Int>> { it.second }
                                    .thenBy { titleRank(it.first.book.title) }
                            )
                            .map { it.first }
                            .take(12)
                        _uiState.value = _uiState.value.copy(
                            categorySuggestions = catSuggestions,
                            bookSuggestions = bookSuggestions,
                            suggestionsVisible = catSuggestions.isNotEmpty() || bookSuggestions.isNotEmpty()
                        )
                    }
                }
        }

        // Debounced suggestions for TOC query (only when a book is selected)
        viewModelScope.launch {
            tocQuery
                .debounce(200)
                .distinctUntilChanged()
                .collect { qRaw ->
                    val q = qRaw.trim()
                    val book = _uiState.value.selectedScopeBook
                    if (q.isBlank() || book == null) {
                        _uiState.value = _uiState.value.copy(
                            tocSuggestions = emptyList(),
                            tocSuggestionsVisible = false
                        )
                    } else {
                        val allToc = repository.getBookToc(book.id)
                        val matches = allToc
                            .asSequence()
                            .filter { it.text.isNotBlank() && it.text.contains(q, ignoreCase = true) }
                            .sortedWith(compareBy<TocEntry> { it.level }.thenBy { it.text })
                            .take(30)
                            .toList()
                        val suggestions = matches.map { toc ->
                            val path = buildTocPathTitles(toc)
                            TocSuggestionDto(toc, path)
                        }
                        _uiState.value = _uiState.value.copy(
                            tocSuggestions = suggestions,
                            tocSuggestionsVisible = suggestions.isNotEmpty()
                        )
                    }
                }
        }

        // Precompute a small set of (book, toc) pairs for synchronized placeholders
        viewModelScope.launch {
            val pairs = try {
                val out = mutableListOf<Pair<String, String>>()
                val books = repository.getAllBooks()
                for (b in books) {
                    val toc = try { repository.getBookToc(b.id) } catch (_: Exception) { emptyList() }
                    val first = toc.firstOrNull { it.text.isNotBlank() }?.text
                    if (first != null && out.none { it.first == b.title }) {
                        out += b.title to first
                    }
                    if (out.size >= 5) break
                }
                out
            } catch (_: Exception) { emptyList() }
            _uiState.value = _uiState.value.copy(pairedReferenceHints = pairs)
        }
    }

    fun onReferenceQueryChanged(query: String) {
        referenceQuery.value = query
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                selectedScopeCategory = null,
                selectedScopeBook = null,
                selectedScopeToc = null,
                tocPreviewHints = emptyList(),
            )
        }
    }

    fun onTocQueryChanged(query: String) {
        tocQuery.value = query
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(selectedScopeToc = null)
        }
    }

    fun onPickCategory(category: Category) {
        _uiState.value = _uiState.value.copy(
            selectedScopeCategory = category,
            selectedScopeBook = null,
            selectedScopeToc = null,
            suggestionsVisible = false,
            tocSuggestionsVisible = false,
            tocSuggestions = emptyList(),
            tocPreviewHints = emptyList()
        )
    }

    fun onPickBook(book: Book) {
        // Update synchronously first
        _uiState.value = _uiState.value.copy(
            selectedScopeCategory = null,
            selectedScopeBook = book,
            selectedScopeToc = null,
            suggestionsVisible = false,
            tocSuggestionsVisible = false,
            tocSuggestions = emptyList(),
            tocPreviewHints = emptyList()
        )
        // Load preview hints asynchronously
        viewModelScope.launch {
            val preview = runCatching {
                repository
                    .getBookToc(book.id)
                    .asSequence()
                    .mapNotNull { it.text.takeIf { t -> t.isNotBlank() } }
                    .distinct()
                    .take(5)
                    .toList()
            }.getOrElse { emptyList() }
            _uiState.value = _uiState.value.copy(tocPreviewHints = preview)
        }
    }

    fun onPickToc(toc: TocEntry) {
        _uiState.value = _uiState.value.copy(
            selectedScopeToc = toc,
            tocSuggestionsVisible = false
        )
    }

    fun onFilterChange(filter: SearchFilter) {
        _uiState.value = _uiState.value.copy(selectedFilter = filter)
    }

    fun onLevelIndexChange(index: Int) {
        val coerced = index.coerceIn(0, NEAR_LEVELS.lastIndex)
        _uiState.value = _uiState.value.copy(selectedLevelIndex = coerced)
    }

    suspend fun submitSearch(query: String) {
        val currentTabs = tabsViewModel.tabs.value
        val currentIndex = tabsViewModel.selectedTabIndex.value
        val currentTabId = currentTabs.getOrNull(currentIndex)?.destination?.tabId ?: return

        // Clear previous filters
        stateManager.saveState(currentTabId, SearchStateKeys.FILTER_CATEGORY_ID, 0L)
        stateManager.saveState(currentTabId, SearchStateKeys.FILTER_BOOK_ID, 0L)
        stateManager.saveState(currentTabId, SearchStateKeys.FILTER_TOC_ID, 0L)

        // Apply selected scope only (view filters) and persist dataset scope for fetch
        var datasetScope = "global"
        _uiState.value.selectedScopeCategory?.let { cat ->
            stateManager.saveState(currentTabId, SearchStateKeys.FILTER_CATEGORY_ID, cat.id)
            stateManager.saveState(currentTabId, SearchStateKeys.DATASET_SCOPE, "category")
            stateManager.saveState(currentTabId, SearchStateKeys.FETCH_CATEGORY_ID, cat.id)
            datasetScope = "category"
        }
        _uiState.value.selectedScopeBook?.let { book ->
            stateManager.saveState(currentTabId, SearchStateKeys.FILTER_BOOK_ID, book.id)
            stateManager.saveState(currentTabId, SearchStateKeys.DATASET_SCOPE, "book")
            stateManager.saveState(currentTabId, SearchStateKeys.FETCH_BOOK_ID, book.id)
            datasetScope = "book"
        }
        _uiState.value.selectedScopeToc?.let { toc ->
            // Ensure book filter matches toc's book as well
            stateManager.saveState(currentTabId, SearchStateKeys.FILTER_BOOK_ID, toc.bookId)
            stateManager.saveState(currentTabId, SearchStateKeys.FILTER_TOC_ID, toc.id)
            stateManager.saveState(currentTabId, SearchStateKeys.DATASET_SCOPE, "toc")
            stateManager.saveState(currentTabId, SearchStateKeys.FETCH_BOOK_ID, toc.bookId)
            stateManager.saveState(currentTabId, SearchStateKeys.FETCH_TOC_ID, toc.id)
            datasetScope = "toc"
        }
        if (datasetScope == "global") {
            // clear any previous fetch-scope remnants
            stateManager.saveState(currentTabId, SearchStateKeys.DATASET_SCOPE, "global")
            stateManager.saveState(currentTabId, SearchStateKeys.FETCH_CATEGORY_ID, 0L)
            stateManager.saveState(currentTabId, SearchStateKeys.FETCH_BOOK_ID, 0L)
            stateManager.saveState(currentTabId, SearchStateKeys.FETCH_TOC_ID, 0L)
        }

        // Persist search params for this tab to restore state
        stateManager.saveState(currentTabId, SearchStateKeys.QUERY, query)
        stateManager.saveState(currentTabId, SearchStateKeys.NEAR, NEAR_LEVELS[_uiState.value.selectedLevelIndex])

        // Clear any previous cached search snapshot for this tab to avoid
        // reusing stale results when a new search is submitted.
        SearchTabCache.clear(currentTabId)
        SearchTabPersistentCache.clear(currentTabId)

        // Also reset persisted scroll/anchor so the SearchResult screen starts at the top
        stateManager.saveState(currentTabId, SearchStateKeys.SCROLL_INDEX, 0)
        stateManager.saveState(currentTabId, SearchStateKeys.SCROLL_OFFSET, 0)
        stateManager.saveState(currentTabId, SearchStateKeys.ANCHOR_ID, -1L)
        stateManager.saveState(currentTabId, SearchStateKeys.ANCHOR_INDEX, 0)

        // Replace current tab destination to Search (no new tab)
        tabsViewModel.replaceCurrentTabDestination(
            TabsDestination.Search(query, currentTabId)
        )
    }

    /**
     * Opens the selected reference (book/TOC) in the current tab.
     * - If a TOC entry is selected, tries to open at its first line.
     * - Otherwise opens the selected book at its beginning.
     */
    suspend fun openSelectedReferenceInCurrentTab() {
        val currentTabs = tabsViewModel.tabs.value
        val currentIndex = tabsViewModel.selectedTabIndex.value
        val currentTabId = currentTabs.getOrNull(currentIndex)?.destination?.tabId ?: return

        val selectedToc = _uiState.value.selectedScopeToc
        val selectedBook = _uiState.value.selectedScopeBook

        // Resolve book and optional line anchor
        val book = when {
            selectedBook != null -> selectedBook
            selectedToc != null -> runCatching { repository.getBook(selectedToc.bookId) }.getOrNull()
            else -> null
        } ?: return

        val anchorLineId: Long? = when (selectedToc) {
            null -> null
            else -> runCatching { repository.getLineIdsForTocEntry(selectedToc.id).firstOrNull() }.getOrNull()
        }

        // Pre-initialize minimal state so the BookContent shell does not flash Home
        stateManager.saveState(currentTabId, io.github.kdroidfilter.seforimapp.features.bookcontent.state.StateKeys.SELECTED_BOOK, book)
        anchorLineId?.let { stateManager.saveState(currentTabId, io.github.kdroidfilter.seforimapp.features.bookcontent.state.StateKeys.CONTENT_ANCHOR_ID, it) }

        // Replace destination in-place to open the book
        tabsViewModel.replaceCurrentTabDestination(
            TabsDestination.BookContent(bookId = book.id, tabId = currentTabId, lineId = anchorLineId)
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
        val bookTitle = runCatching { repository.getBook(entry.bookId)?.title }.getOrNull()
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
        fun extends(prev: String, next: String): Boolean {
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
