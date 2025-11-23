package io.github.kdroidfilter.seforimapp.features.bookcontent

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
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
) : ViewModel() {
    internal val tabId: String = savedStateHandle.get<String>(StateKeys.TAB_ID) ?: ""

    // State Manager centralisé
    private val stateManager = BookContentStateManager(tabId, tabStateManager)

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
            val bookId = state.navigation.selectedBook?.id
            // Prefer per-line selection; if empty, fall back to sticky per-book selection
            val selectedCommentators: Set<Long> = when {
                lineId != null -> {
                    val perLine = state.content.selectedCommentatorsByLine[lineId].orEmpty()
                    perLine.ifEmpty { state.content.selectedCommentatorsByBook[bookId].orEmpty() }
                }
                else -> state.content.selectedCommentatorsByBook[bookId].orEmpty()
            }
            val selectedLinks: Set<Long> = when {
                lineId != null -> {
                    val perLine = state.content.selectedLinkSourcesByLine[lineId].orEmpty()
                    perLine.ifEmpty { state.content.selectedLinkSourcesByBook[bookId].orEmpty() }
                }
                else -> state.content.selectedLinkSourcesByBook[bookId].orEmpty()
            }
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
                val bookId = s.navigation.selectedBook?.id
                val selectedCommentators: Set<Long> = when {
                    lineId != null -> {
                        val perLine = s.content.selectedCommentatorsByLine[lineId].orEmpty()
                        perLine.ifEmpty { s.content.selectedCommentatorsByBook[bookId].orEmpty() }
                    }
                    else -> s.content.selectedCommentatorsByBook[bookId].orEmpty()
                }
                val selectedLinks: Set<Long> = when {
                    lineId != null -> {
                        val perLine = s.content.selectedLinkSourcesByLine[lineId].orEmpty()
                        perLine.ifEmpty { s.content.selectedLinkSourcesByBook[bookId].orEmpty() }
                    }
                    else -> s.content.selectedLinkSourcesByBook[bookId].orEmpty()
                }
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
                    val savedLineId = tabStateManager.getState<Long>(tabId, StateKeys.SELECTED_LINE_ID)
                    if (savedLineId != null) {
                        // Cold boot restoration: don't trigger scroll animation, use saved scroll position
                        loadBookById(restoredBook.id, savedLineId, triggerScroll = false)
                    } else {
                        // Cas Home/Reference: livre choisi sans TOC (pas de lineId). Ouvrir le TOC (type-safe source).
                        val openSource: io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookOpenSource? =
                            tabStateManager.getState(tabId, StateKeys.OPEN_SOURCE)
                        if (openSource == io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookOpenSource.HOME_REFERENCE ||
                            openSource == io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookOpenSource.CATEGORY_TREE_NEW_TAB ||
                            openSource == io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookOpenSource.SEARCH_RESULT ||
                            openSource == io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookOpenSource.COMMENTARY_OR_TARGUM) {
                            ensureTocVisibleOnFirstOpen()
                        }
                        // Reset anchorId BEFORE loadBookData for cold boot restoration
                        // (anchorId centers the pager, but we want to use the saved scrollIndex/Offset instead)
                        stateManager.updateContent(save = false) {
                            copy(anchorId = -1L, anchorIndex = 0)
                        }
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
                    titleUpdateManager.updateTabTitle(tabId, combined, TabType.BOOK)
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

                is BookContentEvent.OpenBookAtLine -> {
                    // If already on the target book, just jump to the line
                    val currentBookId = stateManager.state.value.navigation.selectedBook?.id
                    if (currentBookId == event.bookId) {
                        loadAndSelectLine(event.lineId)
                    } else {
                        // Load the target book and force-anchor to the requested line
                        val book = repository.getBook(event.bookId)
                        if (book != null) {
                            // Select the book in navigation and open TOC on first open
                            navigationUseCase.selectBook(book)
                            ensureTocVisibleOnFirstOpen()
                            loadBookData(book, forceAnchorId = event.lineId)
                        }
                    }
                }

                is BookContentEvent.OpenBookById -> {
                    val book = repository.getBook(event.bookId)
                    if (book != null) {
                        loadBook(book)
                    }
                }

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
    private suspend fun loadBookById(bookId: Long, lineId: Long? = null, triggerScroll: Boolean = true) {
        stateManager.setLoading(true)
        try {
            repository.getBook(bookId)?.let { book ->
                navigationUseCase.selectBook(book)
                // Expand navigation tree up to the selected book's category
                runCatching { navigationUseCase.expandPathToBook(book) }
                // Afficher le TOC pour certaines origines d'ouverture (type-safe)
                val openSource: io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookOpenSource? =
                    tabStateManager.getState(tabId, StateKeys.OPEN_SOURCE)
                if (openSource == io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookOpenSource.HOME_REFERENCE ||
                    openSource == io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookOpenSource.CATEGORY_TREE_NEW_TAB ||
                    openSource == io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookOpenSource.SEARCH_RESULT ||
                    openSource == io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookOpenSource.COMMENTARY_OR_TARGUM) {
                    ensureTocVisibleOnFirstOpen()
                }

                if (lineId != null) {
                    if (triggerScroll) {
                        // Normal navigation: center pager on line and trigger scroll animation
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
                            // Expand TOC to the line's TOC entry so the branch is visible
                            runCatching { tocUseCase.expandPathToLine(line.id) }
                        }
                    } else {
                        // Cold boot restore: don't center pager, use saved scroll position
                        // Reset anchorId BEFORE loadBookData to use scrollIndex/scrollOffset
                        stateManager.updateContent(save = false) {
                            copy(anchorId = -1L, anchorIndex = 0)
                        }
                        loadBookData(book)  // No forceAnchorId to use saved scrollIndex/scrollOffset

                        repository.getLine(lineId)?.let { line ->
                            selectLine(line)
                            // Expand TOC to the line's TOC entry so the branch is visible
                            runCatching { tocUseCase.expandPathToLine(line.id) }
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
     * Ensures the TOC pane is shown when opening the first book in a tab
     * (e.g., from Home/Reference flow), mirroring the behavior of loadBook().
     */
    private fun ensureTocVisibleOnFirstOpen() {
        val current = stateManager.state.value
        if (!current.toc.isVisible) {
            // Restore last known TOC split position and show the pane
            current.layout.tocSplitState.positionPercentage = current.layout.previousPositions.toc
            stateManager.updateToc { copy(isVisible = true) }
        }
    }

    /** Charge un livre */
    private fun loadBook(book: Book) {
        val previousBook = stateManager.state.value.navigation.selectedBook

        navigationUseCase.selectBook(book)
        // Expand navigation tree up to the selected book's category
        viewModelScope.launch { runCatching { navigationUseCase.expandPathToBook(book) } }

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
                    state.content.selectedLine != null -> state.content.selectedLine.id
                    else -> {
                        // Compute from TOC: take the first root TOC entry (or its first leaf) and
                        // select its first associated line. Fallback to the very first line of the book.
                        runCatching {
                            val root = repository.getBookRootToc(book.id)
                            val first = root.firstOrNull()
                            val targetEntryId = if (first == null) null else findFirstLeafTocId(first)
                                ?: first.id
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

                // If we have an explicit forced anchor, always select it to ensure correct scroll/selection.
                // Otherwise, when opening with no prior anchor and no selection, select the computed initial line.
                if (resolvedInitialLineId != null) {
                    if (forceAnchorId != null) {
                        loadAndSelectLine(resolvedInitialLineId)
                        runCatching { tocUseCase.expandPathToLine(resolvedInitialLineId) }
                    } else if (!shouldUseAnchor && state.content.selectedLine == null) {
                        loadAndSelectLine(resolvedInitialLineId)
                        // Expand TOC path to the resolved initial line (first entry/leaf)
                        runCatching { tocUseCase.expandPathToLine(resolvedInitialLineId) }
                    }
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
    private fun openBookInNewTab(book: Book) {
        val newTabId = java.util.UUID.randomUUID().toString()

        // Copier l'état de navigation vers le nouvel onglet
        stateManager.copyNavigationState(tabId, newTabId, tabStateManager)

        // Pré-initialiser le nouvel onglet avec le livre sélectionné pour éviter
        // l'affichage de la page d'accueil avant le chargement.
        tabStateManager.saveState(newTabId, StateKeys.SELECTED_BOOK, book)
        // Indiquer la source d'ouverture pour afficher le TOC automatiquement dans le nouvel onglet
        tabStateManager.saveState(
            newTabId,
            StateKeys.OPEN_SOURCE,
            io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookOpenSource.CATEGORY_TREE_NEW_TAB
        )

        // Naviguer directement vers le contenu du livre dans le nouvel onglet
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

        // Hint BookContent to show TOC when opened from Commentary/Targum panels
        tabStateManager.saveState(
            newTabId,
            StateKeys.OPEN_SOURCE,
            io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookOpenSource.COMMENTARY_OR_TARGUM
        )

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
