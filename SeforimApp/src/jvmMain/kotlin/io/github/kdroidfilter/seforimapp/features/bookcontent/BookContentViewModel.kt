package io.github.kdroidfilter.seforimapp.features.bookcontent

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactoryKey
import io.github.kdroidfilter.seforim.tabs.*
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentStateManager
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.NavigationState
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.Providers
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.CommentariesUseCase
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.ContentUseCase
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.CategoryDisplaySettingsUseCase
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.NavigationUseCase
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.TocUseCase
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.AltTocUseCase
import io.github.kdroidfilter.seforimapp.logger.debugln
import io.github.kdroidfilter.seforimapp.core.settings.CategoryDisplaySettingsStore
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.StateKeys
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi

/** ViewModel simplifié pour l'écran de contenu du livre */
@OptIn(ExperimentalSplitPaneApi::class)
@AssistedInject
class BookContentViewModel(
    @Assisted savedStateHandle: SavedStateHandle,
    private val persistedStore: TabPersistedStateStore,
    private val repository: SeforimRepository,
    private val categoryDisplaySettingsStore: CategoryDisplaySettingsStore,
    private val titleUpdateManager: TabTitleUpdateManager,
    private val tabsViewModel: TabsViewModel
) : ViewModel() {
    @AssistedFactory
    @ViewModelAssistedFactoryKey(BookContentViewModel::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ViewModelAssistedFactory {
        override fun create(extras: CreationExtras): BookContentViewModel {
            return create(extras.createSavedStateHandle())
        }

        fun create(@Assisted savedStateHandle: SavedStateHandle): BookContentViewModel
    }

    internal val tabId: String = savedStateHandle.get<String>(StateKeys.TAB_ID) ?: ""

    // State Manager centralisé
    private val stateManager = BookContentStateManager(tabId, persistedStore)

    // UseCases
    private val navigationUseCase = NavigationUseCase(repository, stateManager)
    private val contentUseCase = ContentUseCase(repository, stateManager)
    private val tocUseCase = TocUseCase(repository, stateManager)
    private val altTocUseCase = AltTocUseCase(repository, stateManager)
    private val commentariesUseCase = CommentariesUseCase(repository, stateManager, viewModelScope)
    private val categoryDisplaySettingsUseCase =
        CategoryDisplaySettingsUseCase(repository, categoryDisplaySettingsStore)

    // Paging pour les lignes
    private val _linesPagingData = MutableStateFlow<Flow<PagingData<Line>>?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val linesPagingData: Flow<PagingData<Line>> = _linesPagingData
        .filterNotNull()
        .flatMapLatest { it }
        .cachedIn(viewModelScope)

    private val _showDiacritics = MutableStateFlow(true)
    val showDiacritics: StateFlow<Boolean> = _showDiacritics.asStateFlow()
    private var currentRootCategoryId: Long? = null

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
            val selectedSources: Set<Long> = when {
                lineId != null -> {
                    val perLine = state.content.selectedSourcesByLine[lineId].orEmpty()
                    perLine.ifEmpty { state.content.selectedSourcesByBook[bookId].orEmpty() }
                }
                else -> state.content.selectedSourcesByBook[bookId].orEmpty()
            }
            state.copy(
                providers = Providers(
                    linesPagingData = linesPagingData,
                    buildCommentariesPagerFor = commentariesUseCase::buildCommentariesPager,
                    getAvailableCommentatorsForLine = commentariesUseCase::getAvailableCommentators,
                    getCommentatorGroupsForLine = commentariesUseCase::getCommentatorGroups,
                    loadLineConnections = commentariesUseCase::loadLineConnections,
                    buildLinksPagerFor = commentariesUseCase::buildLinksPager,
                    getAvailableLinksForLine = commentariesUseCase::getAvailableLinks,
                    buildSourcesPagerFor = commentariesUseCase::buildSourcesPager,
                    getAvailableSourcesForLine = commentariesUseCase::getAvailableSources
                ),
                content = state.content.copy(
                    selectedCommentatorIds = selectedCommentators,
                    selectedTargumSourceIds = selectedLinks,
                    selectedSourceIds = selectedSources
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
                val selectedSources: Set<Long> = when {
                    lineId != null -> {
                        val perLine = s.content.selectedSourcesByLine[lineId].orEmpty()
                        perLine.ifEmpty { s.content.selectedSourcesByBook[bookId].orEmpty() }
                    }
                    else -> s.content.selectedSourcesByBook[bookId].orEmpty()
                }
                s.copy(
                providers = Providers(
                    linesPagingData = linesPagingData,
                    buildCommentariesPagerFor = commentariesUseCase::buildCommentariesPager,
                    getAvailableCommentatorsForLine = commentariesUseCase::getAvailableCommentators,
                    getCommentatorGroupsForLine = commentariesUseCase::getCommentatorGroups,
                    loadLineConnections = commentariesUseCase::loadLineConnections,
                    buildLinksPagerFor = commentariesUseCase::buildLinksPager,
                    getAvailableLinksForLine = commentariesUseCase::getAvailableLinks,
                    buildSourcesPagerFor = commentariesUseCase::buildSourcesPager,
                    getAvailableSourcesForLine = commentariesUseCase::getAvailableSources
                ),
                    content = s.content.copy(
                        selectedCommentatorIds = selectedCommentators,
                        selectedTargumSourceIds = selectedLinks,
                        selectedSourceIds = selectedSources
                    )
                )
            }
        )

    init {
        initialize(savedStateHandle)
        observeDiacriticsSettings()
    }

    /** Initialisation du ViewModel */
    private fun initialize(savedStateHandle: SavedStateHandle) {
        val persistedBookState = persistedStore.get(tabId)?.bookContent
        val persistedBookId: Long? = persistedBookState?.selectedBookId?.takeIf { it > 0 }
        val argBookId: Long? = savedStateHandle.get<Long>(StateKeys.BOOK_ID)?.takeIf { it > 0 }
        val bookIdToOpen: Long? = argBookId ?: persistedBookId

        debugln {
            "[BookContentViewModel] init tabId=$tabId argBookId=$argBookId persistedBookId=$persistedBookId " +
                "selectedLineId=${persistedBookState?.selectedLineId} " +
                "anchorLineId=${persistedBookState?.contentAnchorLineId} " +
                "scroll=(${persistedBookState?.contentScrollIndex},${persistedBookState?.contentScrollOffset})"
        }

        // Avoid flashing Home before starting an async load when a book is known upfront.
        if (bookIdToOpen != null) {
            stateManager.setLoading(true)
        }

        viewModelScope.launch {
            // Charger les catégories racine
            navigationUseCase.loadRootCategories()

            val requestedLineId: Long? = savedStateHandle.get<Long>(StateKeys.LINE_ID)?.takeIf { it > 0 }
            debugln { "[BookContentViewModel] init tabId=$tabId requestedLineId=$requestedLineId bookIdToOpen=$bookIdToOpen" }
            if (bookIdToOpen != null) {
                // Explicit line navigation wins (e.g., search result / deep link)
                if (requestedLineId != null) {
                    loadBookById(bookIdToOpen, requestedLineId, triggerScroll = true)
                } else {
                    // Restore from persisted state: build the pager around the persisted anchor/selection.
                    // This provides a stable starting window for Paging3 so scroll restoration can be exact.
                    loadBookById(bookIdToOpen, lineId = null, triggerScroll = false)
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

    private fun observeDiacriticsSettings() {
        viewModelScope.launch {
            stateManager.state
                .map { it.navigation }
                .map { nav ->
                    nav to Triple(
                        nav.selectedBook?.id,
                        nav.rootCategories.size,
                        nav.categoryChildren.size
                    )
                }
                .distinctUntilChanged { old, new -> old.second == new.second }
                .collectLatest { (nav, _) ->
                    refreshDiacriticsForNavigation(nav)
                }
        }

        viewModelScope.launch {
            categoryDisplaySettingsUseCase.categoryChanges.collectLatest { categoryId ->
                if (categoryId == currentRootCategoryId) {
                    val nav = stateManager.state.value.navigation
                    _showDiacritics.value = categoryDisplaySettingsUseCase
                        .getShowDiacriticsForCategory(categoryId, nav)
                        .showDiacritics
                }
            }
        }
    }

    private suspend fun refreshDiacriticsForNavigation(nav: NavigationState) {
        val categoryId = nav.selectedBook?.categoryId
        if (categoryId == null || categoryId <= 0) {
            currentRootCategoryId = null
            _showDiacritics.value = true
            return
        }

        val setting = categoryDisplaySettingsUseCase.getShowDiacriticsForCategory(categoryId, nav)
        currentRootCategoryId = setting.rootCategoryId
        _showDiacritics.value = setting.showDiacritics
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

                is BookContentEvent.SearchInDatabase -> {
                    val newTabId = java.util.UUID.randomUUID().toString()
                    tabsViewModel.openTab(
                        TabsDestination.Search(
                            searchQuery = event.query,
                            tabId = newTabId,
                        )
                    )
                }

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

                is BookContentEvent.AltTocEntryExpanded ->
                    altTocUseCase.toggleAltTocEntry(event.entry)

                is BookContentEvent.AltTocScrolled ->
                    altTocUseCase.updateAltTocScrollPosition(event.index, event.offset)

                is BookContentEvent.AltTocStructureSelected ->
                    altTocUseCase.selectStructure(event.structure)

                is BookContentEvent.AltTocEntrySelected -> {
                    val lineId = altTocUseCase.selectAltEntry(event.entry)
                    lineId?.let { loadAndSelectLine(it, syncAltToc = false) }
                }

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
                        val book = repository.getBookCore(event.bookId)
                        if (book != null) {
                            // Select the book in navigation and open TOC on first open
                            navigationUseCase.selectBook(book)
                            ensureTocVisibleOnFirstOpen()
                            loadBookData(book, forceAnchorId = event.lineId)
                            // Fermer automatiquement le panneau de l'arbre des livres si l'option est activée
                            closeBookTreeIfEnabled()
                        }
                    }
                }

                is BookContentEvent.OpenBookById -> {
                    val book = repository.getBookCore(event.bookId)
                    if (book != null) {
                        loadBook(book)
                    }
                }

                BookContentEvent.NavigateToPreviousLine -> {
                    val line = contentUseCase.navigateToPreviousLine()
                    if (line != null) {
                        commentariesUseCase.reapplySelectedCommentators(line)
                        commentariesUseCase.reapplySelectedLinkSources(line)
                        commentariesUseCase.reapplySelectedSources(line)
                    }
                }

                BookContentEvent.NavigateToNextLine -> {
                    val line = contentUseCase.navigateToNextLine()
                    if (line != null) {
                        commentariesUseCase.reapplySelectedCommentators(line)
                        commentariesUseCase.reapplySelectedLinkSources(line)
                        commentariesUseCase.reapplySelectedSources(line)
                    }
                }

                BookContentEvent.ToggleCommentaries ->
                    contentUseCase.toggleCommentaries()

                BookContentEvent.ToggleTargum ->
                    contentUseCase.toggleTargum()

                BookContentEvent.ToggleSources ->
                    contentUseCase.toggleSources()

                BookContentEvent.ToggleDiacritics ->
                    toggleShowDiacriticsForCurrentCategory()

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

                is BookContentEvent.SelectedSourcesChanged ->
                    commentariesUseCase.updateSelectedSources(event.lineId, event.selectedIds)

                // State
                BookContentEvent.SaveState ->
                    stateManager.saveAllStates()
            }
        }
    }

    private suspend fun toggleShowDiacriticsForCurrentCategory() {
        val nav = stateManager.state.value.navigation
        val selectedCategoryId = nav.selectedBook?.categoryId ?: return
        val setting = categoryDisplaySettingsUseCase.toggleShowDiacriticsForCategory(selectedCategoryId, nav) ?: return
        currentRootCategoryId = setting.rootCategoryId
        _showDiacritics.value = setting.showDiacritics
    }

    /** Charge un livre par ID */
    private suspend fun loadBookById(bookId: Long, lineId: Long? = null, triggerScroll: Boolean = true) {
        stateManager.setLoading(true)
        try {
            repository.getBookCore(bookId)?.let { book ->
                val persistedBeforeLoad = persistedStore.get(tabId)?.bookContent
                val isRestore = !triggerScroll

                // During cold-boot restore, avoid persisting intermediate state before the restored
                // selection is rehydrated (otherwise IDs can be overwritten with -1).
                navigationUseCase.selectBook(book, save = !isRestore)
                // Expand navigation tree up to the selected book's category
                runCatching { navigationUseCase.expandPathToBook(book, save = !isRestore) }

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

                        // Fermer automatiquement le panneau de l'arbre des livres si l'option est activée
                        closeBookTreeIfEnabled()
                    } else {
                        // Restore path: keep the persisted scroll anchor and just ensure the book is loaded.
                        loadBookData(book)

                        repository.getLine(lineId)?.let { line ->
                            selectLine(line)
                            // Expand TOC to the line's TOC entry so the branch is visible
                            runCatching { tocUseCase.expandPathToLine(line.id) }
                        }
                    }
                } else {
                    if (triggerScroll) {
                        loadBook(book)
                    } else {
                        // Use the snapshot captured before any background loads start updating state.
                        val persisted = persistedBeforeLoad ?: persistedStore.get(tabId)?.bookContent
                        val shouldEnsureSelectionForPanes = persisted?.let {
                            it.showCommentaries || it.showTargum || it.showSources
                        } == true
                        val lineIdToSelect: Long? = persisted?.selectedLineId?.takeIf { it > 0 }
                            ?: persisted?.contentAnchorLineId?.takeIf { it > 0 && shouldEnsureSelectionForPanes }

                        // Restore path: load the book without resetting persisted scroll/selection.
                        loadBookData(book)

                        if (lineIdToSelect != null) {
                            repository.getLine(lineIdToSelect)?.let { line ->
                                if (line.bookId == book.id) {
                                    selectLine(line)
                                    runCatching { tocUseCase.expandPathToLine(line.id) }
                                }
                            }
                        }
                    }
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

    /**
     * Automatically closes the book tree panel if the setting is enabled.
     * Called when opening a book from search results, toolbar, or links.
     */
    private fun closeBookTreeIfEnabled() {
        if (AppSettings.getCloseBookTreeOnNewBookSelected()) {
            val isTreeVisible = stateManager.state.value.navigation.isVisible
            if (isTreeVisible) {
                navigationUseCase.toggleBookTree()
            }
        }
    }

    /** Charge un livre */
    private suspend fun loadBook(book: Book) {
        val resolvedBook = repository.getBookCore(book.id) ?: book
        val previousBook = stateManager.state.value.navigation.selectedBook

        navigationUseCase.selectBook(resolvedBook)
        // Expand navigation tree up to the selected book's category
        viewModelScope.launch { runCatching { navigationUseCase.expandPathToBook(resolvedBook) } }

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
        if (previousBook?.id != resolvedBook.id) {
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
            closeBookTreeIfEnabled()

            System.gc()
        }

        loadBookData(resolvedBook)
    }

    /** Charge les données du livre */
    private fun loadBookData(
        book: Book,
        forceAnchorId: Long? = null
    ) {
        viewModelScope.launch {
            stateManager.setLoading(true)
            try {
                // Pré-appliquer les commentateurs par défaut pour ce livre (si définis en base)
                runCatching { commentariesUseCase.applyDefaultCommentatorsForBook(book.id) }

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
                altTocUseCase.loadStructures(book)

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
        commentariesUseCase.reapplySelectedSources(line)
    }

    /** Charge et sélectionne une ligne */
    private suspend fun loadAndSelectLine(lineId: Long, syncAltToc: Boolean = true) {
        val book = stateManager.state.value.navigation.selectedBook ?: return

        contentUseCase.loadAndSelectLine(lineId)?.let { line ->
            if (line.bookId == book.id) {
                // Recréer le pager centré sur la ligne
                _linesPagingData.value = contentUseCase.buildLinesPager(book.id, line.id)

                commentariesUseCase.reapplySelectedCommentators(line)
                commentariesUseCase.reapplySelectedLinkSources(line)
                commentariesUseCase.reapplySelectedSources(line)
                // Sync alternative TOC selection if applicable
                if (syncAltToc) {
                    altTocUseCase.selectAltEntryForLine(line.id)
                }
            }
        }
    }

    /** Ouvre un livre dans un nouvel onglet */
    private fun openBookInNewTab(book: Book) {
        val newTabId = java.util.UUID.randomUUID().toString()

        // Copy only lightweight navigation preferences to the new tab to keep tree context.
        val fromNav = persistedStore.get(tabId)?.bookContent
        if (fromNav != null) {
            persistedStore.update(newTabId) { current ->
                current.copy(
                    bookContent = current.bookContent.copy(
                        expandedCategoryIds = fromNav.expandedCategoryIds,
                        selectedCategoryId = fromNav.selectedCategoryId,
                        navigationSearchText = fromNav.navigationSearchText,
                        isBookTreeVisible = fromNav.isBookTreeVisible,
                        bookTreeScrollIndex = fromNav.bookTreeScrollIndex,
                        bookTreeScrollOffset = fromNav.bookTreeScrollOffset,
                        selectedBookId = book.id,
                        // Mimic the previous UX: show TOC on first open in the new tab.
                        isTocVisible = true,
                        // Reset per-book scroll/anchor in the new tab to start clean.
                        selectedLineId = -1L,
                        contentAnchorLineId = -1L,
                        contentAnchorIndex = 0,
                        contentScrollIndex = 0,
                        contentScrollOffset = 0,
                    ),
                    search = null
                )
            }
        } else {
            persistedStore.update(newTabId) { current ->
                current.copy(
                    bookContent = current.bookContent.copy(
                        selectedBookId = book.id,
                        isTocVisible = true
                    ),
                    search = null
                )
            }
        }

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

        // Seed persisted state so the new tab can restore scroll/anchor deterministically.
        persistedStore.update(newTabId) { current ->
            current.copy(
                bookContent = current.bookContent.copy(
                    selectedBookId = bookId,
                    selectedLineId = lineId,
                    contentAnchorLineId = lineId,
                    contentAnchorIndex = 0,
                    contentScrollIndex = 0,
                    contentScrollOffset = 0,
                    isTocVisible = true,
                ),
                search = null
            )
        }

        tabsViewModel.openTab(
            TabsDestination.BookContent(
                bookId = bookId,
                tabId = newTabId,
                lineId = lineId
            )
        )
    }

}
