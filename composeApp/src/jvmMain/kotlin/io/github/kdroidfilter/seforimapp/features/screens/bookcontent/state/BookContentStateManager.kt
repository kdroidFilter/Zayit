package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.state

import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabStateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Gestionnaire d'état centralisé pour BookContent
 */
class BookContentStateManager(
    private val tabId: String,
    private val tabStateManager: TabStateManager
) {
    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<BookContentState> = _state.asStateFlow()
    
    /**
     * Charge l'état initial depuis le TabStateManager
     */
    private fun loadInitialState(): BookContentState {
        return BookContentState(
            navigation = NavigationState(
                expandedCategories = getState(StateKeys.EXPANDED_CATEGORIES) ?: emptySet(),
                categoryChildren = getState(StateKeys.CATEGORY_CHILDREN) ?: emptyMap(),
                booksInCategory = getState(StateKeys.BOOKS_IN_CATEGORY) ?: emptySet(),
                selectedCategory = getState(StateKeys.SELECTED_CATEGORY),
                selectedBook = getState(StateKeys.SELECTED_BOOK),
                searchText = getState(StateKeys.SEARCH_TEXT) ?: "",
                isVisible = getState(StateKeys.SHOW_BOOK_TREE) ?: true,
                scrollPosition = ScrollPosition(
                    index = getState(StateKeys.BOOK_TREE_SCROLL_INDEX) ?: 0,
                    offset = getState(StateKeys.BOOK_TREE_SCROLL_OFFSET) ?: 0
                )
            ),
            toc = TocState(
                expandedEntries = getState(StateKeys.EXPANDED_TOC_ENTRIES) ?: emptySet(),
                children = getState(StateKeys.TOC_CHILDREN) ?: emptyMap(),
                isVisible = getState(StateKeys.SHOW_TOC) ?: true,
                scrollPosition = ScrollPosition(
                    index = getState(StateKeys.TOC_SCROLL_INDEX) ?: 0,
                    offset = getState(StateKeys.TOC_SCROLL_OFFSET) ?: 0
                )
            ),
            content = ContentState(
                selectedLine = getState(StateKeys.SELECTED_LINE),
                showCommentaries = getState(StateKeys.SHOW_COMMENTARIES) ?: false,
                showLinks = getState(StateKeys.SHOW_TARGUM) ?: false,
                scrollPosition = ScrollPosition(
                    index = getState(StateKeys.CONTENT_SCROLL_INDEX) ?: 0,
                    offset = getState(StateKeys.CONTENT_SCROLL_OFFSET) ?: 0
                ),
                anchorId = getState(StateKeys.CONTENT_ANCHOR_ID) ?: -1L,
                anchorIndex = getState(StateKeys.CONTENT_ANCHOR_INDEX) ?: 0,
                paragraphScrollPosition = getState(StateKeys.PARAGRAPH_SCROLL_POSITION) ?: 0,
                chapterScrollPosition = getState(StateKeys.CHAPTER_SCROLL_POSITION) ?: 0,
                selectedChapter = getState(StateKeys.SELECTED_CHAPTER) ?: 0,
                commentariesState = CommentariesState(
                    selectedTab = getState(StateKeys.COMMENTARIES_SELECTED_TAB) ?: 0,
                    scrollPosition = ScrollPosition(
                        index = getState(StateKeys.COMMENTARIES_SCROLL_INDEX) ?: 0,
                        offset = getState(StateKeys.COMMENTARIES_SCROLL_OFFSET) ?: 0
                    ),
                    selectedCommentatorsByLine = getState(StateKeys.SELECTED_COMMENTATORS_BY_LINE) ?: emptyMap(),
                    selectedCommentatorsByBook = getState(StateKeys.SELECTED_COMMENTATORS_BY_BOOK) ?: emptyMap(),
                    selectedLinkSourcesByLine = getState(StateKeys.SELECTED_TARGUM_SOURCES_BY_LINE) ?: emptyMap(),
                    selectedLinkSourcesByBook = getState(StateKeys.SELECTED_TARGUM_SOURCES_BY_BOOK) ?: emptyMap()
                )
            ),
            layout = LayoutState(
                mainSplitPosition = getState(StateKeys.SPLIT_PANE_POSITION) ?: 0.3f,
                tocSplitPosition = getState(StateKeys.TOC_SPLIT_PANE_POSITION) ?: 0.3f,
                contentSplitPosition = getState(StateKeys.CONTENT_SPLIT_PANE_POSITION) ?: 0.7f,
                linksSplitPosition = getState(StateKeys.TARGUM_SPLIT_PANE_POSITION) ?: 0.8f,
                previousPositions = PreviousPositions(
                    main = getState(StateKeys.PREVIOUS_MAIN_SPLIT_POSITION) ?: 0.3f,
                    toc = getState(StateKeys.PREVIOUS_TOC_SPLIT_POSITION) ?: 0.3f,
                    content = getState(StateKeys.PREVIOUS_CONTENT_SPLIT_POSITION) ?: 0.7f,
                    links = getState(StateKeys.PREVIOUS_TARGUM_SPLIT_POSITION) ?: 0.8f
                )
            )
        )
    }
    
    /**
     * Met à jour l'état et sauvegarde optionnellement
     */
    fun update(
        save: Boolean = true,
        transform: BookContentState.() -> BookContentState
    ) {
        _state.update { it.transform() }
        if (save) {
            saveAllStates()
        }
    }
    
    /**
     * Met à jour uniquement la navigation
     */
    fun updateNavigation(
        save: Boolean = true,
        transform: NavigationState.() -> NavigationState
    ) {
        update(save) { copy(navigation = navigation.transform()) }
    }
    
    /**
     * Met à jour uniquement le TOC
     */
    fun updateToc(
        save: Boolean = true,
        transform: TocState.() -> TocState
    ) {
        update(save) { copy(toc = toc.transform()) }
    }
    
    /**
     * Met à jour uniquement le contenu
     */
    fun updateContent(
        save: Boolean = true,
        transform: ContentState.() -> ContentState
    ) {
        update(save) { copy(content = content.transform()) }
    }
    
    /**
     * Met à jour uniquement le layout
     */
    fun updateLayout(
        save: Boolean = true,
        transform: LayoutState.() -> LayoutState
    ) {
        update(save) { copy(layout = layout.transform()) }
    }
    
    /**
     * Met à jour l'état de chargement
     */
    fun setLoading(isLoading: Boolean) {
        _state.update { it.copy(isLoading = isLoading) }
    }
    
    /**
     * Sauvegarde tous les états
     */
    fun saveAllStates() {
        val currentState = _state.value
        
        // Navigation
        saveState(StateKeys.EXPANDED_CATEGORIES, currentState.navigation.expandedCategories)
        saveState(StateKeys.CATEGORY_CHILDREN, currentState.navigation.categoryChildren)
        saveState(StateKeys.BOOKS_IN_CATEGORY, currentState.navigation.booksInCategory)
        currentState.navigation.selectedCategory?.let { saveState(StateKeys.SELECTED_CATEGORY, it) }
        currentState.navigation.selectedBook?.let { saveState(StateKeys.SELECTED_BOOK, it) }
        saveState(StateKeys.SEARCH_TEXT, currentState.navigation.searchText)
        saveState(StateKeys.SHOW_BOOK_TREE, currentState.navigation.isVisible)
        saveState(StateKeys.BOOK_TREE_SCROLL_INDEX, currentState.navigation.scrollPosition.index)
        saveState(StateKeys.BOOK_TREE_SCROLL_OFFSET, currentState.navigation.scrollPosition.offset)
        
        // TOC
        saveState(StateKeys.EXPANDED_TOC_ENTRIES, currentState.toc.expandedEntries)
        saveState(StateKeys.TOC_CHILDREN, currentState.toc.children)
        saveState(StateKeys.SHOW_TOC, currentState.toc.isVisible)
        saveState(StateKeys.TOC_SCROLL_INDEX, currentState.toc.scrollPosition.index)
        saveState(StateKeys.TOC_SCROLL_OFFSET, currentState.toc.scrollPosition.offset)
        
        // Content
        currentState.content.selectedLine?.let { saveState(StateKeys.SELECTED_LINE, it) }
        saveState(StateKeys.SHOW_COMMENTARIES, currentState.content.showCommentaries)
        saveState(StateKeys.SHOW_TARGUM, currentState.content.showLinks)
        saveState(StateKeys.CONTENT_SCROLL_INDEX, currentState.content.scrollPosition.index)
        saveState(StateKeys.CONTENT_SCROLL_OFFSET, currentState.content.scrollPosition.offset)
        saveState(StateKeys.CONTENT_ANCHOR_ID, currentState.content.anchorId)
        saveState(StateKeys.CONTENT_ANCHOR_INDEX, currentState.content.anchorIndex)
        saveState(StateKeys.PARAGRAPH_SCROLL_POSITION, currentState.content.paragraphScrollPosition)
        saveState(StateKeys.CHAPTER_SCROLL_POSITION, currentState.content.chapterScrollPosition)
        saveState(StateKeys.SELECTED_CHAPTER, currentState.content.selectedChapter)
        
        // Commentaries
        val commentaries = currentState.content.commentariesState
        saveState(StateKeys.COMMENTARIES_SELECTED_TAB, commentaries.selectedTab)
        saveState(StateKeys.COMMENTARIES_SCROLL_INDEX, commentaries.scrollPosition.index)
        saveState(StateKeys.COMMENTARIES_SCROLL_OFFSET, commentaries.scrollPosition.offset)
        saveState(StateKeys.SELECTED_COMMENTATORS_BY_LINE, commentaries.selectedCommentatorsByLine)
        saveState(StateKeys.SELECTED_COMMENTATORS_BY_BOOK, commentaries.selectedCommentatorsByBook)
        saveState(StateKeys.SELECTED_TARGUM_SOURCES_BY_LINE, commentaries.selectedLinkSourcesByLine)
        saveState(StateKeys.SELECTED_TARGUM_SOURCES_BY_BOOK, commentaries.selectedLinkSourcesByBook)
        
        // Layout
        saveState(StateKeys.SPLIT_PANE_POSITION, currentState.layout.mainSplitPosition)
        saveState(StateKeys.TOC_SPLIT_PANE_POSITION, currentState.layout.tocSplitPosition)
        saveState(StateKeys.CONTENT_SPLIT_PANE_POSITION, currentState.layout.contentSplitPosition)
        saveState(StateKeys.TARGUM_SPLIT_PANE_POSITION, currentState.layout.linksSplitPosition)
        saveState(StateKeys.PREVIOUS_MAIN_SPLIT_POSITION, currentState.layout.previousPositions.main)
        saveState(StateKeys.PREVIOUS_TOC_SPLIT_POSITION, currentState.layout.previousPositions.toc)
        saveState(StateKeys.PREVIOUS_CONTENT_SPLIT_POSITION, currentState.layout.previousPositions.content)
        saveState(StateKeys.PREVIOUS_TARGUM_SPLIT_POSITION, currentState.layout.previousPositions.links)
    }
    
    private inline fun <reified T> getState(key: String): T? {
        return tabStateManager.getState(tabId, key)
    }
    
    private fun saveState(key: String, value: Any) {
        tabStateManager.saveState(tabId, key, value)
    }
}