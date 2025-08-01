package io.github.kdroidfilter.seforimapp.features.screens.bookcontent

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.kdroidfilter.seforimlibrary.core.models.*
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabAwareViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabStateManager
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.models.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.SplitPaneState

@OptIn(ExperimentalSplitPaneApi::class)
class BookContentViewModel(
    savedStateHandle: SavedStateHandle,
    stateManager: TabStateManager,
    private val repository: SeforimRepository
) : TabAwareViewModel(
    tabId = savedStateHandle.get<String>(KEY_TAB_ID) ?: "",
    stateManager = stateManager
) {

    companion object {
        // State keys
        const val KEY_TAB_ID = "tabId"
        const val KEY_BOOK_ID = "bookId"
        const val KEY_LINE_ID = "lineId"
        const val KEY_SELECTED_BOOK = "selectedBook"
        const val KEY_SEARCH_TEXT = "searchText"
        const val KEY_SHOW_BOOK_TREE = "showBookTree"
        const val KEY_SPLIT_PANE_POSITION = "splitPanePosition"
        const val KEY_TOC_SPLIT_PANE_POSITION = "tocSplitPanePosition"
        const val KEY_CONTENT_SPLIT_PANE_POSITION = "contentSplitPanePosition"
        const val KEY_SHOW_TOC = "showToc"
        const val KEY_SHOW_COMMENTARIES = "showCommentaries"
        const val KEY_PARAGRAPH_SCROLL_POSITION = "paragraphScrollPosition"
        const val KEY_CHAPTER_SCROLL_POSITION = "chapterScrollPosition"
        const val KEY_SELECTED_CHAPTER = "selectedChapter"

        // Additional state keys
        const val KEY_SELECTED_CATEGORY = "selectedCategory"
        const val KEY_EXPANDED_CATEGORIES = "expandedCategories"
        const val KEY_CATEGORY_CHILDREN = "categoryChildren"
        const val KEY_BOOKS_IN_CATEGORY = "booksInCategory"
        const val KEY_EXPANDED_TOC_ENTRIES = "expandedTocEntries"
        const val KEY_TOC_CHILDREN = "tocChildren"
        const val KEY_SELECTED_LINE = "selectedLine"
        const val KEY_PREVIOUS_MAIN_SPLIT_POSITION = "previousMainSplitPosition"
        const val KEY_PREVIOUS_TOC_SPLIT_POSITION = "previousTocSplitPosition"
        const val KEY_PREVIOUS_CONTENT_SPLIT_POSITION = "previousContentSplitPosition"
        const val KEY_TOC_SCROLL_INDEX = "tocScrollIndex"
        const val KEY_TOC_SCROLL_OFFSET = "tocScrollOffset"
    }
    // Initialize state flows first
    private val _isLoading = MutableStateFlow(false)
    private val _rootCategories = MutableStateFlow<List<Category>>(emptyList())
    private val _expandedCategories = MutableStateFlow<Set<Long>>(getState(KEY_EXPANDED_CATEGORIES) ?: emptySet())
    private val _categoryChildren = MutableStateFlow<Map<Long, List<Category>>>(getState(KEY_CATEGORY_CHILDREN) ?: emptyMap())
    private val _booksInCategory = MutableStateFlow<Set<Book>>(getState(KEY_BOOKS_IN_CATEGORY) ?: emptySet())
    private val _selectedCategory = MutableStateFlow<Category?>(getState(KEY_SELECTED_CATEGORY))
    private val _selectedBook = MutableStateFlow<Book?>(getState(KEY_SELECTED_BOOK))
    private val _searchText = MutableStateFlow(getState<String>(KEY_SEARCH_TEXT) ?: "")
    private val _showBookTree = MutableStateFlow(getState<Boolean>(KEY_SHOW_BOOK_TREE) ?: true)

    @OptIn(ExperimentalSplitPaneApi::class)
    private val _splitPaneState = MutableStateFlow(
        SplitPaneState(
            initialPositionPercentage = getState<Float>(KEY_SPLIT_PANE_POSITION) ?: 0.05f,
            moveEnabled = true
        )
    )

    @OptIn(ExperimentalSplitPaneApi::class)
    private val _tocSplitPaneState = MutableStateFlow(
        SplitPaneState(
            initialPositionPercentage = getState<Float>(KEY_TOC_SPLIT_PANE_POSITION) ?: 0.0025f,
            moveEnabled = true
        )
    )

    @OptIn(ExperimentalSplitPaneApi::class)
    private val _contentSplitPaneState = MutableStateFlow(
        SplitPaneState(
            initialPositionPercentage = getState<Float>(KEY_CONTENT_SPLIT_PANE_POSITION) ?: 0.9f,
            moveEnabled = true
        )
    )

    private val _bookLines = MutableStateFlow<List<Line>>(emptyList())
    private val _selectedLine = MutableStateFlow<Line?>(getState(KEY_SELECTED_LINE))
    private val _tocEntries = MutableStateFlow<List<TocEntry>>(emptyList())
    private val _expandedTocEntries = MutableStateFlow<Set<Long>>(getState(KEY_EXPANDED_TOC_ENTRIES) ?: emptySet())
    private val _tocChildren = MutableStateFlow<Map<Long, List<TocEntry>>>(getState(KEY_TOC_CHILDREN) ?: emptyMap())
    private val _showToc = MutableStateFlow(getState<Boolean>(KEY_SHOW_TOC) ?: true)
    private val _tocScrollIndex = MutableStateFlow(getState<Int>(KEY_TOC_SCROLL_INDEX) ?: 0)
    private val _tocScrollOffset = MutableStateFlow(getState<Int>(KEY_TOC_SCROLL_OFFSET) ?: 0)

    private val _commentaries = MutableStateFlow<List<CommentaryWithText>>(emptyList())
    private val _showCommentaries = MutableStateFlow(getState<Boolean>(KEY_SHOW_COMMENTARIES) ?: false)
    private val _paragraphScrollPosition = MutableStateFlow(getState<Int>(KEY_PARAGRAPH_SCROLL_POSITION) ?: 0)
    private val _chapterScrollPosition = MutableStateFlow(getState<Int>(KEY_CHAPTER_SCROLL_POSITION) ?: 0)
    private val _selectedChapter = MutableStateFlow(getState<Int>(KEY_SELECTED_CHAPTER) ?: 0)

    // Create UI state using combine for better performance
    @OptIn(ExperimentalSplitPaneApi::class)
    val uiState: StateFlow<BookContentUiState> = combine(
        navigationState(),
        tocState(),
        contentState(),
        layoutState(),
        _isLoading
    ) { navigation, toc, content, layout, isLoading ->
        BookContentUiState(
            navigation = navigation,
            toc = toc,
            content = content,
            layout = layout,
            isLoading = isLoading
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        BookContentUiState(layout = createInitialLayoutState())
    )

    init {
        loadRootCategories()

        // First check if we have a restored book from saved state
        val restoredBook = _selectedBook.value
        if (restoredBook != null) {
            // Load the associated data for the restored book
            viewModelScope.launch {
                _isLoading.value = true
                try {
                    // Load the book data
                    loadBookData(restoredBook)
                } finally {
                    _isLoading.value = false
                }
            }
        } else {
            // If no restored book, check if we have a bookId in the savedStateHandle
            savedStateHandle.get<Long>(KEY_BOOK_ID)?.let { bookId ->
                // Load the book
                viewModelScope.launch {
                    _isLoading.value = true
                    try {
                        // Get the book from the repository
                        repository.getBook(bookId)?.let { book ->
                            // Load the book
                            loadBook(book)

                            // Check if we have a lineId in the savedStateHandle
                            savedStateHandle.get<Long>(KEY_LINE_ID)?.let { lineId ->
                                // Load and select the line
                                loadAndSelectLine(lineId)
                            }
                        }
                    } finally {
                        _isLoading.value = false
                    }
                }
            }
        }
    }

    fun onEvent(event: BookContentEvent) {
        when (event) {
            // Navigation events
            is BookContentEvent.SearchTextChanged -> updateSearchText(event.text)
            is BookContentEvent.CategorySelected -> selectCategory(event.category)
            is BookContentEvent.BookSelected -> loadBook(event.book)
            BookContentEvent.ToggleBookTree -> toggleBookTree()

            // TOC events
            is BookContentEvent.TocEntryExpanded -> expandTocEntry(event.entry)
            BookContentEvent.ToggleToc -> toggleToc()
            is BookContentEvent.TocScrolled -> updateTocScrollPosition(event.index, event.offset)

            // Content events
            is BookContentEvent.LineSelected -> selectLine(event.line)
            is BookContentEvent.LoadAndSelectLine -> loadAndSelectLine(event.lineId)
            BookContentEvent.ToggleCommentaries -> toggleCommentaries()

            // Scroll events
            is BookContentEvent.ParagraphScrolled -> updateParagraphScrollPosition(event.position)
            is BookContentEvent.ChapterScrolled -> updateChapterScrollPosition(event.position)
            is BookContentEvent.ChapterSelected -> selectChapter(event.index)

            // State management
            BookContentEvent.SaveState -> saveAllStates()
        }
    }

    // Create separate state flows for each UI section
    private fun navigationState(): StateFlow<NavigationUiState> {
        return _rootCategories.combine(_expandedCategories) { rootCategories, expanded ->
            Pair(rootCategories, expanded)
        }.combine(_categoryChildren) { (rootCategories, expanded), children ->
            Triple(rootCategories, expanded, children)
        }.combine(_booksInCategory) { (rootCategories, expanded, children), books ->
            NavigationData(rootCategories, expanded, children, books)
        }.combine(_selectedCategory) { data, category ->
            data.copy(selectedCategory = category)
        }.combine(_selectedBook) { data, book ->
            data.copy(selectedBook = book)
        }.combine(_searchText) { data, search ->
            data.copy(searchText = search)
        }.combine(_showBookTree) { data, visible ->
            NavigationUiState(
                rootCategories = data.rootCategories,
                expandedCategories = data.expandedCategories,
                categoryChildren = data.categoryChildren,
                booksInCategory = data.booksInCategory,
                selectedCategory = data.selectedCategory,
                selectedBook = data.selectedBook,
                searchText = data.searchText,
                isVisible = visible
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, NavigationUiState())
    }

    private data class NavigationData(
        val rootCategories: List<Category>,
        val expandedCategories: Set<Long>,
        val categoryChildren: Map<Long, List<Category>>,
        val booksInCategory: Set<Book>,
        val selectedCategory: Category? = null,
        val selectedBook: Book? = null,
        val searchText: String = ""
    )

    private fun tocState(): StateFlow<TocUiState> =
        combine(
            _tocEntries,
            _expandedTocEntries,
            _tocChildren,
            _showToc,
            _tocScrollIndex,
            _tocScrollOffset
        ) { array ->
            @Suppress("UNCHECKED_CAST")
            val entries = array[0] as List<TocEntry>
            @Suppress("UNCHECKED_CAST")
            val expanded = array[1] as Set<Long>
            @Suppress("UNCHECKED_CAST")
            val children = array[2] as Map<Long, List<TocEntry>>
            val visible = array[3] as Boolean
            val scrollIndex = array[4] as Int
            val scrollOffset = array[5] as Int
            TocUiState(
                entries = entries,
                expandedEntries = expanded,
                children = children,
                isVisible = visible,
                scrollIndex = scrollIndex,
                scrollOffset = scrollOffset
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, TocUiState())
    private fun contentState(): StateFlow<ContentUiState> {
        return _bookLines.combine(_selectedLine) { lines, line ->
            Pair(lines, line)
        }.combine(_commentaries) { (lines, line), commentaries ->
            Triple(lines, line, commentaries)
        }.combine(_showCommentaries) { (lines, line, commentaries), showComm ->
            ContentData(lines, line, commentaries, showComm)
        }.combine(_paragraphScrollPosition) { data, pScroll ->
            data.copy(paragraphScrollPosition = pScroll)
        }.combine(_chapterScrollPosition) { data, cScroll ->
            data.copy(chapterScrollPosition = cScroll)
        }.combine(_selectedChapter) { data, chapter ->
            ContentUiState(
                lines = data.lines,
                selectedLine = data.selectedLine,
                commentaries = data.commentaries,
                showCommentaries = data.showCommentaries,
                paragraphScrollPosition = data.paragraphScrollPosition,
                chapterScrollPosition = data.chapterScrollPosition,
                selectedChapter = chapter
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ContentUiState())
    }

    private data class ContentData(
        val lines: List<Line>,
        val selectedLine: Line?,
        val commentaries: List<CommentaryWithText>,
        val showCommentaries: Boolean,
        val paragraphScrollPosition: Int = 0,
        val chapterScrollPosition: Int = 0
    )

    @OptIn(ExperimentalSplitPaneApi::class)
    private fun layoutState(): StateFlow<LayoutUiState> {
        return _splitPaneState.combine(_tocSplitPaneState) { main, toc ->
            Pair(main, toc)
        }.combine(_contentSplitPaneState) { (main, toc), content ->
            LayoutUiState(
                mainSplitState = main,
                tocSplitState = toc,
                contentSplitState = content
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, createInitialLayoutState())
    }

    @OptIn(ExperimentalSplitPaneApi::class)
    private fun createInitialLayoutState(): LayoutUiState = LayoutUiState(
        mainSplitState = SplitPaneState(
            initialPositionPercentage = getState<Float>(KEY_SPLIT_PANE_POSITION) ?: 0.3f,
            moveEnabled = true
        ),
        tocSplitState = SplitPaneState(
            initialPositionPercentage = getState<Float>(KEY_TOC_SPLIT_PANE_POSITION) ?: 0.3f,
            moveEnabled = true
        ),
        contentSplitState = SplitPaneState(
            initialPositionPercentage = getState<Float>(KEY_CONTENT_SPLIT_PANE_POSITION) ?: 0.7f,
            moveEnabled = true
        )
    )


    // Implementation methods
    private fun loadRootCategories() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Load root categories
                val rootCategories = repository.getRootCategories()
                _rootCategories.value = rootCategories

                // Get the current expanded categories
                val expandedCategories = _expandedCategories.value

                // If there are expanded categories, load their books
                if (expandedCategories.isNotEmpty()) {
                    // Load books for all expanded categories
                    val booksToLoad = mutableSetOf<Book>()

                    // Process categories to load books
                    expandedCategories.forEach { categoryId ->
                        try {
                            val books = repository.getBooksByCategory(categoryId)
                            if (books.isNotEmpty()) {
                                booksToLoad.addAll(books)
                            }
                        } catch (e: Exception) {
                            // Handle error
                        }
                    }

                    // Update books in category
                    if (booksToLoad.isNotEmpty()) {
                        _booksInCategory.value = _booksInCategory.value + booksToLoad
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun expandCategory(category: Category) {
        val isExpanded = _expandedCategories.value.contains(category.id)

        if (isExpanded) {
            _expandedCategories.value -= category.id
        } else {
            _expandedCategories.value += category.id

            if (!_categoryChildren.value.containsKey(category.id)) {
                viewModelScope.launch {
                    _isLoading.value = true
                    try {
                        val children = repository.getCategoryChildren(category.id)
                        if (children.isNotEmpty()) {
                            val updatedMap = _categoryChildren.value + (category.id to children)
                            _categoryChildren.value = updatedMap

                            // Save category children map
                            saveState(KEY_CATEGORY_CHILDREN, updatedMap)
                        }

                        val books = repository.getBooksByCategory(category.id)
                        if (books.isNotEmpty()) {
                            val updatedBooks = _booksInCategory.value + books
                            _booksInCategory.value = updatedBooks

                            // Save books in category
                            saveState(KEY_BOOKS_IN_CATEGORY, updatedBooks)
                        }
                    } finally {
                        _isLoading.value = false
                    }
                }
            }
        }

        // Save expanded categories state
        saveState(KEY_EXPANDED_CATEGORIES, _expandedCategories.value)
    }

    private fun selectCategory(category: Category) {
        _selectedCategory.value = category
        expandCategory(category)

        // Save selected category
        saveState(KEY_SELECTED_CATEGORY, category)
    }

    private fun loadBook(book: Book) {
        _selectedBook.value = book
        loadBookData(book)
    }

    private fun loadBookData(book: Book) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Load book lines
                _bookLines.value = repository.getLines(book.id, 0, 30)

                // Load root TOC entries
                val rootToc = repository.getBookRootToc(book.id)
                _tocEntries.value = rootToc
                _tocChildren.value = mapOf(-1L to rootToc)

                // Auto-expand first TOC entry
                rootToc.firstOrNull()?.let { firstEntry ->
                    _expandedTocEntries.value = setOf(firstEntry.id)

                    val children = repository.getTocChildren(firstEntry.id)
                    if (children.isNotEmpty()) {
                        _tocChildren.value += (firstEntry.id to children)
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun expandTocEntry(tocEntry: TocEntry) {
        val isExpanded = _expandedTocEntries.value.contains(tocEntry.id)

        if (isExpanded) {
            val descendants = getAllDescendantIds(tocEntry.id, _tocChildren.value)
            _expandedTocEntries.value = _expandedTocEntries.value - tocEntry.id - descendants
        } else {
            _expandedTocEntries.value += tocEntry.id

            if (!_tocChildren.value.containsKey(tocEntry.id)) {
                viewModelScope.launch {
                    _isLoading.value = true
                    try {
                        val children = repository.getTocChildren(tocEntry.id)
                        val updatedChildren = _tocChildren.value + (tocEntry.id to children.ifEmpty { emptyList() })
                        _tocChildren.value = updatedChildren

                        // Save TOC children map
                        saveState(KEY_TOC_CHILDREN, updatedChildren)
                    } finally {
                        _isLoading.value = false
                    }
                }
            }
        }

        // Save expanded TOC entries state
        saveState(KEY_EXPANDED_TOC_ENTRIES, _expandedTocEntries.value)
    }

    private fun getAllDescendantIds(entryId: Long, childrenMap: Map<Long, List<TocEntry>>): Set<Long> =
        buildSet {
            childrenMap[entryId]?.forEach { child ->
                add(child.id)
                addAll(getAllDescendantIds(child.id, childrenMap))
            }
        }

    private fun selectLine(line: Line) {
        _selectedLine.value = line
        fetchCommentariesForLine(line)

        // Save selected line
        saveState(KEY_SELECTED_LINE, line)
    }

    private fun fetchCommentariesForLine(line: Line) {
        viewModelScope.launch {
            try {
                _commentaries.value = repository.getCommentariesForLines(listOf(line.id))
            } catch (e: Exception) {
                _commentaries.value = emptyList()
            }
        }
    }

    private fun loadAndSelectLine(lineId: Long) {
        val currentBook = _selectedBook.value ?: return

        viewModelScope.launch {
            try {
                repository.getLine(lineId)?.let { line ->
                    if (line.bookId == currentBook.id) {
                        if (_bookLines.value.none { it.id == lineId }) {
                            val startIndex = maxOf(0, line.lineIndex - 25)
                            val endIndex = line.lineIndex + 25
                            _bookLines.value = repository.getLines(currentBook.id, startIndex, endIndex)
                        }
                        _selectedLine.value = line
                    }
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    private fun updateSearchText(text: String) {
        _searchText.value = text
        saveState(KEY_SEARCH_TEXT, text)
    }

    private fun updateParagraphScrollPosition(position: Int) {
        _paragraphScrollPosition.value = position
        saveState(KEY_PARAGRAPH_SCROLL_POSITION, position)
    }

    private fun updateChapterScrollPosition(position: Int) {
        _chapterScrollPosition.value = position
        saveState(KEY_CHAPTER_SCROLL_POSITION, position)
    }

    private fun updateTocScrollPosition(index: Int, offset: Int) {
        _tocScrollIndex.value = index
        _tocScrollOffset.value = offset
        saveState(KEY_TOC_SCROLL_INDEX, index)
        saveState(KEY_TOC_SCROLL_OFFSET, offset)
    }

    private fun selectChapter(chapter: Int) {
        _selectedChapter.value = chapter
        saveState(KEY_SELECTED_CHAPTER, chapter)
    }

    @OptIn(ExperimentalSplitPaneApi::class)
    private fun toggleCommentaries() {
        _showCommentaries.value = !_showCommentaries.value

        // Set appropriate split pane position based on visibility
        if (_showCommentaries.value) {
            _contentSplitPaneState.value = SplitPaneState(
                initialPositionPercentage = _previousContentSplitPosition.value,
                moveEnabled = true
            )
        } else {
            // Save current position before hiding
            if (_contentSplitPaneState.value.positionPercentage > 0) {
                _previousContentSplitPosition.value = _contentSplitPaneState.value.positionPercentage
                // Save previous content split position
                saveState(KEY_PREVIOUS_CONTENT_SPLIT_POSITION, _previousContentSplitPosition.value)
            }
            // Set to a high value to minimize the content area
            _contentSplitPaneState.value = SplitPaneState(
                initialPositionPercentage = 0.95f,
                moveEnabled = false
            )
        }

        saveState(KEY_SHOW_COMMENTARIES, _showCommentaries.value)
        saveState(KEY_CONTENT_SPLIT_PANE_POSITION, _contentSplitPaneState.value.positionPercentage)
    }

    // Store previous split pane positions
    private val _previousMainSplitPosition = MutableStateFlow(getState<Float>(KEY_PREVIOUS_MAIN_SPLIT_POSITION) ?: 0.3f)
    private val _previousTocSplitPosition = MutableStateFlow(getState<Float>(KEY_PREVIOUS_TOC_SPLIT_POSITION) ?: 0.3f)
    private val _previousContentSplitPosition = MutableStateFlow(getState<Float>(KEY_PREVIOUS_CONTENT_SPLIT_POSITION) ?: 0.7f)

    private fun toggleBookTree() {
        val isCurrentlyVisible = _showBookTree.value
        _showBookTree.value = !isCurrentlyVisible

        // Set appropriate split pane position based on visibility
        if (!isCurrentlyVisible) {
            _splitPaneState.value = SplitPaneState(
                initialPositionPercentage = _previousMainSplitPosition.value,
                moveEnabled = true
            )
        } else {
            // Save current position before hiding
            if (_splitPaneState.value.positionPercentage > 0) {
                _previousMainSplitPosition.value = _splitPaneState.value.positionPercentage
                // Save previous main split position
                saveState(KEY_PREVIOUS_MAIN_SPLIT_POSITION, _previousMainSplitPosition.value)
            }
            // Set to zero to hide the panel
            _splitPaneState.value = SplitPaneState(
                initialPositionPercentage = 0f,
                moveEnabled = false
            )
        }

        saveState(KEY_SHOW_BOOK_TREE, _showBookTree.value)
        saveState(KEY_SPLIT_PANE_POSITION, _splitPaneState.value.positionPercentage)
    }

    @OptIn(ExperimentalSplitPaneApi::class)
    private fun toggleToc() {
        val isCurrentlyVisible = _showToc.value
        _showToc.value = !isCurrentlyVisible

        // Set appropriate split pane position based on visibility
        if (!isCurrentlyVisible) {
            _tocSplitPaneState.value = SplitPaneState(
                initialPositionPercentage = _previousTocSplitPosition.value,
                moveEnabled = true
            )
        } else {
            // Save current position before hiding
            if (_tocSplitPaneState.value.positionPercentage > 0) {
                _previousTocSplitPosition.value = _tocSplitPaneState.value.positionPercentage
                // Save previous TOC split position
                saveState(KEY_PREVIOUS_TOC_SPLIT_POSITION, _previousTocSplitPosition.value)
            }
            // Set to zero to hide the panel
            _tocSplitPaneState.value = SplitPaneState(
                initialPositionPercentage = 0f,
                moveEnabled = false
            )
        }

        saveState(KEY_SHOW_TOC, _showToc.value)
        saveState(KEY_TOC_SPLIT_PANE_POSITION, _tocSplitPaneState.value.positionPercentage)
    }

    @OptIn(ExperimentalSplitPaneApi::class)
    private fun saveAllStates() {
        // Save split pane positions
        saveState(KEY_SPLIT_PANE_POSITION, _splitPaneState.value.positionPercentage)
        saveState(KEY_TOC_SPLIT_PANE_POSITION, _tocSplitPaneState.value.positionPercentage)
        saveState(KEY_CONTENT_SPLIT_PANE_POSITION, _contentSplitPaneState.value.positionPercentage)

        // Save previous split pane positions
        saveState(KEY_PREVIOUS_MAIN_SPLIT_POSITION, _previousMainSplitPosition.value)
        saveState(KEY_PREVIOUS_TOC_SPLIT_POSITION, _previousTocSplitPosition.value)
        saveState(KEY_PREVIOUS_CONTENT_SPLIT_POSITION, _previousContentSplitPosition.value)

        // Save UI state
        saveState(KEY_SEARCH_TEXT, _searchText.value)
        saveState(KEY_PARAGRAPH_SCROLL_POSITION, _paragraphScrollPosition.value)
        saveState(KEY_CHAPTER_SCROLL_POSITION, _chapterScrollPosition.value)
        saveState(KEY_SELECTED_CHAPTER, _selectedChapter.value)
        saveState(KEY_SHOW_COMMENTARIES, _showCommentaries.value)
        saveState(KEY_SHOW_BOOK_TREE, _showBookTree.value)
        saveState(KEY_SHOW_TOC, _showToc.value)
        saveState(KEY_TOC_SCROLL_INDEX, _tocScrollIndex.value)
        saveState(KEY_TOC_SCROLL_OFFSET, _tocScrollOffset.value)

        // Save selection state
        _selectedBook.value?.let { book ->
            saveState(KEY_SELECTED_BOOK, book)
        }
        _selectedCategory.value?.let { category ->
            saveState(KEY_SELECTED_CATEGORY, category)
        }
        _selectedLine.value?.let { line ->
            saveState(KEY_SELECTED_LINE, line)
        }

        // Save expanded items state
        saveState(KEY_EXPANDED_CATEGORIES, _expandedCategories.value)
        saveState(KEY_CATEGORY_CHILDREN, _categoryChildren.value)
        saveState(KEY_BOOKS_IN_CATEGORY, _booksInCategory.value)
        saveState(KEY_EXPANDED_TOC_ENTRIES, _expandedTocEntries.value)
        saveState(KEY_TOC_CHILDREN, _tocChildren.value)
    }
}
