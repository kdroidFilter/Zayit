package io.github.kdroidfilter.seforimapp.features.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabStateManager
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get

data class CategorySuggestionDto(val category: Category, val path: List<String>)
data class BookSuggestionDto(val book: Book, val path: List<String>)

data class SearchHomeUiState(
    val selectedFilter: SearchFilter = SearchFilter.TEXT,
    val selectedLevelIndex: Int = 2,
    val suggestionsVisible: Boolean = false,
    val categorySuggestions: List<CategorySuggestionDto> = emptyList(),
    val bookSuggestions: List<BookSuggestionDto> = emptyList(),
    val selectedScopeCategory: Category? = null,
    val selectedScopeBook: Book? = null,
    val userDisplayName: String = ""
)

class SearchHomeViewModel(
    private val tabsViewModel: TabsViewModel,
    private val stateManager: TabStateManager,
    private val repository: SeforimRepository,
    private val settings: Settings
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchHomeUiState())
    val uiState: StateFlow<SearchHomeUiState> = _uiState.asStateFlow()

    private val referenceQuery = MutableStateFlow("")

    private val NEAR_LEVELS = listOf(1, 3, 5, 10, 20)

    init {
        // Build display name from injected Settings
        runCatching {
            val firstName: String = settings["user_first_name", ""]
            val lastName: String = settings["user_last_name", ""]
            val displayName = "$firstName $lastName".trim()
            _uiState.value = _uiState.value.copy(userDisplayName = displayName)
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
                            .findCategoriesByTitleLike(pattern, limit = 12)
                            .filter { it.title.isNotBlank() }
                            .distinctBy { it.id }
                        val catSuggestions = catsRaw.map { cat ->
                            val path = buildCategoryPathTitles(cat.id)
                            CategorySuggestionDto(cat, path.ifEmpty { listOf(cat.title) })
                        }
                        // Books via FTS5 on titles + acronyms
                        val tokens = qNorm.split("\\s+".toRegex()).filter { it.isNotBlank() }
                        val ftsQuery = if (tokens.isEmpty()) "" else tokens.joinToString(" ") { it + "*" }
                        val ftsBooks = if (ftsQuery.isNotBlank()) repository.searchBooksByTitleFts(ftsQuery, limit = 50) else emptyList()

                        val bookItems = ftsBooks.map { book ->
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
    }

    fun onReferenceQueryChanged(query: String) {
        referenceQuery.value = query
    }

    fun onPickCategory(category: Category) {
        _uiState.value = _uiState.value.copy(
            selectedScopeCategory = category,
            selectedScopeBook = null,
            suggestionsVisible = false
        )
    }

    fun onPickBook(book: Book) {
        _uiState.value = _uiState.value.copy(
            selectedScopeCategory = null,
            selectedScopeBook = book,
            suggestionsVisible = false
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

        // Apply selected scope only
        _uiState.value.selectedScopeCategory?.let { cat ->
            stateManager.saveState(currentTabId, SearchStateKeys.FILTER_CATEGORY_ID, cat.id)
        }
        _uiState.value.selectedScopeBook?.let { book ->
            stateManager.saveState(currentTabId, SearchStateKeys.FILTER_BOOK_ID, book.id)
        }

        // Persist search params for this tab to restore state
        stateManager.saveState(currentTabId, SearchStateKeys.QUERY, query)
        stateManager.saveState(currentTabId, SearchStateKeys.NEAR, NEAR_LEVELS[_uiState.value.selectedLevelIndex])

        // Replace current tab destination to Search (no new tab)
        tabsViewModel.replaceCurrentTabDestination(
            TabsDestination.Search(query, currentTabId)
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
}
