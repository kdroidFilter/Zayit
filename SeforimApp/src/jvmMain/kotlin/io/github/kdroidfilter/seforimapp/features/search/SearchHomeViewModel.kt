package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kdroidfilter.seforim.tabs.SearchScope
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.deeplink.parseZayitDeepLink
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.search.domain.reference.ReferenceParser
import io.github.kdroidfilter.seforimapp.features.search.domain.reference.ReferenceResolver
import io.github.kdroidfilter.seforimapp.features.search.domain.reference.RepositoryReferenceSource
import io.github.kdroidfilter.seforimapp.features.search.domain.reference.ResolvedReference
import io.github.kdroidfilter.seforimapp.framework.search.LuceneLookupSearchService
import io.github.kdroidfilter.seforimapp.framework.search.LuceneLookupSearchService.AuthorHit
import io.github.kdroidfilter.seforimapp.framework.search.MIN_BOOK_QUERY_LENGTH
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /**
     * Navigate to a destination resolved from a zayit:// deep link pasted into the search bar.
     * @param destination The parsed destination (book/line or search)
     */
    data class NavigateToDeepLink(
        val destination: TabsDestination,
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
    // Typed as one of its acronyms, whole
    val exactAcronym: Boolean = false,
)

@Immutable
data class TocSuggestionDto(
    val toc: TocEntry,
    val path: List<String>,
)

