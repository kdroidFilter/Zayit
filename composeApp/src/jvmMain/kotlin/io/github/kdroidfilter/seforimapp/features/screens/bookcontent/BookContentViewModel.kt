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
    tabId = savedStateHandle.get<String>("tabId") ?: "",
    stateManager = stateManager
) {
    // Initialize state flows first
    private val _isLoading = MutableStateFlow(false)
    private val _rootCategories = MutableStateFlow<List<Category>>(emptyList())
    private val _expandedCategories = MutableStateFlow<Set<Long>>(emptySet())
    private val _categoryChildren = MutableStateFlow<Map<Long, List<Category>>>(emptyMap())
    private val _booksInCategory = MutableStateFlow<Set<Book>>(emptySet())
    private val _selectedCategory = MutableStateFlow<Category?>(null)
    private val _selectedBook = MutableStateFlow<Book?>(null)
    private val _searchText = MutableStateFlow(getState<String>("searchText") ?: "")
    private val _showBookTree = MutableStateFlow(getState<Boolean>("showBookTree") ?: true)

    @OptIn(ExperimentalSplitPaneApi::class)
    private val _splitPaneState = MutableStateFlow(
        SplitPaneState(
            initialPositionPercentage = getState<Float>("splitPanePosition") ?: 0.05f,
            moveEnabled = true
        )
    )

    @OptIn(ExperimentalSplitPaneApi::class)
    private val _tocSplitPaneState = MutableStateFlow(
        SplitPaneState(
            initialPositionPercentage = getState<Float>("tocSplitPanePosition") ?: 0.05f,
            moveEnabled = true
        )
    )

    @OptIn(ExperimentalSplitPaneApi::class)
    private val _contentSplitPaneState = MutableStateFlow(
        SplitPaneState(
            initialPositionPercentage = getState<Float>("contentSplitPanePosition") ?: 0.7f,
            moveEnabled = true
        )
    )

    private val _bookLines = MutableStateFlow<List<Line>>(emptyList())
    private val _selectedLine = MutableStateFlow<Line?>(null)
    private val _tocEntries = MutableStateFlow<List<TocEntry>>(emptyList())
    private val _expandedTocEntries = MutableStateFlow<Set<Long>>(emptySet())
    private val _tocChildren = MutableStateFlow<Map<Long, List<TocEntry>>>(emptyMap())
    private val _showToc = MutableStateFlow(getState<Boolean>("showToc") ?: true)

    private val _commentaries = MutableStateFlow<List<CommentaryWithText>>(emptyList())
    private val _showCommentaries = MutableStateFlow(getState<Boolean>("showCommentaries") ?: false)
    private val _paragraphScrollPosition = MutableStateFlow(getState<Int>("paragraphScrollPosition") ?: 0)
    private val _chapterScrollPosition = MutableStateFlow(getState<Int>("chapterScrollPosition") ?: 0)
    private val _selectedChapter = MutableStateFlow(getState<Int>("selectedChapter") ?: 0)

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

        // Check if we have a bookId in the savedStateHandle
        savedStateHandle.get<Long>("bookId")?.let { bookId ->
            // Load the book
            viewModelScope.launch {
                _isLoading.value = true
                try {
                    // Get the book from the repository
                    repository.getBook(bookId)?.let { book ->
                        // Load the book
                        loadBook(book)

                        // Check if we have a lineId in the savedStateHandle
                        savedStateHandle.get<Long>("lineId")?.let { lineId ->
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
            _showToc
        ) { entries, expanded, children, visible ->
            TocUiState(
                entries = entries,
                expandedEntries = expanded,
                children = children,
                isVisible = visible
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
            initialPositionPercentage = getState<Float>("splitPanePosition") ?: 0.3f,
            moveEnabled = true
        ),
        tocSplitState = SplitPaneState(
            initialPositionPercentage = getState<Float>("tocSplitPanePosition") ?: 0.3f,
            moveEnabled = true
        ),
        contentSplitState = SplitPaneState(
            initialPositionPercentage = getState<Float>("contentSplitPanePosition") ?: 0.7f,
            moveEnabled = true
        )
    )


    // Implementation methods
    private fun loadRootCategories() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _rootCategories.value = repository.getRootCategories()
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
                            _categoryChildren.value += (category.id to children)
                        }

                        val books = repository.getBooksByCategory(category.id)
                        if (books.isNotEmpty()) {
                            _booksInCategory.value += books
                        }
                    } finally {
                        _isLoading.value = false
                    }
                }
            }
        }
    }

    private fun selectCategory(category: Category) {
        _selectedCategory.value = category
        expandCategory(category)
    }

    private fun loadBook(book: Book) {
        _selectedBook.value = book

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
                        _tocChildren.value += (tocEntry.id to children.ifEmpty { emptyList() })
                    } finally {
                        _isLoading.value = false
                    }
                }
            }
        }
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
        saveState("searchText", text)
    }

    private fun updateParagraphScrollPosition(position: Int) {
        _paragraphScrollPosition.value = position
        saveState("paragraphScrollPosition", position)
    }

    private fun updateChapterScrollPosition(position: Int) {
        _chapterScrollPosition.value = position
        saveState("chapterScrollPosition", position)
    }

    private fun selectChapter(chapter: Int) {
        _selectedChapter.value = chapter
        saveState("selectedChapter", chapter)
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
            }
            // Set to a high value to minimize the content area
            _contentSplitPaneState.value = SplitPaneState(
                initialPositionPercentage = 0.95f,
                moveEnabled = false
            )
        }

        saveState("showCommentaries", _showCommentaries.value)
    }

    // Store previous split pane positions
    private val _previousMainSplitPosition = MutableStateFlow(0.3f)
    private val _previousTocSplitPosition = MutableStateFlow(0.3f)
    private val _previousContentSplitPosition = MutableStateFlow(0.7f)

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
            }
            // Set to zero to hide the panel
            _splitPaneState.value = SplitPaneState(
                initialPositionPercentage = 0f,
                moveEnabled = false
            )
        }

        saveState("showBookTree", _showBookTree.value)
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
            }
            // Set to zero to hide the panel
            _tocSplitPaneState.value = SplitPaneState(
                initialPositionPercentage = 0f,
                moveEnabled = false
            )
        }

        saveState("showToc", _showToc.value)
    }

    @OptIn(ExperimentalSplitPaneApi::class)
    private fun saveAllStates() {
        saveState("splitPanePosition", _splitPaneState.value.positionPercentage)
        saveState("tocSplitPanePosition", _tocSplitPaneState.value.positionPercentage)
        saveState("contentSplitPanePosition", _contentSplitPaneState.value.positionPercentage)
        saveState("searchText", _searchText.value)
        saveState("paragraphScrollPosition", _paragraphScrollPosition.value)
        saveState("selectedChapter", _selectedChapter.value)
        saveState("showCommentaries", _showCommentaries.value)
        saveState("showBookTree", _showBookTree.value)
        saveState("showToc", _showToc.value)
    }
}
