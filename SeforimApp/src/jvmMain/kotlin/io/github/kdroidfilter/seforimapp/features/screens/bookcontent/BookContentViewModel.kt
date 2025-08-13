package io.github.kdroidfilter.seforimapp.features.screens.bookcontent

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.paging.PagingData
import app.cash.paging.cachedIn
import io.github.kdroidfilter.seforim.navigation.Navigator
import io.github.kdroidfilter.seforim.tabs.*
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.state.BookContentStateManager
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.state.BookContentUiState
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.state.Providers
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.state.StateKeys
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.usecases.CommentariesUseCase
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.usecases.ContentUseCase
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.usecases.NavigationUseCase
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.usecases.TocUseCase
import io.github.kdroidfilter.seforimapp.logger.debugln
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi

/**
 * ViewModel simplifié pour l'écran de contenu du livre
 */
@OptIn(ExperimentalSplitPaneApi::class)
class BookContentViewModel(
    savedStateHandle: SavedStateHandle,
    private val tabStateManager: TabStateManager,
    private val repository: SeforimRepository,
    private val titleUpdateManager: TabTitleUpdateManager,
    private val navigator: Navigator
) : TabAwareViewModel(
    tabId = savedStateHandle.get<String>(StateKeys.TAB_ID) ?: "",
    stateManager = tabStateManager
) {
    private val currentTabId: String = savedStateHandle.get<String>(StateKeys.TAB_ID) ?: ""

    // State Manager centralisé
    private val stateManager = BookContentStateManager(currentTabId, tabStateManager)

    // UseCases
    private val navigationUseCase = NavigationUseCase(repository, stateManager)
    private val contentUseCase = ContentUseCase(repository, stateManager)
    private val tocUseCase = TocUseCase(repository, stateManager)
    private val commentariesUseCase = CommentariesUseCase(repository, stateManager, viewModelScope)

    // Paging pour les lignes
    private val _linesPagingData = MutableStateFlow<Flow<PagingData<Line>>?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val linesPagingData: Flow<PagingData<Line>> = _linesPagingData
        .filterNotNull()
        .flatMapLatest { it }
        .cachedIn(viewModelScope)

    // État UI unifié (state is already UI-ready; just inject providers and compute per-line selections)
    val uiState: StateFlow<BookContentUiState> = stateManager.state
        .map { state ->
            val lineId = state.content.selectedLine?.id
            val selectedCommentators = lineId?.let { state.content.selectedCommentatorsByLine[it] } ?: emptySet()
            val selectedLinks = lineId?.let { state.content.selectedLinkSourcesByLine[it] } ?: emptySet()
            state.copy(
                providers = Providers(
                    linesPagingData = linesPagingData,
                    buildCommentariesPagerFor = commentariesUseCase::buildCommentariesPager,
                    getAvailableCommentatorsForLine = commentariesUseCase::getAvailableCommentators,
                    buildLinksPagerFor = commentariesUseCase::buildLinksPager,
                    getAvailableLinksForLine = commentariesUseCase::getAvailableLinks
                ),
                content = state.content.copy(
                    selectedCommentatorIds = selectedCommentators,
                    selectedTargumSourceIds = selectedLinks
                )
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            run {
                val s = stateManager.state.value
                val lineId = s.content.selectedLine?.id
                val selectedCommentators = lineId?.let { s.content.selectedCommentatorsByLine[it] } ?: emptySet()
                val selectedLinks = lineId?.let { s.content.selectedLinkSourcesByLine[it] } ?: emptySet()
                s.copy(
                    providers = Providers(
                        linesPagingData = linesPagingData,
                        buildCommentariesPagerFor = commentariesUseCase::buildCommentariesPager,
                        getAvailableCommentatorsForLine = commentariesUseCase::getAvailableCommentators,
                        buildLinksPagerFor = commentariesUseCase::buildLinksPager,
                        getAvailableLinksForLine = commentariesUseCase::getAvailableLinks
                    ),
                    content = s.content.copy(
                        selectedCommentatorIds = selectedCommentators,
                        selectedTargumSourceIds = selectedLinks
                    )
                )
            }
        )

    init {
        initialize(savedStateHandle)
    }

    /**
     * Initialisation du ViewModel
     */
    private fun initialize(savedStateHandle: SavedStateHandle) {
        viewModelScope.launch {
            // Charger les catégories racine
            navigationUseCase.loadRootCategories()

            // Vérifier si on a un livre restauré
            val restoredBook = stateManager.state.value.navigation.selectedBook
            if (restoredBook != null) {
                debugln { "Restoring book ${restoredBook.id}" }
                loadBookData(restoredBook)

                // Restaurer la ligne sélectionnée
                stateManager.state.value.content.selectedLine?.let { line ->
                    commentariesUseCase.reapplySelectedCommentators(line)
                    commentariesUseCase.reapplySelectedLinkSources(line)
                }
            } else {
                // Charger depuis les paramètres
                savedStateHandle.get<Long>(StateKeys.BOOK_ID)?.let { bookId ->
                    loadBookById(bookId, savedStateHandle.get<Long>(StateKeys.LINE_ID))
                }
            }

            // Observer le livre sélectionné pour mettre à jour le titre
            stateManager.state
                .map { it.navigation.selectedBook }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { book ->
                    titleUpdateManager.updateTabTitle(currentTabId, book.title, TabType.BOOK)
                }
        }
    }

    /**
     * Gestion des événements
     */
    fun onEvent(event: BookContentEvent) {
        viewModelScope.launch {
            when (event) {
                // Navigation
                is BookContentEvent.CategorySelected ->
                    navigationUseCase.selectCategory(event.category)

                is BookContentEvent.BookSelected ->
                    loadBook(event.book)

                is BookContentEvent.BookSelectedInNewTab ->
                    openBookInNewTab(event.book)

                is BookContentEvent.SearchTextChanged ->
                    navigationUseCase.updateSearchText(event.text)

                BookContentEvent.ToggleBookTree ->
                    navigationUseCase.toggleBookTree()

                is BookContentEvent.BookTreeScrolled ->
                    navigationUseCase.updateBookTreeScrollPosition(event.index, event.offset)

                // TOC
                is BookContentEvent.TocEntryExpanded ->
                    tocUseCase.toggleTocEntry(event.entry)

                BookContentEvent.ToggleToc ->
                    tocUseCase.toggleToc()

                is BookContentEvent.TocScrolled ->
                    tocUseCase.updateTocScrollPosition(event.index, event.offset)

                // Content
                is BookContentEvent.LineSelected ->
                    selectLine(event.line)

                is BookContentEvent.LoadAndSelectLine ->
                    loadAndSelectLine(event.lineId)

                BookContentEvent.NavigateToPreviousLine ->
                    contentUseCase.navigateToPreviousLine()

                BookContentEvent.NavigateToNextLine ->
                    contentUseCase.navigateToNextLine()

                BookContentEvent.ToggleCommentaries ->
                    contentUseCase.toggleCommentaries()

                BookContentEvent.ToggleTargum ->
                    contentUseCase.toggleTargum()

                is BookContentEvent.ContentScrolled ->
                    contentUseCase.updateContentScrollPosition(
                        event.anchorId, event.anchorIndex, event.scrollIndex, event.scrollOffset
                    )

                is BookContentEvent.ParagraphScrolled ->
                    contentUseCase.updateParagraphScrollPosition(event.position)

                is BookContentEvent.ChapterScrolled ->
                    contentUseCase.updateChapterScrollPosition(event.position)

                is BookContentEvent.ChapterSelected ->
                    contentUseCase.selectChapter(event.index)

                is BookContentEvent.OpenCommentaryTarget ->
                    event.lineId?.let { openCommentaryTarget(event.bookId, it) }

                // Commentaries
                is BookContentEvent.CommentariesTabSelected ->
                    commentariesUseCase.updateCommentariesTab(event.index)

                is BookContentEvent.CommentariesScrolled ->
                    commentariesUseCase.updateCommentariesScrollPosition(event.index, event.offset)

                is BookContentEvent.SelectedCommentatorsChanged ->
                    commentariesUseCase.updateSelectedCommentators(event.lineId, event.selectedIds)

                is BookContentEvent.SelectedTargumSourcesChanged ->
                    commentariesUseCase.updateSelectedLinkSources(event.lineId, event.selectedIds)

                // State
                BookContentEvent.SaveState ->
                    stateManager.saveAllStates()
            }
        }
    }

    /**
     * Charge un livre par ID
     */
    private suspend fun loadBookById(bookId: Long, lineId: Long? = null) {
        stateManager.setLoading(true)
        try {
            repository.getBook(bookId)?.let { book ->
                navigationUseCase.selectBook(book)

                if (lineId != null) {
                    stateManager.updateContent {
                        copy(
                            anchorId = lineId,
                            scrollIndex = 0,
                            scrollOffset = 0
                        )
                    }
                    loadBookData(book, lineId)

                    repository.getLine(lineId)?.let { line ->
                        selectLine(line)
                        stateManager.updateContent {
                            copy(scrollToLineTimestamp = System.currentTimeMillis())
                        }
                    }
                } else {
                    loadBook(book)
                }
            }
        } finally {
            stateManager.setLoading(false)
            System.gc()
        }
    }

    /**
     * Charge un livre
     */
    private fun loadBook(book: Book) {
        val previousBook = stateManager.state.value.navigation.selectedBook

        navigationUseCase.selectBook(book)

        // Réinitialiser les positions et les sélections si on change de livre
        if (previousBook?.id != book.id) {
            debugln { "Loading new book, resetting positions and selections" }
            contentUseCase.resetScrollPositions()
            tocUseCase.resetToc()

            // Réinitialiser les sélections de commentateurs et de targum/links et la ligne sélectionnée
            stateManager.resetForNewBook()

            // Cacher les commentaires lors du changement de livre
            if (stateManager.state.value.content.showCommentaries) {
                contentUseCase.toggleCommentaries()
            }
            // Cacher le targum lors du changement de livre
            if (stateManager.state.value.content.showTargum) {
                contentUseCase.toggleTargum()
            }
            System.gc()
        }

        loadBookData(book)
    }

    /**
     * Charge les données du livre
     */
    private fun loadBookData(
        book: Book,
        forceAnchorId: Long? = null
    ) {
        viewModelScope.launch {
            stateManager.setLoading(true)
            try {
                val state = stateManager.state.value
                val shouldUseAnchor = state.content.anchorId != -1L &&
                        state.content.scrollIndex > 50 // INITIAL_LOAD_SIZE

                val initialLineId = when {
                    forceAnchorId != null -> forceAnchorId
                    shouldUseAnchor -> state.content.anchorId
                    state.content.selectedLine != null -> state.content.selectedLine?.id
                    else -> null
                }

                debugln { "Loading book data - initialLineId: $initialLineId" }

                // Créer le nouveau pager pour les lignes
                _linesPagingData.value = contentUseCase.buildLinesPager(book.id, initialLineId)

                // Charger le TOC
                tocUseCase.loadRootToc(book.id)
            } finally {
                stateManager.setLoading(false)
            }
        }
    }

    /**
     * Sélectionne une ligne
     */
    private suspend fun selectLine(line: Line) {
        contentUseCase.selectLine(line)
        commentariesUseCase.reapplySelectedCommentators(line)
        commentariesUseCase.reapplySelectedLinkSources(line)
    }

    /**
     * Charge et sélectionne une ligne
     */
    private suspend fun loadAndSelectLine(lineId: Long) {
        val book = stateManager.state.value.navigation.selectedBook ?: return

        contentUseCase.loadAndSelectLine(lineId)?.let { line ->
            if (line.bookId == book.id) {
                // Recréer le pager centré sur la ligne
                _linesPagingData.value = contentUseCase.buildLinesPager(book.id, line.id)

                commentariesUseCase.reapplySelectedCommentators(line)
                commentariesUseCase.reapplySelectedLinkSources(line)
            }
        }
    }

    /**
     * Ouvre un livre dans un nouvel onglet
     */
    private suspend fun openBookInNewTab(book: Book) {
        val newTabId = java.util.UUID.randomUUID().toString()

        // Copier l'état de navigation vers le nouvel onglet
        stateManager.copyNavigationState(currentTabId, newTabId, tabStateManager)

        navigator.navigate(
            TabsDestination.BookContent(
                bookId = book.id,
                tabId = newTabId
            )
        )
    }

    /**
     * Ouvre une cible de commentaire
     */
    private suspend fun openCommentaryTarget(bookId: Long, lineId: Long) {
        navigator.navigate(
            TabsDestination.BookContent(
                bookId = bookId,
                tabId = java.util.UUID.randomUUID().toString(),
                lineId = lineId
            )
        )
    }

}

/**
 * Extension pour copier l'état de navigation entre onglets
 */
private fun BookContentStateManager.copyNavigationState(
    fromTabId: String,
    toTabId: String,
    tabStateManager: TabStateManager
) {
    tabStateManager.copyKeys(
        fromTabId = fromTabId,
        toTabId = toTabId,
        keys = listOf(
            StateKeys.EXPANDED_CATEGORIES,
            StateKeys.CATEGORY_CHILDREN,
            StateKeys.BOOKS_IN_CATEGORY,
            StateKeys.BOOK_TREE_SCROLL_INDEX,
            StateKeys.BOOK_TREE_SCROLL_OFFSET,
            StateKeys.SELECTED_CATEGORY,
            StateKeys.SEARCH_TEXT,
            StateKeys.SHOW_BOOK_TREE
        )
    )
}