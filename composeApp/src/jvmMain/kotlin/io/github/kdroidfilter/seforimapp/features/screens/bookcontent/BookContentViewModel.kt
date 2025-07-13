package io.github.kdroidfilter.seforimapp.features.screens.bookcontent

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabAwareViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabStateManager
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.SplitPaneState

class BookContentViewModel(
    savedStateHandle: SavedStateHandle,
    stateManager: TabStateManager,
    private val repository: SeforimRepository
) : TabAwareViewModel(
    tabId = savedStateHandle.get<String>("tabId") ?: "",
    stateManager = stateManager
) {

    // SplitPane states
    @OptIn(ExperimentalSplitPaneApi::class)
    private val _splitPaneState = MutableStateFlow(
        // Only try to get the position percentage, not the entire SplitPaneState
        SplitPaneState(
            initialPositionPercentage = getState<Float>("splitPanePosition") ?: 0.3f,
            moveEnabled = true,
        )
    )

    @OptIn(ExperimentalSplitPaneApi::class)
    val splitPaneState = _splitPaneState.asStateFlow()

    @OptIn(ExperimentalSplitPaneApi::class)
    private val _tocSplitPaneState = MutableStateFlow(
        // Only try to get the position percentage, not the entire SplitPaneState
        SplitPaneState(
            initialPositionPercentage = getState<Float>("tocSplitPanePosition") ?: 0.3f,
            moveEnabled = true,
        )
    )

    @OptIn(ExperimentalSplitPaneApi::class)
    val tocSplitPaneState = _tocSplitPaneState.asStateFlow()

    // Content split pane state for book content and commentaries
    @OptIn(ExperimentalSplitPaneApi::class)
    private val _contentSplitPaneState = MutableStateFlow(
        // Only try to get the position percentage, not the entire SplitPaneState
        SplitPaneState(
            initialPositionPercentage = getState<Float>("contentSplitPanePosition") ?: 0.7f,
            moveEnabled = true,
        )
    )

    @OptIn(ExperimentalSplitPaneApi::class)
    val contentSplitPaneState = _contentSplitPaneState.asStateFlow()

    // Flag to show/hide commentaries
    private val _showCommentaries = MutableStateFlow(
        getState<Boolean>("showCommentaries") ?: false
    )
    val showCommentaries = _showCommentaries.asStateFlow()

    // Flag to show/hide book tree
    private val _showBookTree = MutableStateFlow(
        getState<Boolean>("showBookTree") ?: true
    )
    val showBookTree = _showBookTree.asStateFlow()

    // Search text state
    private val _searchText = MutableStateFlow(
        getState<String>("searchText") ?: ""
    )
    val searchText = _searchText.asStateFlow()

    // Scroll position state
    private val _paragraphScrollPosition = MutableStateFlow(
        getState<Int>("paragraphScrollPosition") ?: 0
    )
    val paragraphScrollPosition = _paragraphScrollPosition.asStateFlow()

    private val _chapterScrollPosition = MutableStateFlow(
        getState<Int>("chapterScrollPosition") ?: 0
    )
    val chapterScrollPosition = _chapterScrollPosition.asStateFlow()

    // Selected chapter state
    private val _selectedChapter = MutableStateFlow(
        getState<Int>("selectedChapter") ?: 0
    )
    val selectedChapter = _selectedChapter.asStateFlow()

    // Database-related state
    private val _rootCategories = MutableStateFlow<List<Category>>(emptyList())
    val rootCategories = _rootCategories.asStateFlow()

    private val _expandedCategories = MutableStateFlow<Set<Long>>(emptySet())
    val expandedCategories = _expandedCategories.asStateFlow()

    private val _categoryChildren = MutableStateFlow<Map<Long, List<Category>>>(emptyMap())
    val categoryChildren = _categoryChildren.asStateFlow()

    private val _booksInCategory = MutableStateFlow<Set<Book>>(emptySet())
    val booksInCategory = _booksInCategory.asStateFlow()

    private val _selectedCategory = MutableStateFlow<Category?>(null)
    val selectedCategory = _selectedCategory.asStateFlow()

    private val _selectedBook = MutableStateFlow<Book?>(null)
    val selectedBook = _selectedBook.asStateFlow()

    private val _bookLines = MutableStateFlow<List<Line>>(emptyList())
    val bookLines = _bookLines.asStateFlow()

    private val _selectedLine = MutableStateFlow<Line?>(null)
    val selectedLine = _selectedLine.asStateFlow()

    private val _tocEntries = MutableStateFlow<List<TocEntry>>(emptyList())
    val tocEntries = _tocEntries.asStateFlow()

    private val _expandedTocEntries = MutableStateFlow<Set<Long>>(emptySet())
    val expandedTocEntries = _expandedTocEntries.asStateFlow()

    private val _tocChildren = MutableStateFlow<Map<Long, List<TocEntry>>>(emptyMap())
    val tocChildren = _tocChildren.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    // Commentaries for the selected line
    private val _commentaries = MutableStateFlow<List<CommentaryWithText>>(emptyList())
    val commentaries = _commentaries.asStateFlow()

    init {
        // Load root categories on initialization
        loadRootCategories()
    }

    private fun loadRootCategories() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                _rootCategories.value = repository.getRootCategories()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun expandCategory(category: Category) {
        if (_expandedCategories.value.contains(category.id)) {
            // Collapse category
            _expandedCategories.value = _expandedCategories.value - category.id
        } else {
            // Expand category
            _expandedCategories.value = _expandedCategories.value + category.id

            // Load category children if not already loaded
            if (!_categoryChildren.value.containsKey(category.id)) {
                _isLoading.value = true
                viewModelScope.launch {
                    try {
                        val children = repository.getCategoryChildren(category.id)
                        if (children.isNotEmpty()) {
                            _categoryChildren.value = _categoryChildren.value + (category.id to children)
                        }

                        // Load books in this category
                        val books = repository.getBooksByCategory(category.id)
                        if (books.isNotEmpty()) {
                            _booksInCategory.value = _booksInCategory.value + books
                        }
                    } finally {
                        _isLoading.value = false
                    }
                }
            }
        }
    }

    fun selectCategory(category: Category) {
        _selectedCategory.value = category
        expandCategory(category)
    }

    fun loadBook(book: Book) {
        _selectedBook.value = book
        _isLoading.value = true

        viewModelScope.launch {
            try {
                // Load book lines
                val lines = repository.getLines(book.id, 0, 30)
                _bookLines.value = lines

                // Only load root TOC entries initially for better performance
                val rootToc = repository.getBookRootToc(book.id)
                _tocEntries.value = rootToc

                // Store root entries with the special key (-1L)
                _tocChildren.value = mapOf(-1L to rootToc)

                // Auto-expand first TOC entry if exists
                if (rootToc.isNotEmpty()) {
                    val firstEntry = rootToc.first()
                    _expandedTocEntries.value = setOf(firstEntry.id)

                    // Load children of the first entry
                    val children = repository.getTocChildren(firstEntry.id)
                    if (children.isNotEmpty()) {
                        _tocChildren.value = _tocChildren.value + (firstEntry.id to children)
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun expandTocEntry(tocEntry: TocEntry) {
        if (_expandedTocEntries.value.contains(tocEntry.id)) {
            // Collapse TOC entry and its descendants
            val descendants = getAllDescendantIds(tocEntry.id, _tocChildren.value)
            _expandedTocEntries.value = _expandedTocEntries.value - tocEntry.id - descendants
        } else {
            // Expand TOC entry
            _expandedTocEntries.value = _expandedTocEntries.value + tocEntry.id

            // Load TOC children if not already loaded
            if (!_tocChildren.value.containsKey(tocEntry.id)) {
                _isLoading.value = true
                viewModelScope.launch {
                    try {
                        // Load only the direct children for this entry
                        val children = repository.getTocChildren(tocEntry.id)

                        // Update the children map
                        if (children.isNotEmpty()) {
                            _tocChildren.value = _tocChildren.value + (tocEntry.id to children)
                        } else {
                            // If there are no children, add an empty list to mark that we've checked
                            _tocChildren.value = _tocChildren.value + (tocEntry.id to emptyList())
                        }
                    } finally {
                        _isLoading.value = false
                    }
                }
            }
        }
    }

    private fun getAllDescendantIds(entryId: Long, childrenMap: Map<Long, List<TocEntry>>): Set<Long> {
        val result = mutableSetOf<Long>()
        childrenMap[entryId]?.forEach { child ->
            result.add(child.id)
            result.addAll(getAllDescendantIds(child.id, childrenMap))
        }
        return result
    }

    fun selectLine(line: Line) {
        _selectedLine.value = line

        // Fetch commentaries for the selected line
        fetchCommentariesForLine(line)
    }

    /**
     * Fetches commentaries for a selected line.
     */
    private fun fetchCommentariesForLine(line: Line) {
        viewModelScope.launch {
            try {
                _commentaries.value = repository.getCommentariesForLines(listOf(line.id))
            } catch (e: Exception) {
                // Handle any errors
                println("Error fetching commentaries: ${e.message}")
                _commentaries.value = emptyList()
            }
        }
    }

    /**
     * Loads a line by its ID and selects it.
     * If the line is not in the current book, this method does nothing.
     * If the line is in the current book but not in the current lines list,
     * it loads a range of lines around it and then selects it.
     */
    fun loadAndSelectLine(lineId: Long) {
        // Make sure we have a book selected
        val currentBook = _selectedBook.value ?: return

        viewModelScope.launch {
            try {
                // Load the line from the repository
                val line = repository.getLine(lineId)

                // Make sure the line belongs to the current book
                if (line != null && line.bookId == currentBook.id) {
                    // Check if the line is already in the current lines list
                    if (!_bookLines.value.any { it.id == lineId }) {
                        // If not, load a range of lines around it
                        val startIndex = maxOf(0, line.lineIndex - 25)
                        val endIndex = line.lineIndex + 25
                        _bookLines.value = repository.getLines(currentBook.id, startIndex, endIndex)
                    }

                    // Select the line
                    _selectedLine.value = line
                }
            } catch (e: Exception) {
                // Handle any errors
                println("Error loading line: ${e.message}")
            }
        }
    }

    fun updateSearchText(text: String) {
        _searchText.value = text
        saveState("searchText", text)
    }

    fun updateParagraphScrollPosition(position: Int) {
        _paragraphScrollPosition.value = position
        saveState("paragraphScrollPosition", position)
    }

    fun updateChapterScrollPosition(position: Int) {
        _chapterScrollPosition.value = position
        saveState("chapterScrollPosition", position)
    }

    fun selectChapter(chapter: Int) {
        _selectedChapter.value = chapter
        saveState("selectedChapter", chapter)
    }

    /**
     * Toggles the display of commentaries.
     */
    fun toggleCommentaries() {
        _showCommentaries.value = !_showCommentaries.value
        saveState("showCommentaries", _showCommentaries.value)
    }

    /**
     * Toggles the display of the book tree.
     */
    fun toggleBookTree() {
        _showBookTree.value = !_showBookTree.value
        saveState("showBookTree", _showBookTree.value)
    }

    fun onEvent(events: BookContentEvents) {
        when (events) {
            is BookContentEvents.OnUpdateParagraphScrollPosition -> updateParagraphScrollPosition(events.position)
            is BookContentEvents.OnChapterSelected -> selectChapter(events.index)
            is BookContentEvents.OnSearchTextChange -> updateSearchText(events.text)
            BookContentEvents.SaveAllStates -> saveAllStates()
            is BookContentEvents.OnUpdateChapterScrollPosition -> updateChapterScrollPosition(events.position)
            is BookContentEvents.OnCategorySelected -> selectCategory(events.category)
            is BookContentEvents.OnBookSelected -> loadBook(events.book)
            is BookContentEvents.OnTocEntryExpanded -> expandTocEntry(events.tocEntry)
            is BookContentEvents.OnLineSelected -> selectLine(events.line)
            is BookContentEvents.OnLoadAndSelectLine -> loadAndSelectLine(events.lineId)
            BookContentEvents.OnToggleCommentaries -> toggleCommentaries()
            BookContentEvents.OnToggleBookTree -> toggleBookTree()
        }
    }

    @OptIn(ExperimentalSplitPaneApi::class)
    fun saveAllStates() {
        // Only save position percentages, not the entire SplitPaneState objects
        // This is more efficient and reduces lag during resize operations
        saveState("splitPanePosition", splitPaneState.value.positionPercentage)
        saveState("tocSplitPanePosition", tocSplitPaneState.value.positionPercentage)
        saveState("contentSplitPanePosition", contentSplitPaneState.value.positionPercentage)

        // Save other state values
        saveState("searchText", searchText.value)
        saveState("scrollPosition", paragraphScrollPosition.value)
        saveState("selectedChapter", selectedChapter.value)
        saveState("showCommentaries", showCommentaries.value)
        saveState("showBookTree", showBookTree.value)
    }

    /**
     * Public method to get a state value for a specific key.
     * This is needed because the getState method in TabAwareViewModel is protected.
     */
    fun <T> getStateValue(key: String): T? {
        return getState(key)
    }

    /**
     * Public method to save a state value for a specific key.
     * This is needed because the saveState method in TabAwareViewModel is protected.
     */
    fun saveStateValue(key: String, value: Any) {
        saveState(key, value)
    }
}
