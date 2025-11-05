package io.github.kdroidfilter.seforimapp.features.bookcontent

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import io.github.kdroidfilter.seforim.tabs.*
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentStateManager
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.Providers
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.CommentariesUseCase
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.ContentUseCase
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.NavigationUseCase
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.TocUseCase
import io.github.kdroidfilter.seforimapp.logger.debugln
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.StateKeys
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi

/** ViewModel simplifié pour l'écran de contenu du livre */
@OptIn(ExperimentalSplitPaneApi::class)
class BookContentViewModel(
    savedStateHandle: SavedStateHandle,
    private val tabStateManager: TabStateManager,
    private val repository: SeforimRepository,
    private val titleUpdateManager: TabTitleUpdateManager,
    private val tabsViewModel: TabsViewModel
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
    val uiState: StateFlow<BookContentState> = stateManager.state
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

    /** Initialisation du ViewModel */
    private fun initialize(savedStateHandle: SavedStateHandle) {
        viewModelScope.launch {
            // Charger les catégories racine
            navigationUseCase.loadRootCategories()

            // Vérifier si on a un livre restauré
            val restoredBook = stateManager.state.value.navigation.selectedBook
            if (restoredBook != null) {
                debugln { "Restoring book ${restoredBook.id}" }
                val requestedLineId = savedStateHandle.get<Long>(StateKeys.LINE_ID)
                if (requestedLineId != null) {
                    loadBookById(restoredBook.id, requestedLineId)
                } else {
                    // Vérifier s'il y a une ligne sélectionnée sauvegardée à restaurer
                    val savedLineId = tabStateManager.getState<Long>(currentTabId, StateKeys.SELECTED_LINE_ID)
                    if (savedLineId != null) {
                        loadBookById(restoredBook.id, savedLineId)
                    } else {
                        loadBookData(restoredBook)
                    }
                }
            } else {
                // Charger depuis les paramètres
                savedStateHandle.get<Long>(StateKeys.BOOK_ID)?.let { bookId ->
                    loadBookById(bookId, savedStateHandle.get<Long>(StateKeys.LINE_ID))
                }
            }

            // Observer le livre sélectionné et le TOC courant pour mettre à jour le titre
            stateManager.state
                .map { state ->
                    val bookTitle = state.navigation.selectedBook?.title.orEmpty()
                    val tocLabel = state.toc.breadcrumbPath.lastOrNull()?.text?.takeIf { it.isNotBlank() }
                    val combined = if (bookTitle.isNotBlank() && tocLabel != null) {
                        "$bookTitle - $tocLabel"
                    } else {
                        bookTitle
                    }
                    combined
                }
                .filter { it.isNotEmpty() }
                .distinctUntilChanged()
                .collect { combined ->
                    titleUpdateManager.updateTabTitle(currentTabId, combined, TabType.BOOK)
                }
        }
    }

    /** Gestion des événements */
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

                BookContentEvent.NavigateToPreviousLine -> {
                    val line = contentUseCase.navigateToPreviousLine()
                    if (line != null) {
                        commentariesUseCase.reapplySelectedCommentators(line)
                        commentariesUseCase.reapplySelectedLinkSources(line)
                    }
                }

                BookContentEvent.NavigateToNextLine -> {
                    val line = contentUseCase.navigateToNextLine()
                    if (line != null) {
                        commentariesUseCase.reapplySelectedCommentators(line)
                        commentariesUseCase.reapplySelectedLinkSources(line)
                    }
                }

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

                is BookContentEvent.CommentatorsListScrolled ->
                    commentariesUseCase.updateCommentatorsListScrollPosition(event.index, event.offset)

                is BookContentEvent.CommentaryColumnScrolled ->
                    commentariesUseCase.updateCommentaryColumnScrollPosition(
                        event.commentatorId,
                        event.index,
                        event.offset
                    )

                is BookContentEvent.SelectedCommentatorsChanged ->
                    commentariesUseCase.updateSelectedCommentators(event.lineId, event.selectedIds)

                BookContentEvent.CommentatorsSelectionLimitExceeded ->
                    stateManager.updateContent(save = false) {
                        copy(maxCommentatorsLimitSignal = System.currentTimeMillis())
                    }

                is BookContentEvent.SelectedTargumSourcesChanged ->
                    commentariesUseCase.updateSelectedLinkSources(event.lineId, event.selectedIds)

                // State
                BookContentEvent.SaveState ->
                    stateManager.saveAllStates()
            }
        }
    }

    /** Charge un livre par ID */
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

    /** Charge un livre */
    private fun loadBook(book: Book) {
        val previousBook = stateManager.state.value.navigation.selectedBook

        navigationUseCase.selectBook(book)

        // Afficher automatiquement le TOC lors de la première sélection d'un livre si caché
        if (previousBook == null && !stateManager.state.value.toc.isVisible) {
            val current = stateManager.state.value
            // Restaurer la position précédente du séparateur TOC
            current.layout.tocSplitState.positionPercentage = current.layout.previousPositions.toc
            stateManager.updateToc {
                copy(isVisible = true)
            }
        }

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

            // Fermer automatiquement le panneau de l'arbre des livres si l'option est activée
            if (AppSettings.getCloseBookTreeOnNewBookSelected()) {
                val isTreeVisible = stateManager.state.value.navigation.isVisible
                if (isTreeVisible) {
                    navigationUseCase.toggleBookTree()
                }
            }

            System.gc()
        }

        loadBookData(book)
    }

    /** Charge les données du livre */
    private fun loadBookData(
        book: Book,
        forceAnchorId: Long? = null
    ) {
        viewModelScope.launch {
            stateManager.setLoading(true)
            try {
                val state = stateManager.state.value
                // Always prefer an explicit anchor when present (e.g., opening from a commentary link)
                val shouldUseAnchor = state.content.anchorId != -1L

                // Resolve initial line anchor if any, otherwise fall back to the first TOC's first line
                // so that opening a book from the category tree selects the first meaningful section.
                val resolvedInitialLineId: Long? = when {
                    forceAnchorId != null -> forceAnchorId
                    shouldUseAnchor -> state.content.anchorId
                    state.content.selectedLine != null -> state.content.selectedLine?.id
                    else -> {
                        // Compute from TOC: take the first root TOC entry (or its first leaf) and
                        // select its first associated line. Fallback to the very first line of the book.
                        runCatching {
                            val root = repository.getBookRootToc(book.id)
                            val first = root.firstOrNull()
                            val targetEntryId = if (first == null) null else findFirstLeafTocId(first)
                                ?: first?.id
                            val fromToc = targetEntryId?.let { id ->
                                repository.getLineIdsForTocEntry(id).firstOrNull()
                            }
                            fromToc ?: repository.getLineByIndex(book.id, 0)?.id
                        }.getOrNull()
                    }
                }

                debugln { "Loading book data - initialLineId: $resolvedInitialLineId" }

                // Build pager centered on the resolved initial line when available
                _linesPagingData.value = contentUseCase.buildLinesPager(book.id, resolvedInitialLineId)

                // Load TOC after pager creation
                tocUseCase.loadRootToc(book.id)

                // If we computed an initial line (i.e., opened from the category tree with no prior anchor),
                // select it to update TOC selection and breadcrumbs, and request a top-anchor alignment.
                if (resolvedInitialLineId != null && !shouldUseAnchor && forceAnchorId == null && state.content.selectedLine == null) {
                    loadAndSelectLine(resolvedInitialLineId)
                }
            } finally {
                stateManager.setLoading(false)
            }
        }
    }

    /**
     * Finds the first leaf TOC entry under the given entry, depth-first.
     */
    private suspend fun findFirstLeafTocId(entry: io.github.kdroidfilter.seforimlibrary.core.models.TocEntry): Long? {
        if (!entry.hasChildren) return entry.id
        val children = runCatching { repository.getTocChildren(entry.id) }.getOrDefault(emptyList())
        val firstChild = children.firstOrNull() ?: return entry.id
        return findFirstLeafTocId(firstChild)
    }

    /** Sélectionne une ligne */
    private suspend fun selectLine(line: Line) {
        contentUseCase.selectLine(line)
        commentariesUseCase.reapplySelectedCommentators(line)
        commentariesUseCase.reapplySelectedLinkSources(line)
    }

    /** Charge et sélectionne une ligne */
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

    /** Ouvre un livre dans un nouvel onglet */
    private suspend fun openBookInNewTab(book: Book) {
        val newTabId = java.util.UUID.randomUUID().toString()

        // Copier l'état de navigation vers le nouvel onglet
        stateManager.copyNavigationState(currentTabId, newTabId, tabStateManager)

        tabsViewModel.openTab(
            TabsDestination.BookContent(
                bookId = book.id,
                tabId = newTabId
            )
        )
    }

    /** Ouvre une cible de commentaire */
    private suspend fun openCommentaryTarget(bookId: Long, lineId: Long) {

        // Create a new tab and pre-initialize it to avoid initial flashing
        val newTabId = java.util.UUID.randomUUID().toString()

        // Preload the Book object so that the screen does not display the Home by default
        repository.getBook(bookId)?.let { book ->
            tabStateManager.saveState(newTabId, StateKeys.SELECTED_BOOK, book)
        }
        // Optional: indicate the initial anchor for a center scroll upon loading
        tabStateManager.saveState(newTabId, StateKeys.CONTENT_ANCHOR_ID, lineId)

        tabsViewModel.openTab(
            TabsDestination.BookContent(
                bookId = bookId,
                tabId = newTabId,
                lineId = lineId
            )
        )
    }

}

/** Extension pour copier l'état de navigation entre onglets */
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
