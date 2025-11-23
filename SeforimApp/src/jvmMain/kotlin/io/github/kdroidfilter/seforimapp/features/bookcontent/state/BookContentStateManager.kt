@file:OptIn(ExperimentalSplitPaneApi::class)

package io.github.kdroidfilter.seforimapp.features.bookcontent.state

import io.github.kdroidfilter.seforim.tabs.TabStateManager
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.SplitPaneState

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
    @OptIn(ExperimentalSplitPaneApi::class)
    private fun loadInitialState(): BookContentState {
        val mainPos = getState<Float>(StateKeys.SPLIT_PANE_POSITION) ?: SplitDefaults.MAIN
        val tocPos = getState<Float>(StateKeys.TOC_SPLIT_PANE_POSITION) ?: SplitDefaults.TOC
        val contentPos = getState<Float>(StateKeys.CONTENT_SPLIT_PANE_POSITION) ?: 0.7f
        val targumPos = getState<Float>(StateKeys.TARGUM_SPLIT_PANE_POSITION) ?: 0.8f

        return BookContentState(
            tabId = tabId,
            navigation = NavigationState(
                expandedCategories = getState(StateKeys.EXPANDED_CATEGORIES) ?: emptySet(),
                categoryChildren = getState(StateKeys.CATEGORY_CHILDREN) ?: emptyMap(),
                booksInCategory = getState(StateKeys.BOOKS_IN_CATEGORY) ?: emptySet(),
                selectedCategory = getState(StateKeys.SELECTED_CATEGORY),
                selectedBook = getState(StateKeys.SELECTED_BOOK),
                searchText = getState(StateKeys.SEARCH_TEXT) ?: "",
                isVisible = getState(StateKeys.SHOW_BOOK_TREE) ?: true,
                scrollIndex = getState(StateKeys.BOOK_TREE_SCROLL_INDEX) ?: 0,
                scrollOffset = getState(StateKeys.BOOK_TREE_SCROLL_OFFSET) ?: 0
            ),
            toc = TocState(
                expandedEntries = getState(StateKeys.EXPANDED_TOC_ENTRIES) ?: emptySet(),
                children = getState(StateKeys.TOC_CHILDREN) ?: emptyMap(),
                isVisible = getState(StateKeys.SHOW_TOC) ?: false,
                scrollIndex = getState(StateKeys.TOC_SCROLL_INDEX) ?: 0,
                scrollOffset = getState(StateKeys.TOC_SCROLL_OFFSET) ?: 0
            ),
            content = ContentState(
                selectedLine = getState(StateKeys.SELECTED_LINE),
                showCommentaries = getState(StateKeys.SHOW_COMMENTARIES) ?: false,
                showTargum = getState(StateKeys.SHOW_TARGUM) ?: false,
                scrollIndex = getState(StateKeys.CONTENT_SCROLL_INDEX) ?: 0,
                scrollOffset = getState(StateKeys.CONTENT_SCROLL_OFFSET) ?: 0,
                anchorId = getState(StateKeys.CONTENT_ANCHOR_ID) ?: -1L,
                anchorIndex = getState(StateKeys.CONTENT_ANCHOR_INDEX) ?: 0,
                paragraphScrollPosition = getState(StateKeys.PARAGRAPH_SCROLL_POSITION) ?: 0,
                chapterScrollPosition = getState(StateKeys.CHAPTER_SCROLL_POSITION) ?: 0,
                selectedChapter = getState(StateKeys.SELECTED_CHAPTER) ?: 0,
                commentariesSelectedTab = getState(StateKeys.COMMENTARIES_SELECTED_TAB) ?: 0,
                commentariesScrollIndex = getState(StateKeys.COMMENTARIES_SCROLL_INDEX) ?: 0,
                commentariesScrollOffset = getState(StateKeys.COMMENTARIES_SCROLL_OFFSET) ?: 0,
                commentatorsListScrollIndex = getState(StateKeys.COMMENTATORS_LIST_SCROLL_INDEX) ?: 0,
                commentatorsListScrollOffset = getState(StateKeys.COMMENTATORS_LIST_SCROLL_OFFSET) ?: 0,
                commentariesColumnScrollIndexByCommentator = getState(StateKeys.COMMENTARIES_COLUMN_SCROLL_INDEX_BY_COMMENTATOR) ?: emptyMap(),
                commentariesColumnScrollOffsetByCommentator = getState(StateKeys.COMMENTARIES_COLUMN_SCROLL_OFFSET_BY_COMMENTATOR) ?: emptyMap(),
                selectedCommentatorsByLine = getState(StateKeys.SELECTED_COMMENTATORS_BY_LINE) ?: emptyMap(),
                selectedCommentatorsByBook = getState(StateKeys.SELECTED_COMMENTATORS_BY_BOOK) ?: emptyMap(),
                selectedLinkSourcesByLine = getState(StateKeys.SELECTED_TARGUM_SOURCES_BY_LINE) ?: emptyMap(),
                selectedLinkSourcesByBook = getState(StateKeys.SELECTED_TARGUM_SOURCES_BY_BOOK) ?: emptyMap()
            ),
            layout = LayoutState(
                mainSplitState = SplitPaneState(initialPositionPercentage = mainPos, moveEnabled = true),
                tocSplitState = SplitPaneState(initialPositionPercentage = tocPos, moveEnabled = true),
                contentSplitState = SplitPaneState(initialPositionPercentage = contentPos, moveEnabled = true),
                targumSplitState = SplitPaneState(initialPositionPercentage = targumPos, moveEnabled = true),
                previousPositions = PreviousPositions(
                    main = getState(StateKeys.PREVIOUS_MAIN_SPLIT_POSITION) ?: SplitDefaults.MAIN,
                    toc = getState(StateKeys.PREVIOUS_TOC_SPLIT_POSITION) ?: SplitDefaults.TOC,
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
     * Réinitialise les sélections et positions de contenu lors d'un changement de livre
     * (rend le code appelant plus propre)
     */
    fun resetForNewBook() {
        updateContent {
            copy(
                selectedCommentatorsByLine = emptyMap(),
                selectedLinkSourcesByLine = emptyMap(),
                selectedLine = null,
                anchorId = -1L,
                scrollIndex = 0,
                scrollOffset = 0
            )
        }
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
        if (!AppSettings.isRamSaverEnabled()) {
            // Heavy map: skip in RAM saver mode
            saveState(StateKeys.CATEGORY_CHILDREN, currentState.navigation.categoryChildren)
        }
        saveState(StateKeys.BOOKS_IN_CATEGORY, currentState.navigation.booksInCategory)
        currentState.navigation.selectedCategory?.let { saveState(StateKeys.SELECTED_CATEGORY, it) }
        currentState.navigation.selectedBook?.let { saveState(StateKeys.SELECTED_BOOK, it) }
        saveState(StateKeys.SEARCH_TEXT, currentState.navigation.searchText)
        saveState(StateKeys.SHOW_BOOK_TREE, currentState.navigation.isVisible)
        saveState(StateKeys.BOOK_TREE_SCROLL_INDEX, currentState.navigation.scrollIndex)
        saveState(StateKeys.BOOK_TREE_SCROLL_OFFSET, currentState.navigation.scrollOffset)
        
        // TOC
        saveState(StateKeys.EXPANDED_TOC_ENTRIES, currentState.toc.expandedEntries)
        if (!AppSettings.isRamSaverEnabled()) {
            // Heavy map: skip in RAM saver mode
            saveState(StateKeys.TOC_CHILDREN, currentState.toc.children)
        }
        saveState(StateKeys.SHOW_TOC, currentState.toc.isVisible)
        saveState(StateKeys.TOC_SCROLL_INDEX, currentState.toc.scrollIndex)
        saveState(StateKeys.TOC_SCROLL_OFFSET, currentState.toc.scrollOffset)
        
        // Content
        currentState.content.selectedLine?.let { 
            saveState(StateKeys.SELECTED_LINE, it)
            saveState(StateKeys.SELECTED_LINE_ID, it.id)
        }
        saveState(StateKeys.SHOW_COMMENTARIES, currentState.content.showCommentaries)
        saveState(StateKeys.SHOW_TARGUM, currentState.content.showTargum)
        saveState(StateKeys.CONTENT_SCROLL_INDEX, currentState.content.scrollIndex)
        saveState(StateKeys.CONTENT_SCROLL_OFFSET, currentState.content.scrollOffset)
        saveState(StateKeys.CONTENT_ANCHOR_ID, currentState.content.anchorId)
        saveState(StateKeys.CONTENT_ANCHOR_INDEX, currentState.content.anchorIndex)
        saveState(StateKeys.PARAGRAPH_SCROLL_POSITION, currentState.content.paragraphScrollPosition)
        saveState(StateKeys.CHAPTER_SCROLL_POSITION, currentState.content.chapterScrollPosition)
        saveState(StateKeys.SELECTED_CHAPTER, currentState.content.selectedChapter)
        
        // Commentaries
        saveState(StateKeys.COMMENTARIES_SELECTED_TAB, currentState.content.commentariesSelectedTab)
        saveState(StateKeys.COMMENTARIES_SCROLL_INDEX, currentState.content.commentariesScrollIndex)
        saveState(StateKeys.COMMENTARIES_SCROLL_OFFSET, currentState.content.commentariesScrollOffset)
        saveState(StateKeys.COMMENTATORS_LIST_SCROLL_INDEX, currentState.content.commentatorsListScrollIndex)
        saveState(StateKeys.COMMENTATORS_LIST_SCROLL_OFFSET, currentState.content.commentatorsListScrollOffset)
        saveState(StateKeys.COMMENTARIES_COLUMN_SCROLL_INDEX_BY_COMMENTATOR, currentState.content.commentariesColumnScrollIndexByCommentator)
        saveState(StateKeys.COMMENTARIES_COLUMN_SCROLL_OFFSET_BY_COMMENTATOR, currentState.content.commentariesColumnScrollOffsetByCommentator)
        saveState(StateKeys.SELECTED_COMMENTATORS_BY_LINE, currentState.content.selectedCommentatorsByLine)
        saveState(StateKeys.SELECTED_COMMENTATORS_BY_BOOK, currentState.content.selectedCommentatorsByBook)
        saveState(StateKeys.SELECTED_TARGUM_SOURCES_BY_LINE, currentState.content.selectedLinkSourcesByLine)
        saveState(StateKeys.SELECTED_TARGUM_SOURCES_BY_BOOK, currentState.content.selectedLinkSourcesByBook)
        
        // Layout
        saveState(StateKeys.SPLIT_PANE_POSITION, currentState.layout.mainSplitState.positionPercentage)
        saveState(StateKeys.TOC_SPLIT_PANE_POSITION, currentState.layout.tocSplitState.positionPercentage)
        saveState(StateKeys.CONTENT_SPLIT_PANE_POSITION, currentState.layout.contentSplitState.positionPercentage)
        saveState(StateKeys.TARGUM_SPLIT_PANE_POSITION, currentState.layout.targumSplitState.positionPercentage)
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