@Immutable
data class SearchHomeUiState(
    val globalExtended: Boolean = false,
    val suggestionsVisible: Boolean = false,
    val isReferenceLoading: Boolean = false,
    val categorySuggestions: List<CategorySuggestionDto> = emptyList(),
    val bookSuggestions: List<BookSuggestionDto> = emptyList(),
    /** Places the typed text points to (`חולין יב:`), listed above the book suggestions. */
    val jumpSuggestions: List<ResolvedReference> = emptyList(),
    /** Authors the typed text may name, listed before the books. */
    val authorSuggestions: List<AuthorHit> = emptyList(),
    /** The author picked: the books listed are then theirs. */
    val selectedScopeAuthor: AuthorHit? = null,
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
    private val appSettings: AppSettings,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchHomeUiState())
    val uiState: StateFlow<SearchHomeUiState> = _uiState.asStateFlow()

    // Navigation events channel - UI collects and handles navigation
    private val _navigationEvents = Channel<SearchHomeNavigationEvent>(Channel.BUFFERED)
    val navigationEvents = _navigationEvents.receiveAsFlow()

    private val referenceResolver = ReferenceResolver(RepositoryReferenceSource(repository))

    // The books of the picked author, filtered by what is typed next
    private var authorBooks: List<BookSuggestionDto> = emptyList()

    private val referenceQuery = MutableStateFlow("")
    private val tocQuery = MutableStateFlow("")

    private val minTocPrefixLen = 1 // minimum characters before triggering TOC predictive queries
    private val maxBookPredictive = 120 // tighter ceiling to avoid heavy allocations
    private val maxTocPredictive = 300 // TOC suggestions stay bounded

    // Lightweight, thread-safe LRU caches to avoid repeated DB hits when typing fast
    private class LruCache<K, V>(
        private val maxSize: Int,
    ) : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maxSize
    }

    private val categoryDepthCache = LruCache<Long, Int>(512)
    private val categoryDepthMutex = Mutex()
    private val categoryPathCache = LruCache<Long, List<String>>(512)
    private val categoryPathMutex = Mutex()
    private val tocPathCache = LruCache<Long, List<String>>(2048)
    private val tocPathMutex = Mutex()
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
        categoryDepthMutex.withLock { categoryDepthCache[catId]?.let { return it } }
        val depth =
            withContext(Dispatchers.IO) {
                runSuspendCatching { repository.getCategoryDepth(catId) }.getOrDefault(Int.MAX_VALUE)
            }
        categoryDepthMutex.withLock { categoryDepthCache[catId] = depth }
        return depth
    }

    private suspend fun buildCategoryPathTitlesCached(catId: Long): List<String> {
        categoryPathMutex.withLock { categoryPathCache[catId]?.let { return it } }
        val path = withContext(Dispatchers.IO) { runSuspendCatching { buildCategoryPathTitles(catId) }.getOrDefault(emptyList()) }
        categoryPathMutex.withLock { categoryPathCache[catId] = path }
        return path
    }

    private suspend fun buildTocPathTitlesCached(entry: TocEntry): List<String> {
        val key = entry.id
        tocPathMutex.withLock { tocPathCache[key]?.let { return it } }
        val path = withContext(Dispatchers.IO) { runSuspendCatching { buildTocPathTitles(entry) }.getOrDefault(emptyList()) }
        tocPathMutex.withLock { tocPathCache[key] = path }
        return path
    }

    init {
        // Observe changes in user profile and keep display name in sync
        viewModelScope.launch {
            appSettings.userFirstNameFlow
                .combine(appSettings.userLastNameFlow) { f, l -> "$f $l".trim() }
                .distinctUntilChanged()
                .collect { displayName ->
                    _uiState.value = _uiState.value.copy(userDisplayName = displayName)
                }
        }
        viewModelScope.launch {
            appSettings.userCommunityCodeFlow
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
                    if (_uiState.value.selectedScopeAuthor != null) {
                        _uiState.value =
                            _uiState.value.copy(
                                isReferenceLoading = false,
                                bookSuggestions = authorBooks.filter { it.titleContains(q) },
                                suggestionsVisible = true,
                            )
                    } else if (q.isBlank()) {
                        _uiState.value =
                            _uiState.value.copy(
                                isReferenceLoading = false,
                                categorySuggestions = emptyList(),
                                bookSuggestions = emptyList(),
                                jumpSuggestions = emptyList(),
                                authorSuggestions = emptyList(),
                                suggestionsVisible = false,
                            )
                    } else {
                        val startLoading = q.length >= MIN_BOOK_QUERY_LENGTH
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

                                    // Books: same acronym-aware suggestions as the book tree search
                                    val booksDeferred =
                                        async(Dispatchers.Default) {
                                            lookup.suggestBooks(q, limit = maxBookPredictive).map { hit ->
                                                val book = hit.toBook()
                                                val catPath = buildCategoryPathTitlesCached(book.categoryId)
                                                BookSuggestionDto(book, catPath + book.title, hit.exactAcronym)
                                            }
                                        }

                                    val authorsDeferred =
                                        async(Dispatchers.Default) { lookup.suggestAuthors(q, limit = MAX_AUTHOR_SUGGESTIONS) }

                                    val jumpsDeferred =
                                        async(Dispatchers.IO) {
                                            runSuspendCatching { referenceResolver.resolve(q) }.getOrDefault(emptyList())
                                        }

                                    Suggestions(catsDeferred.await(), booksDeferred.await(), jumpsDeferred.await(), authorsDeferred.await())
                                }
                            }

                        val (catSuggestions, bookSuggestions, jumpSuggestions, authorSuggestions) = result
                        _uiState.value =
                            _uiState.value.copy(
                                isReferenceLoading = false,
                                categorySuggestions = catSuggestions,
                                bookSuggestions = bookSuggestions,
                                jumpSuggestions = jumpSuggestions,
                                authorSuggestions = authorSuggestions,
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
                        q.length < minTocPrefixLen ->
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

    /** Lists the books of [author], to pick one of them next. */
    fun onPickAuthor(author: AuthorHit) {
        _uiState.value =
            _uiState.value.copy(
                selectedScopeAuthor = author,
                authorSuggestions = emptyList(),
                jumpSuggestions = emptyList(),
                bookSuggestions = emptyList(),
                isReferenceLoading = true,
                suggestionsVisible = true,
            )
        viewModelScope.launch {
            authorBooks =
                withContext(Dispatchers.IO) {
                    runSuspendCatching { repository.searchBooksByAuthor(author.name) }
                        .getOrDefault(emptyList())
                        // The query matches the name anywhere: keep this author's books only
                        .filter { book -> book.authors.any { it.id == author.id } }
                        .sortedWith(compareByDescending<Book> { it.isBaseBook }.thenBy { it.order })
                        .map { book -> BookSuggestionDto(book, buildCategoryPathTitlesCached(book.categoryId) + book.title) }
                }
            if (_uiState.value.selectedScopeAuthor?.id == author.id) {
                _uiState.value = _uiState.value.copy(bookSuggestions = authorBooks, isReferenceLoading = false)
            }
        }
    }

    fun onClearAuthor() {
        authorBooks = emptyList()
        _uiState.value = _uiState.value.copy(selectedScopeAuthor = null, bookSuggestions = emptyList(), suggestionsVisible = false)
    }

    fun onPickBook(book: Book) {
        authorBooks = emptyList()
        // Update synchronously first
        _uiState.value =
            _uiState.value.copy(
                selectedScopeAuthor = null,
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
                    val entries = runSuspendCatching { repository.getBookToc(book.id) }.getOrElse { emptyList() }
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
                    built += altTocSuggestions(book)
                    tocCache[book.id] = built
                    built
                }
            val preview =
                tocEntries
                    .mapNotNull { it.toc.text.takeIf { t -> t.isNotBlank() } }
                    .distinct()
                    .take(5)
                    .toList()
            val initialSuggestions = tocEntries.take(maxTocPredictive)
            _uiState.value =
                _uiState.value.copy(
                    tocPreviewHints = preview,
                    tocSuggestions = initialSuggestions,
                    tocSuggestionsVisible = initialSuggestions.isNotEmpty(),
                    isTocLoading = false,
                )
        }
    }

    // Entries of the book's alternative TOCs (parashot, chapter names...), after the main TOC. They
    // travel as TocEntry with the negated alt entry id, opened at their own line (see isAltTocEntry).
    private suspend fun altTocSuggestions(book: Book): List<TocSuggestionDto> =
        runSuspendCatching {
            repository.getAltTocStructuresForBook(book.id).flatMap { structure ->
                val entries = repository.getAltTocEntriesForStructure(structure.id)
                val byId = entries.associateBy { it.id }
                val label = altTocLabel(structure.key, structure.heTitle, book.title)
                entries.filter { it.text.isNotBlank() }.map { entry ->
                    val ancestors = generateSequence(entry.parentId?.let(byId::get)) { it.parentId?.let(byId::get) }
                    val path = listOf(label) + ancestors.map { it.text }.toList().asReversed() + entry.text
                    TocSuggestionDto(
                        TocEntry(
                            id = -entry.id,
                            bookId = book.id,
                            text = entry.text,
                            level = entry.level,
                            lineId = entry.lineId,
                        ),
                        path,
                    )
                }
            }
        }.getOrDefault(emptyList())

    private fun altTocLabel(
        key: String,
        heTitle: String?,
        bookTitle: String,
    ): String =
        heTitle?.takeIf { it.isNotBlank() && it != bookTitle }
            ?: ALT_TOC_LABELS[key]
            ?: key

    fun onPickToc(toc: TocEntry) {
        _uiState.value =
            _uiState.value.copy(
                selectedScopeToc = toc,
                tocSuggestionsVisible = false,
                isTocLoading = false,
            )
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
        // A zayit:// link pasted into the search bar opens the target instead of running a search.
        parseZayitDeepLink(query.trim())?.let { destination ->
            val resolvable =
                when (destination) {
                    is TabsDestination.BookContent ->
                        runSuspendCatching { repository.getBookCore(destination.bookId) }.getOrNull() != null
                    else -> true
                }
            if (resolvable) {
                _navigationEvents.send(SearchHomeNavigationEvent.NavigateToDeepLink(destination))
                return
            }
        }

        // Persist the selected scope as both the fetch scope and the view filter
        val selected = _uiState.value
        val scope =
            when {
                // An alternative-TOC entry has no main-TOC scope: search its whole book
                selected.selectedScopeToc != null && !selected.selectedScopeToc.isAltTocEntry() ->
                    SearchScope.Toc(bookId = selected.selectedScopeToc.bookId, tocId = selected.selectedScopeToc.id)
                selected.selectedScopeToc != null -> SearchScope.Book(selected.selectedScopeToc.bookId)
                selected.selectedScopeBook != null -> SearchScope.Book(selected.selectedScopeBook.id)
                selected.selectedScopeCategory != null -> SearchScope.Category(selected.selectedScopeCategory.id)
                else -> SearchScope.Global
            }

        persistedStore.update(currentTabId) { current ->
            val nextSearch =
                (current.search ?: SearchPersistedState()).withScope(scope).copy(
                    query = query,
                    globalExtended = selected.globalExtended,
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
                selectedToc != null -> runSuspendCatching { repository.getBookCore(selectedToc.bookId) }.getOrNull()
                else -> null
            } ?: return

        val anchorLineId: Long? =
            when {
                selectedToc == null -> null
                selectedToc.isAltTocEntry() -> selectedToc.lineId
                else -> runSuspendCatching { repository.getLineIdsForTocEntry(selectedToc.id).firstOrNull() }.getOrNull()
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

    /** Opens [book] at its start in the current tab. */
    suspend fun openBook(
        book: Book,
        currentTabId: String,
    ) {
        persistedStore.update(currentTabId) { current ->
            current.copy(bookContent = current.bookContent.copy(selectedBookId = book.id))
        }
        _uiState.value = _uiState.value.copy(suggestionsVisible = false)
        _navigationEvents.send(SearchHomeNavigationEvent.NavigateToBookContent(bookId = book.id, tabId = currentTabId, lineId = null))
    }

    /** Opens the page of [author] in the current tab. */
    suspend fun openAuthor(
        author: AuthorHit,
        currentTabId: String,
    ) {
        _uiState.value = _uiState.value.copy(suggestionsVisible = false)
        _navigationEvents.send(
            SearchHomeNavigationEvent.NavigateToDeepLink(TabsDestination.Author(tabId = currentTabId, authorId = author.id)),
        )
    }

    /** Opens the place a typed reference resolved to, in the current tab. */
    suspend fun openJump(
        jump: ResolvedReference,
        currentTabId: String,
    ) {
        persistedStore.update(currentTabId) { current ->
            current.copy(bookContent = current.bookContent.copy(selectedBookId = jump.book.id))
        }
        _uiState.value = _uiState.value.copy(suggestionsVisible = false)
        _navigationEvents.send(
            SearchHomeNavigationEvent.NavigateToBookContent(
                bookId = jump.book.id,
                tabId = currentTabId,
                lineId = jump.lineId,
            ),
        )
    }

    private suspend fun buildCategoryPathTitles(catId: Long): List<String> {
        val path = mutableListOf<String>()
        var currentId: Long? = catId
        val safety = 64
        var guard = 0
        while (currentId != null && guard++ < safety) {
            currentCoroutineContext().ensureActive()
            val c = repository.getCategory(currentId) ?: break
            path += c.title
            currentId = c.parentId
        }
        return path.asReversed()
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
        val bookTitle = runSuspendCatching { repository.getBookCore(entry.bookId)?.title }.getOrNull()
        val tocTitles = mutableListOf<String>()
        var current: TocEntry? = entry
        val safety = 128
        var guard = 0
        while (current != null && guard++ < safety) {
            currentCoroutineContext().ensureActive()
            tocTitles += current.text
            current = current.parentId?.let { pid -> runSuspendCatching { repository.getTocEntry(pid) }.getOrNull() }
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

// Alternative-TOC entries ride in TocSuggestionDto as TocEntry with a negated id
private fun TocEntry.isAltTocEntry(): Boolean = id < 0

// Names of Sefaria's alternative structures, whose heTitle is usually the book's own title
private val ALT_TOC_LABELS =
    mapOf(
        "Parasha" to "פרשיות",
        "Chapters" to "פרקים",
        "Chapter" to "פרקים",
        "Topic" to "נושאים",
        "Venice" to "דפוס ונציה",
        "Vilna" to "דפוס וילנא",
        "30 Day Cycle" to "מחזור חודשי",
        "Book" to "ספרים",
        "Gate" to "שערים",
        "Letter" to "אותיות",
        "Daf" to "דפים",
        "Contents" to "תוכן",
    )

private const val MAX_AUTHOR_SUGGESTIONS = 4

// Typed text against a title, both without nikud or quotes (שו"ת / שו״ת)
private fun BookSuggestionDto.titleContains(typed: String): Boolean =
    typed.isBlank() || ReferenceParser.normalizeName(book.title).contains(ReferenceParser.normalizeName(typed))

private data class Suggestions(
    val categories: List<CategorySuggestionDto>,
    val books: List<BookSuggestionDto>,
    val jumps: List<ResolvedReference>,
    val authors: List<AuthorHit>,
)
