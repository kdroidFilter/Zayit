@file:OptIn(ExperimentalSplitPaneApi::class)

package io.github.kdroidfilter.seforimapp.features.bookcontent.state

import io.github.kdroidfilter.seforimapp.framework.session.BookContentPersistedState
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
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
    private val persistedStore: TabPersistedStateStore,
) {
    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<BookContentState> = _state.asStateFlow()

    /**
     * Charge l'état initial depuis le store persisté (par tabId)
     */
    @OptIn(ExperimentalSplitPaneApi::class)
    private fun loadInitialState(): BookContentState {
        val persisted: BookContentPersistedState = persistedStore.get(tabId)?.bookContent ?: BookContentPersistedState()

        return BookContentState(
            tabId = tabId,
            navigation =
                NavigationState(
                    expandedCategories = persisted.expandedCategoryIds,
                    categoryChildren = emptyMap(),
                    booksInCategory = emptySet(),
                    selectedCategory = null,
                    selectedBook = null,
                    searchText = persisted.navigationSearchText,
                    isVisible = persisted.isBookTreeVisible,
                    scrollIndex = persisted.bookTreeScrollIndex,
                    scrollOffset = persisted.bookTreeScrollOffset,
                ),
            toc =
                TocState(
                    expandedEntries = persisted.expandedTocEntryIds,
                    children = emptyMap(),
                    selectedEntryId = persisted.selectedTocEntryId.takeIf { it > 0 },
                    selectedEntryIds = persisted.selectedTocEntryIds,
                    isVisible = persisted.isTocVisible,
                    scrollIndex = persisted.tocScrollIndex,
                    scrollOffset = persisted.tocScrollOffset,
                ),
            altToc = AltTocState(),
            content =
                ContentState(
                    selectedLine = null,
                    selectedLineIds = persisted.selectedLineIds,
                    isTocBasedSelection = persisted.isTocBasedSelection,
                    showCommentaries = persisted.showCommentaries,
                    showTargum = persisted.showTargum,
                    showSources = persisted.showSources,
                    scrollIndex = persisted.contentScrollIndex,
                    scrollOffset = persisted.contentScrollOffset,
                    anchorId = persisted.contentAnchorLineId,
                    anchorIndex = persisted.contentAnchorIndex,
                    paragraphScrollPosition = persisted.paragraphScrollPosition,
                    chapterScrollPosition = persisted.chapterScrollPosition,
                    selectedChapter = persisted.selectedChapter,
                    commentariesSelectedTab = persisted.commentariesSelectedTab,
                    commentariesScrollIndex = persisted.commentariesScrollIndex,
                    commentariesScrollOffset = persisted.commentariesScrollOffset,
                    commentatorsListScrollIndex = persisted.commentatorsListScrollIndex,
                    commentatorsListScrollOffset = persisted.commentatorsListScrollOffset,
                    commentariesColumnScrollIndexByCommentator = persisted.commentariesColumnScrollIndexByCommentator,
                    commentariesColumnScrollOffsetByCommentator = persisted.commentariesColumnScrollOffsetByCommentator,
                    selectedCommentatorsByLine = persisted.selectedCommentatorsByLine,
                    selectedCommentatorsByBook = persisted.selectedCommentatorsByBook,
                    selectedLinkSourcesByLine = persisted.selectedTargumSourcesByLine,
                    selectedLinkSourcesByBook = persisted.selectedTargumSourcesByBook,
                    selectedSourcesByLine = persisted.selectedSourcesByLine,
                    selectedSourcesByBook = persisted.selectedSourcesByBook,
                ),
            layout =
                LayoutState(
                    mainSplitState = SplitPaneState(initialPositionPercentage = persisted.mainSplitPosition, moveEnabled = true),
                    tocSplitState = SplitPaneState(initialPositionPercentage = persisted.tocSplitPosition, moveEnabled = true),
                    contentSplitState = SplitPaneState(initialPositionPercentage = persisted.contentSplitPosition, moveEnabled = true),
                    targumSplitState = SplitPaneState(initialPositionPercentage = persisted.targumSplitPosition, moveEnabled = true),
                    previousPositions =
                        PreviousPositions(
                            main = persisted.previousMainSplitPosition,
                            toc = persisted.previousTocSplitPosition,
                            content = persisted.previousContentSplitPosition,
                            sources = persisted.previousSourcesSplitPosition,
                            links = persisted.previousTargumSplitPosition,
                        ),
                ),
        )
    }

    /**
     * Met à jour l'état et sauvegarde optionnellement
     */
    fun update(
        save: Boolean = true,
        transform: BookContentState.() -> BookContentState,
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
        transform: NavigationState.() -> NavigationState,
    ) {
        update(save) { copy(navigation = navigation.transform()) }
    }

    /**
     * Met à jour uniquement le TOC
     */
    fun updateToc(
        save: Boolean = true,
        transform: TocState.() -> TocState,
    ) {
        update(save) { copy(toc = toc.transform()) }
    }

    /**
     * Met à jour uniquement le contenu
     */
    fun updateContent(
        save: Boolean = true,
        transform: ContentState.() -> ContentState,
    ) {
        update(save) { copy(content = content.transform()) }
    }

    /**
     * Met à jour uniquement l'état des TOC alternatifs
     */
    fun updateAltToc(
        save: Boolean = true,
        transform: AltTocState.() -> AltTocState,
    ) {
        update(save) { copy(altToc = altToc.transform()) }
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
                selectedSourcesByLine = emptyMap(),
                selectedTargumSourceIds = emptySet(),
                selectedSourceIds = emptySet(),
                selectedLine = null,
                selectedLineIds = emptySet(),
                isTocBasedSelection = false,
                anchorId = -1L,
                scrollIndex = 0,
                scrollOffset = 0,
                showSources = false,
            )
        }
        updateToc(save = false) {
            copy(selectedEntryIds = emptySet())
        }
        updateAltToc(save = false) { AltTocState() }
    }

    /**
     * Met à jour uniquement le layout
     */
    fun updateLayout(
        save: Boolean = true,
        transform: LayoutState.() -> LayoutState,
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

        val persisted =
            BookContentPersistedState(
                selectedBookId = currentState.navigation.selectedBook?.id ?: -1L,
                selectedCategoryId = currentState.navigation.selectedCategory?.id ?: -1L,
                expandedCategoryIds = currentState.navigation.expandedCategories,
                navigationSearchText = currentState.navigation.searchText,
                isBookTreeVisible = currentState.navigation.isVisible,
                bookTreeScrollIndex = currentState.navigation.scrollIndex,
                bookTreeScrollOffset = currentState.navigation.scrollOffset,
                isTocVisible = currentState.toc.isVisible,
                expandedTocEntryIds = currentState.toc.expandedEntries,
                selectedTocEntryId = currentState.toc.selectedEntryId ?: -1L,
                selectedTocEntryIds = currentState.toc.selectedEntryIds,
                tocScrollIndex = currentState.toc.scrollIndex,
                tocScrollOffset = currentState.toc.scrollOffset,
                selectedLineId = currentState.content.selectedLine?.id ?: -1L,
                selectedLineIds = currentState.content.selectedLineIds,
                isTocBasedSelection = currentState.content.isTocBasedSelection,
                showCommentaries = currentState.content.showCommentaries,
                showTargum = currentState.content.showTargum,
                showSources = currentState.content.showSources,
                paragraphScrollPosition = currentState.content.paragraphScrollPosition,
                chapterScrollPosition = currentState.content.chapterScrollPosition,
                selectedChapter = currentState.content.selectedChapter,
                contentScrollIndex = currentState.content.scrollIndex,
                contentScrollOffset = currentState.content.scrollOffset,
                contentAnchorLineId = currentState.content.anchorId,
                contentAnchorIndex = currentState.content.anchorIndex,
                commentariesSelectedTab = currentState.content.commentariesSelectedTab,
                commentariesScrollIndex = currentState.content.commentariesScrollIndex,
                commentariesScrollOffset = currentState.content.commentariesScrollOffset,
                commentatorsListScrollIndex = currentState.content.commentatorsListScrollIndex,
                commentatorsListScrollOffset = currentState.content.commentatorsListScrollOffset,
                commentariesColumnScrollIndexByCommentator = currentState.content.commentariesColumnScrollIndexByCommentator,
                commentariesColumnScrollOffsetByCommentator = currentState.content.commentariesColumnScrollOffsetByCommentator,
                selectedCommentatorsByLine = currentState.content.selectedCommentatorsByLine,
                selectedCommentatorsByBook = currentState.content.selectedCommentatorsByBook,
                selectedTargumSourcesByLine = currentState.content.selectedLinkSourcesByLine,
                selectedTargumSourcesByBook = currentState.content.selectedLinkSourcesByBook,
                selectedSourcesByLine = currentState.content.selectedSourcesByLine,
                selectedSourcesByBook = currentState.content.selectedSourcesByBook,
                mainSplitPosition = currentState.layout.mainSplitState.positionPercentage,
                tocSplitPosition = currentState.layout.tocSplitState.positionPercentage,
                contentSplitPosition = currentState.layout.contentSplitState.positionPercentage,
                targumSplitPosition = currentState.layout.targumSplitState.positionPercentage,
                previousMainSplitPosition = currentState.layout.previousPositions.main,
                previousTocSplitPosition = currentState.layout.previousPositions.toc,
                previousContentSplitPosition = currentState.layout.previousPositions.content,
                previousSourcesSplitPosition = currentState.layout.previousPositions.sources,
                previousTargumSplitPosition = currentState.layout.previousPositions.links,
            )

        persistedStore.update(tabId) { current ->
            current.copy(bookContent = persisted)
        }
    }
}
