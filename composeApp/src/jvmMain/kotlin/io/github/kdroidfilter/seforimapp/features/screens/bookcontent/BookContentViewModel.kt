package io.github.kdroidfilter.seforimapp.features.screens.bookcontent

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.paging.Pager
import app.cash.paging.PagingConfig
import app.cash.paging.PagingData
import app.cash.paging.cachedIn
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabAwareViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabStateManager
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabType
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.utils.debugln
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.pagination.LinesPagingSource
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.pagination.LineCommentsPagingSource
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.pagination.PagingDefaults
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.models.*
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.SplitPaneState

@OptIn(ExperimentalSplitPaneApi::class)
class BookContentViewModel(
    savedStateHandle: SavedStateHandle,
    stateManager: TabStateManager,
    private val repository: SeforimRepository,
    private val titleUpdateManager: TabTitleUpdateManager,
    private val navigator: io.github.kdroidfilter.seforimapp.core.presentation.navigation.Navigator
) : TabAwareViewModel(
    tabId = savedStateHandle.get<String>(KEY_TAB_ID) ?: "",
    stateManager = stateManager
) {
    private val currentTabId: String = savedStateHandle.get<String>(KEY_TAB_ID) ?: ""

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
        const val KEY_BOOK_TREE_SCROLL_INDEX = "bookTreeScrollIndex"
        const val KEY_BOOK_TREE_SCROLL_OFFSET = "bookTreeScrollOffset"
        const val KEY_CONTENT_SCROLL_INDEX = "contentScrollIndex"
        const val KEY_CONTENT_SCROLL_OFFSET = "contentScrollOffset"
        const val KEY_CONTENT_ANCHOR_ID = "contentAnchorId"
        const val KEY_CONTENT_ANCHOR_INDEX = "contentAnchorIndex"
        const val KEY_COMMENTARIES_SELECTED_TAB = "commentariesSelectedTab"
        const val KEY_COMMENTARIES_SCROLL_INDEX = "commentariesScrollIndex"
        const val KEY_COMMENTARIES_SCROLL_OFFSET = "commentariesScrollOffset"
        const val KEY_SELECTED_COMMENTATORS_BY_LINE = "selectedCommentatorsByLine"
        const val KEY_SELECTED_COMMENTATORS_BY_BOOK = "selectedCommentatorsByBook"

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
    private val _bookTreeScrollIndex = MutableStateFlow(getState<Int>(KEY_BOOK_TREE_SCROLL_INDEX) ?: 0)
    private val _bookTreeScrollOffset = MutableStateFlow(getState<Int>(KEY_BOOK_TREE_SCROLL_OFFSET) ?: 0)

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
    private val _contentScrollIndex = MutableStateFlow(getState<Int>(KEY_CONTENT_SCROLL_INDEX) ?: 0)
    private val _contentScrollOffset = MutableStateFlow(getState<Int>(KEY_CONTENT_SCROLL_OFFSET) ?: 0)
    private val _contentAnchorId = MutableStateFlow(getState<Long>(KEY_CONTENT_ANCHOR_ID) ?: -1L)
    private val _contentAnchorIndex = MutableStateFlow(getState<Int>(KEY_CONTENT_ANCHOR_INDEX) ?: 0)
    private val _scrollToLineTimestamp = MutableStateFlow(0L)

    // Commentaries tab and scroll state
    private val _commentariesSelectedTab = MutableStateFlow(getState<Int>(KEY_COMMENTARIES_SELECTED_TAB) ?: 0)
    private val _commentariesScrollIndex = MutableStateFlow(getState<Int>(KEY_COMMENTARIES_SCROLL_INDEX) ?: 0)
    private val _commentariesScrollOffset = MutableStateFlow(getState<Int>(KEY_COMMENTARIES_SCROLL_OFFSET) ?: 0)

    // Selected commentators per line (lineId -> set of commentator bookIds)
    private val _selectedCommentatorsByLine = MutableStateFlow<Map<Long, Set<Long>>>(
        getState(KEY_SELECTED_COMMENTATORS_BY_LINE) ?: emptyMap()
    )

    // Selected commentators per book (bookId -> set of commentator bookIds)
    private val _selectedCommentatorsByBook = MutableStateFlow<Map<Long, Set<Long>>>(
        getState(KEY_SELECTED_COMMENTATORS_BY_BOOK) ?: emptyMap()
    )

    // Paging data flow for lines
    private val _linesPagingData = MutableStateFlow<Flow<PagingData<Line>>?>(null)
    @OptIn(ExperimentalCoroutinesApi::class)
    val linesPagingData: Flow<PagingData<Line>> = _linesPagingData
        .filterNotNull()
        .flatMapLatest { it }
        .cachedIn(viewModelScope)


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
            // We have a restored book with saved scroll position
            debugln { "Restoring book ${restoredBook.id} with saved position: anchor=${_contentAnchorId.value}, index=${_contentScrollIndex.value}" }

            viewModelScope.launch {
                _isLoading.value = true
                try {
                    // Load the book data with the restored position
                    // Don't call loadBook as it might reset the position
                    loadBookData(restoredBook)

                    // Check if we have a restored line and fetch its commentaries
                    _selectedLine.value?.let { line ->
                        fetchCommentariesForLine(line)
                        // Reapply previously selected commentators for this book on the restored line
                        reapplySelectedCommentatorsForLine(line)
                    }
                } finally {
                    _isLoading.value = false
                }
            }
        } else {
            // If no restored book, check if we have a bookId in the savedStateHandle
            savedStateHandle.get<Long>(KEY_BOOK_ID)?.let { bookId ->
                viewModelScope.launch {
                    _isLoading.value = true
                    try {
                        // Get the book from the repository
                        repository.getBook(bookId)?.let { book ->
                            // Check if we have a lineId to center on
                            val targetLineId = savedStateHandle.get<Long>(KEY_LINE_ID)

                            if (targetLineId != null) {
                                // Load with specific line as anchor
                                _selectedBook.value = book
                                _contentAnchorId.value = targetLineId
                                _contentScrollIndex.value = 0  // Start at beginning, will scroll to line
                                _contentScrollOffset.value = 0
                                loadBookData(book, targetLineId)

                                // Also select the line
                                repository.getLine(targetLineId)?.let { line ->
                                    _selectedLine.value = line
                                    fetchCommentariesForLine(line)
                                    // Trigger scroll by updating timestamp
                                    _scrollToLineTimestamp.value = System.currentTimeMillis()
                                }
                            } else {
                                // Normal load - this will reset positions
                                loadBook(book)
                            }
                        }
                    } finally {
                        _isLoading.value = false
                    }
                }
            }
        }

        // Observe the selected book and update the tab title when it changes
        viewModelScope.launch {
            _selectedBook
                .filterNotNull()
                .collect { book ->
                    updateTabTitle(book)
                }
        }
    }

    fun onEvent(event: BookContentEvent) {
        when (event) {
            // Navigation events
            is BookContentEvent.SearchTextChanged -> updateSearchText(event.text)
            is BookContentEvent.CategorySelected -> selectCategory(event.category)
            is BookContentEvent.BookSelected -> loadBook(event.book)
            is BookContentEvent.BookSelectedInNewTab -> {
                viewModelScope.launch {
                    navigator.navigate(
                        TabsDestination.BookContent(
                            bookId = event.book.id,
                            tabId = java.util.UUID.randomUUID().toString()
                        )
                    )
                }
            }
            BookContentEvent.ToggleBookTree -> toggleBookTree()

            // TOC events
            is BookContentEvent.TocEntryExpanded -> expandTocEntry(event.entry)
            BookContentEvent.ToggleToc -> toggleToc()
            is BookContentEvent.TocScrolled -> updateTocScrollPosition(event.index, event.offset)

            // Book tree events
            is BookContentEvent.BookTreeScrolled -> updateBookTreeScrollPosition(event.index, event.offset)

            // Content events
            is BookContentEvent.LineSelected -> selectLine(event.line)
            is BookContentEvent.LoadAndSelectLine -> loadAndSelectLine(event.lineId)
            BookContentEvent.ToggleCommentaries -> toggleCommentaries()
            is BookContentEvent.ContentScrolled -> updateContentScrollPosition(
                event.anchorId, 
                event.anchorIndex, 
                event.scrollIndex, 
                event.scrollOffset
            )
            BookContentEvent.NavigateToPreviousLine -> navigateToPreviousLine()
            BookContentEvent.NavigateToNextLine -> navigateToNextLine()
            is BookContentEvent.OpenCommentaryTarget -> {
                viewModelScope.launch {
                    navigator.navigate(
                        TabsDestination.BookContent(
                            bookId = event.bookId,
                            tabId = java.util.UUID.randomUUID().toString(),
                            lineId = event.lineId
                        )
                    )
                }
            }

            // Commentaries events
            is BookContentEvent.CommentariesTabSelected -> updateCommentariesTabIndex(event.index)
            is BookContentEvent.CommentariesScrolled -> updateCommentariesScrollPosition(event.index, event.offset)
            is BookContentEvent.SelectedCommentatorsChanged -> updateSelectedCommentators(event.lineId, event.selectedIds)

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
            Pair(data, visible)
        }.combine(_bookTreeScrollIndex) { (data, visible), scrollIndex ->
            Triple(data, visible, scrollIndex)
        }.combine(_bookTreeScrollOffset) { (data, visible, scrollIndex), scrollOffset ->
            NavigationUiState(
                rootCategories = data.rootCategories,
                expandedCategories = data.expandedCategories,
                categoryChildren = data.categoryChildren,
                booksInCategory = data.booksInCategory,
                selectedCategory = data.selectedCategory,
                selectedBook = data.selectedBook,
                searchText = data.searchText,
                isVisible = visible,
                scrollIndex = scrollIndex,
                scrollOffset = scrollOffset
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
        return _selectedLine.combine(_commentaries) { line, commentaries ->
            Pair(line, commentaries)
        }.combine(_showCommentaries) { (line, commentaries), showComm ->
            ContentData(line, commentaries, showComm)
        }.combine(_paragraphScrollPosition) { data, pScroll ->
            data.copy(paragraphScrollPosition = pScroll)
        }.combine(_chapterScrollPosition) { data, cScroll ->
            data.copy(chapterScrollPosition = cScroll)
        }.combine(_selectedChapter) { data, chapter ->
            Pair(data, chapter)
        }.combine(_contentScrollIndex) { (data, chapter), scrollIndex ->
            Triple(data, chapter, scrollIndex)
        }.combine(_contentScrollOffset) { (data, chapter, scrollIndex), scrollOffset ->
            Pair(data.copy(scrollIndex = scrollIndex, scrollOffset = scrollOffset), chapter)
        }.combine(_commentariesSelectedTab) { (data, chapter), selectedTab ->
            Pair(data.copy(commentariesSelectedTab = selectedTab), chapter)
        }.combine(_commentariesScrollIndex) { (data, chapter), scrollIndex ->
            Pair(data.copy(commentariesScrollIndex = scrollIndex), chapter)
        }.combine(_commentariesScrollOffset) { (data, chapter), scrollOffset ->
            Pair(
                ContentUiState(
                    lines = emptyList(),
                    selectedLine = data.selectedLine,
                    commentaries = data.commentaries,
                    showCommentaries = data.showCommentaries,
                    paragraphScrollPosition = data.paragraphScrollPosition,
                    chapterScrollPosition = data.chapterScrollPosition,
                    selectedChapter = chapter,
                    scrollIndex = data.scrollIndex,
                    scrollOffset = data.scrollOffset,
                    commentariesSelectedTab = data.commentariesSelectedTab,
                    commentariesScrollIndex = data.commentariesScrollIndex,
                    commentariesScrollOffset = scrollOffset,
                    shouldScrollToLine = data.shouldScrollToLine
                ),
                scrollOffset
            )
        }.combine(_scrollToLineTimestamp) { (contentState, _), timestamp ->
            contentState.copy(scrollToLineTimestamp = timestamp)
        }
        .combine(_contentAnchorId) { content, anchorId ->
            content.copy(anchorId = anchorId)
        }
        .combine(_contentAnchorIndex) { content, anchorIndex ->
            content.copy(anchorIndex = anchorIndex)
        }
        .combine(_selectedCommentatorsByLine) { content, map ->
            val ids = content.selectedLine?.let { line -> map[line.id] } ?: emptySet()
            content.copy(selectedCommentatorIds = ids)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ContentUiState())
    }

    private data class ContentData(
        val selectedLine: Line?,
        val commentaries: List<CommentaryWithText>,
        val showCommentaries: Boolean,
        val shouldScrollToLine: Boolean = false,
        val paragraphScrollPosition: Int = 0,
        val chapterScrollPosition: Int = 0,
        val scrollIndex: Int = 0,
        val scrollOffset: Int = 0,
        val commentariesSelectedTab: Int = 0,
        val commentariesScrollIndex: Int = 0,
        val commentariesScrollOffset: Int = 0
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

    private fun executeLoadingOperation(block: suspend () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                block()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadRootCategories() {
        executeLoadingOperation {
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
                        // Exception caught and ignored
                    }
                }

                // Update books in category
                if (booksToLoad.isNotEmpty()) {
                    _booksInCategory.value += booksToLoad
                }
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
                executeLoadingOperation {
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

    private fun loadBook(book: Book, preservePosition: Boolean = false) {
        val previousBook = _selectedBook.value
        _selectedBook.value = book

        // If we change the book AND we don't want to preserve the position
        if (previousBook?.id != book.id && !preservePosition) {
            debugln { "Loading new book, resetting scroll position" }
            _contentScrollIndex.value = 0
            _contentScrollOffset.value = 0
            _tocScrollIndex.value = 0
            _tocScrollOffset.value = 0

            // Reset anchor values to avoid incorrect inter-book anchoring
            _contentAnchorId.value = -1L
            _contentAnchorIndex.value = 0
            saveState(KEY_CONTENT_ANCHOR_ID, -1L)
            saveState(KEY_CONTENT_ANCHOR_INDEX, 0)
            saveState(KEY_CONTENT_SCROLL_INDEX, 0)
            saveState(KEY_CONTENT_SCROLL_OFFSET, 0)

            // Hide commentaries pane on explicit book change to preserve UX
            if (_showCommentaries.value) {
                toggleCommentaries()
            }
        } else {
            debugln { "Loading book with preserved position: anchorId=${_contentAnchorId.value}, scrollIndex=${_contentScrollIndex.value}" }
        }

        // Update tab title with book name
        updateTabTitle(book)

        loadBookData(book)
    }

    private fun updateTabTitle(book: Book) {
        // Create a title using the book name
        val title = book.title

        // Update the tab title using the stored tabId
        // Set tabType=TabType.BOOK to indicate this tab contains book content
        titleUpdateManager.updateTabTitle(currentTabId, title, TabType.BOOK)
    }

    private fun loadBookData(book: Book, forceAnchorId: Long? = null) {
        executeLoadingOperation {
            // Determine the initial line for the PagingSource
            // Use the anchor ONLY if it is valid and we have scrolled beyond the first page
            val shouldUseAnchor = _contentAnchorId.value != -1L &&
                    _contentScrollIndex.value > PagingDefaults.LINES.INITIAL_LOAD_SIZE

            val initialLineId = when {
                forceAnchorId != null -> forceAnchorId
                shouldUseAnchor -> _contentAnchorId.value
                _selectedLine.value != null -> _selectedLine.value?.id
                else -> null
            }

            debugln { "Loading book data - Using anchor: $shouldUseAnchor, initialLineId: $initialLineId, scrollIndex: ${_contentScrollIndex.value}" }

            val pager = Pager<Int, Line>(
                config = PagingDefaults.LINES.config(placeholders = false),
                pagingSourceFactory = {
                    LinesPagingSource(repository, book.id, initialLineId)
                }
            )

            _linesPagingData.value = pager.flow.cachedIn(viewModelScope)

            // Load root TOC entries
            val rootToc = repository.getBookRootToc(book.id)
            _tocEntries.value = rootToc
            _tocChildren.value = mapOf(-1L to rootToc)

            // Only set expanded entries if no state has already been restored
            if (_expandedTocEntries.value.isEmpty()) {
                rootToc.firstOrNull()
                    ?.takeIf { it.hasChildren }
                    ?.let { _expandedTocEntries.value = setOf(it.id) }
            }

            // Populate children for EACH already expanded entry
            _expandedTocEntries.value.forEach { id ->
                if (!_tocChildren.value.containsKey(id)) {
                    repository.getTocChildren(id)
                        .takeIf { it.isNotEmpty() }
                        ?.let { _tocChildren.value += (id to it) }
                }
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

            // Load children only if the entry has them AND we don't already have them
            if (tocEntry.hasChildren && !_tocChildren.value.containsKey(tocEntry.id)) {
                executeLoadingOperation {
                    val children = repository.getTocChildren(tocEntry.id)
                    if (children.isNotEmpty()) {
                        val updatedChildren = _tocChildren.value + (tocEntry.id to children)
                        _tocChildren.value = updatedChildren
                        saveState(KEY_TOC_CHILDREN, updatedChildren)
                    }
                }
            }
        }

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
        debugln { "[selectLine] Selecting line with id=${line.id}, index=${line.lineIndex}" }
        
        _selectedLine.value = line
        fetchCommentariesForLine(line)
        // Reapply previously selected commentators for this book on this new line
        reapplySelectedCommentatorsForLine(line)

        // Save selected line
        saveState(KEY_SELECTED_LINE, line)
    }


    private fun fetchCommentariesForLine(line: Line) {
        // Legacy list-based storage is cleared; UI now builds pagers per commentator via ViewModel
        _commentaries.value = emptyList()
    }

    // Reapply previously selected commentators (per-book) for a newly selected line
    private fun reapplySelectedCommentatorsForLine(line: Line) {
        val bookId = _selectedBook.value?.id ?: line.bookId
        val remembered = _selectedCommentatorsByBook.value[bookId] ?: emptySet()
        if (remembered.isEmpty()) return

        viewModelScope.launch {
            try {
                val availableMap = getAvailableCommentatorsForLine(line.id)
                val availableIds = availableMap.values.toSet()
                val intersection = remembered.intersect(availableIds)

                // Update per-line selection
                val byLine = _selectedCommentatorsByLine.value.toMutableMap()
                if (intersection.isEmpty()) {
                    byLine.remove(line.id)
                } else {
                    byLine[line.id] = intersection
                }
                _selectedCommentatorsByLine.value = byLine
                saveState(KEY_SELECTED_COMMENTATORS_BY_LINE, byLine)

                // Also update per-book memory to the intersection — treat missing as deselected
                val byBook = _selectedCommentatorsByBook.value.toMutableMap()
                if (intersection.isEmpty()) {
                    byBook.remove(bookId)
                } else {
                    byBook[bookId] = intersection
                }
                _selectedCommentatorsByBook.value = byBook
                saveState(KEY_SELECTED_COMMENTATORS_BY_BOOK, byBook)
            } catch (e: Exception) {
                // ignore errors silently; selection memory is best-effort
            }
        }
    }

    fun buildCommentariesPagerFor(lineId: Long, commentatorId: Long? = null): Flow<PagingData<CommentaryWithText>> {
        val ids = commentatorId?.let { setOf(it) } ?: emptySet()
        val pager = Pager(
            config = PagingDefaults.COMMENTS.config(placeholders = false),
            pagingSourceFactory = {
                LineCommentsPagingSource(repository, lineId, ids)
            }
        )
        return pager.flow.cachedIn(viewModelScope)
    }

    suspend fun getAvailableCommentatorsForLine(lineId: Long): Map<String, Long> {
        return try {
            val list = repository.getCommentariesForLines(listOf(lineId))
                .filter { it.link.connectionType == io.github.kdroidfilter.seforimlibrary.core.models.ConnectionType.COMMENTARY }
            val map = LinkedHashMap<String, Long>()
            list.forEach { c ->
                if (!map.containsKey(c.targetBookTitle)) {
                    map[c.targetBookTitle] = c.link.targetBookId
                }
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun loadAndSelectLine(lineId: Long) {
        val book = _selectedBook.value ?: return

        viewModelScope.launch {
            repository.getLine(lineId)?.let { targetLine ->
                if (targetLine.bookId != book.id) return@launch

                debugln { "[loadAndSelectLine] Loading line $lineId at index ${targetLine.lineIndex}" }

                // Recreate the pager centered on the target line
                val pager = Pager(
                    config = PagingDefaults.LINES.config(placeholders = false),
                    pagingSourceFactory = {
                        LinesPagingSource(repository, book.id, targetLine.id)
                    }
                )

                _linesPagingData.value = pager.flow.cachedIn(viewModelScope)
                _selectedLine.value = targetLine

                // Update the anchor for future restoration
                _contentAnchorId.value = targetLine.id
                _contentAnchorIndex.value = 0 // Will be updated when scrolling
                saveState(KEY_CONTENT_ANCHOR_ID, targetLine.id)

                // Fetch commentaries
                fetchCommentariesForLine(targetLine)
                // Reapply previously selected commentators for this book on this new line
                reapplySelectedCommentatorsForLine(targetLine)
                
                // Update timestamp to force scroll
                _scrollToLineTimestamp.value = System.currentTimeMillis()
            }
        }
    }

    private fun updateSearchText(text: String) {
        _searchText.value = text
        saveState(KEY_SEARCH_TEXT, text)
    }

    private fun <T : Any> updateScrollValue(stateFlow: MutableStateFlow<T>, value: T, stateKey: String) {
        stateFlow.value = value
        saveState(stateKey, value)
    }

    private fun updateScrollPosition(
        indexFlow: MutableStateFlow<Int>,
        offsetFlow: MutableStateFlow<Int>,
        index: Int,
        offset: Int,
        indexKey: String,
        offsetKey: String
    ) {
        indexFlow.value = index
        offsetFlow.value = offset
        saveState(indexKey, index)
        saveState(offsetKey, offset)
    }

    private fun updateParagraphScrollPosition(position: Int) {
        updateScrollValue(_paragraphScrollPosition, position, KEY_PARAGRAPH_SCROLL_POSITION)
    }

    private fun updateChapterScrollPosition(position: Int) {
        updateScrollValue(_chapterScrollPosition, position, KEY_CHAPTER_SCROLL_POSITION)
    }

    private fun updateTocScrollPosition(index: Int, offset: Int) {
        updateScrollPosition(
            _tocScrollIndex,
            _tocScrollOffset,
            index,
            offset,
            KEY_TOC_SCROLL_INDEX,
            KEY_TOC_SCROLL_OFFSET
        )
    }

    private fun updateBookTreeScrollPosition(index: Int, offset: Int) {
        updateScrollPosition(
            _bookTreeScrollIndex,
            _bookTreeScrollOffset,
            index,
            offset,
            KEY_BOOK_TREE_SCROLL_INDEX,
            KEY_BOOK_TREE_SCROLL_OFFSET
        )
    }

    private fun updateContentScrollPosition(
        anchorId: Long,
        anchorIndex: Int,
        scrollIndex: Int,
        scrollOffset: Int
    ) {
        debugln { "Updating scroll position: anchor=$anchorId, anchorIndex=$anchorIndex, scrollIndex=$scrollIndex, offset=$scrollOffset" }

        // Always update all values, even if anchorId is -1L
        // This ensures we have the scroll position saved for first page scenarios
        _contentAnchorId.value = anchorId
        _contentAnchorIndex.value = anchorIndex
        _contentScrollIndex.value = scrollIndex
        _contentScrollOffset.value = scrollOffset

        // Save all values immediately
        saveState(KEY_CONTENT_ANCHOR_ID, anchorId)
        saveState(KEY_CONTENT_ANCHOR_INDEX, anchorIndex)
        saveState(KEY_CONTENT_SCROLL_INDEX, scrollIndex)
        saveState(KEY_CONTENT_SCROLL_OFFSET, scrollOffset)
    }

    private fun updateCommentariesTabIndex(index: Int) {
        updateScrollValue(_commentariesSelectedTab, index, KEY_COMMENTARIES_SELECTED_TAB)
    }

    private fun updateCommentariesScrollPosition(index: Int, offset: Int) {
        updateScrollPosition(
            _commentariesScrollIndex,
            _commentariesScrollOffset,
            index,
            offset,
            KEY_COMMENTARIES_SCROLL_INDEX,
            KEY_COMMENTARIES_SCROLL_OFFSET
        )
    }

    private fun updateSelectedCommentators(lineId: Long, selectedIds: Set<Long>) {
        // Update per-line selection
        val current = _selectedCommentatorsByLine.value.toMutableMap()
        if (selectedIds.isEmpty()) {
            current.remove(lineId)
        } else {
            current[lineId] = selectedIds
        }
        _selectedCommentatorsByLine.value = current
        // Persist per-line
        saveState(KEY_SELECTED_COMMENTATORS_BY_LINE, current)

        // Also update per-book memory to exactly the current selection (treat others as deselected)
        _selectedBook.value?.let { book ->
            viewModelScope.launch {
                val byBook = _selectedCommentatorsByBook.value.toMutableMap()
                if (selectedIds.isEmpty()) {
                    byBook.remove(book.id)
                } else {
                    byBook[book.id] = selectedIds
                }
                _selectedCommentatorsByBook.value = byBook
                saveState(KEY_SELECTED_COMMENTATORS_BY_BOOK, byBook)
            }
        }
    }

    private fun selectChapter(chapter: Int) {
        _selectedChapter.value = chapter
        saveState(KEY_SELECTED_CHAPTER, chapter)
    }
    
    private fun navigateToPreviousLine() {
        val currentLine = _selectedLine.value ?: return
        val currentBook = _selectedBook.value ?: return
        
        debugln { "[navigateToPreviousLine] Called with currentLine.lineIndex=${currentLine.lineIndex}" }
        
        // Ensure we're only navigating within the same book
        if (currentLine.bookId != currentBook.id) return
        
        // If we're already at the first line, do nothing
        if (currentLine.lineIndex <= 0) return
        
        viewModelScope.launch {
            try {
                // Get the previous line using the repository method
                val previousLine = repository.getPreviousLine(currentBook.id, currentLine.lineIndex)
                
                debugln { "[navigateToPreviousLine] Previous line: ${previousLine?.lineIndex}" }
                
                // If we found a previous line, select it
                if (previousLine != null) {
                    debugln { "[navigateToPreviousLine] Selecting line with index ${previousLine.lineIndex}" }
                    selectLine(previousLine)
                    
                    // Update timestamp to force scroll to the selected line
                    _scrollToLineTimestamp.value = System.currentTimeMillis()
                }
            } catch (e: Exception) {
                // Log the error and continue
                debugln { "[navigateToPreviousLine] Error: ${e.message}" }
            }
        }
    }
    
    private fun navigateToNextLine() {
        val currentLine = _selectedLine.value ?: return
        val currentBook = _selectedBook.value ?: return
        
        debugln { "[navigateToNextLine] Called with currentLine.lineIndex=${currentLine.lineIndex}" }
        
        // Ensure we're only navigating within the same book
        if (currentLine.bookId != currentBook.id) return
        
        viewModelScope.launch {
            try {
                // Get the next line using the repository method
                val nextLine = repository.getNextLine(currentBook.id, currentLine.lineIndex)
                
                debugln { "[navigateToNextLine] Next line: ${nextLine?.lineIndex}" }
                
                // If we found a next line, select it
                if (nextLine != null) {
                    debugln { "[navigateToNextLine] Selecting line with index ${nextLine.lineIndex}" }
                    selectLine(nextLine)
                    
                    // Update timestamp to force scroll to the selected line
                    _scrollToLineTimestamp.value = System.currentTimeMillis()
                }
            } catch (e: Exception) {
                // Log the error and continue
                debugln { "[navigateToNextLine] Error: ${e.message}" }
            }
        }
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

    private fun <T : Any> saveStateFromFlow(key: String, stateFlow: StateFlow<T>) {
        saveState(key, stateFlow.value)
    }

    private fun <T : Any> saveNullableStateFromFlow(key: String, stateFlow: StateFlow<T?>) {
        stateFlow.value?.let { value ->
            saveState(key, value)
        }
    }

    private fun saveStateGroup(vararg keyValuePairs: Pair<String, StateFlow<*>>) {
        keyValuePairs.forEach { (key, flow) ->
            when (flow) {
                is StateFlow<*> -> {
                    flow.value?.let { value ->
                        // All non-null values are instances of Any in Kotlin
                        saveState(key, value)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalSplitPaneApi::class)
    private fun saveAllStates() {
        // Save split pane positions
        saveStateGroup(
            KEY_SPLIT_PANE_POSITION to _splitPaneState.map { it.positionPercentage }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.3f),
            KEY_TOC_SPLIT_PANE_POSITION to _tocSplitPaneState.map { it.positionPercentage }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.3f),
            KEY_CONTENT_SPLIT_PANE_POSITION to _contentSplitPaneState.map { it.positionPercentage }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.7f)
        )

        // Save previous split pane positions
        saveStateGroup(
            KEY_PREVIOUS_MAIN_SPLIT_POSITION to _previousMainSplitPosition,
            KEY_PREVIOUS_TOC_SPLIT_POSITION to _previousTocSplitPosition,
            KEY_PREVIOUS_CONTENT_SPLIT_POSITION to _previousContentSplitPosition
        )

        // Save UI state - scroll positions
        saveStateGroup(
            KEY_PARAGRAPH_SCROLL_POSITION to _paragraphScrollPosition,
            KEY_CHAPTER_SCROLL_POSITION to _chapterScrollPosition,
            KEY_SELECTED_CHAPTER to _selectedChapter,
            KEY_TOC_SCROLL_INDEX to _tocScrollIndex,
            KEY_TOC_SCROLL_OFFSET to _tocScrollOffset,
            KEY_BOOK_TREE_SCROLL_INDEX to _bookTreeScrollIndex,
            KEY_BOOK_TREE_SCROLL_OFFSET to _bookTreeScrollOffset,
            KEY_CONTENT_SCROLL_INDEX to _contentScrollIndex,
            KEY_CONTENT_SCROLL_OFFSET to _contentScrollOffset,
            KEY_COMMENTARIES_SELECTED_TAB to _commentariesSelectedTab,
            KEY_COMMENTARIES_SCROLL_INDEX to _commentariesScrollIndex,
            KEY_COMMENTARIES_SCROLL_OFFSET to _commentariesScrollOffset,
            KEY_SELECTED_COMMENTATORS_BY_LINE to _selectedCommentatorsByLine,
            KEY_SELECTED_COMMENTATORS_BY_BOOK to _selectedCommentatorsByBook
        )

        // Save UI state - visibility flags and text
        saveStateGroup(
            KEY_SEARCH_TEXT to _searchText,
            KEY_SHOW_COMMENTARIES to _showCommentaries,
            KEY_SHOW_BOOK_TREE to _showBookTree,
            KEY_SHOW_TOC to _showToc
        )

        // Save selection state
        saveNullableStateFromFlow(KEY_SELECTED_BOOK, _selectedBook)
        saveNullableStateFromFlow(KEY_SELECTED_CATEGORY, _selectedCategory)
        saveNullableStateFromFlow(KEY_SELECTED_LINE, _selectedLine)

        // Save expanded items state
        saveStateGroup(
            KEY_EXPANDED_CATEGORIES to _expandedCategories,
            KEY_CATEGORY_CHILDREN to _categoryChildren,
            KEY_BOOKS_IN_CATEGORY to _booksInCategory,
            KEY_EXPANDED_TOC_ENTRIES to _expandedTocEntries,
            KEY_TOC_CHILDREN to _tocChildren
        )
    }
}