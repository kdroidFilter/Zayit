package io.github.kdroidfilter.seforimapp.features.screens.bookcontent

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.SplitPaneState
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabAwareViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabStateManager

class BookContentViewModel(
    savedStateHandle: SavedStateHandle,
    stateManager: TabStateManager
) : TabAwareViewModel(
    tabId = savedStateHandle.get<String>("tabId") ?: "",
    stateManager = stateManager
) {

    // SplitPane state
    @OptIn(ExperimentalSplitPaneApi::class)
    private val _splitPaneState = MutableStateFlow(
        getState<SplitPaneState>("splitPaneState") ?: SplitPaneState(
            initialPositionPercentage = getState<Float>("splitPanePosition") ?: 0.3f,
            moveEnabled = true,
        )
    )

    @OptIn(ExperimentalSplitPaneApi::class)
    val splitPaneState = _splitPaneState.asStateFlow()

    // Search text state
    private val _searchText = MutableStateFlow(
        getState<String>("searchText") ?: ""
    )
    val searchText = _searchText.asStateFlow()

    // Scroll position state
    private val _paragraphScrollPosition = MutableStateFlow(
        getState<Int>("paragraphScrollPosition") ?: 0
    )
    val paragraphScrollPosition = _paragraphScrollPosition.asStateFlow()

    private val _chapterScrollPosition = MutableStateFlow(
        getState<Int>("chapterScrollPosition") ?: 0
    )

    val chapterScrollPosition = _chapterScrollPosition.asStateFlow()


    // Selected chapter state
    private val _selectedChapter = MutableStateFlow(
        getState<Int>("selectedChapter") ?: 0
    )
    val selectedChapter = _selectedChapter.asStateFlow()

    fun updateSearchText(text: String) {
        _searchText.value = text
        saveState("searchText", text)
    }

    fun updateParagraphScrollPosition(position: Int) {
        _paragraphScrollPosition.value = position
        saveState("paragraphScrollPosition", position)
    }

    fun updateChapterScrollPosition(position: Int) {
        _chapterScrollPosition.value = position
        saveState("chapterScrollPosition", position)
    }

    fun selectChapter(chapter: Int) {
        _selectedChapter.value = chapter
        saveState("selectedChapter", chapter)
    }

    fun onEvent(events: BookContentEvents) {
        when (events) {
            is BookContentEvents.OnUpdateParagraphScrollPosition -> updateParagraphScrollPosition(events.position)
            is BookContentEvents.OnChapterSelected -> selectChapter(events.index)
            is BookContentEvents.OnSearchTextChange -> updateSearchText(events.text)
            BookContentEvents.SaveAllStates -> saveAllStates()
            is BookContentEvents.OnUpdateChapterScrollPosition -> updateChapterScrollPosition(events.position)
        }
    }

    @OptIn(ExperimentalSplitPaneApi::class)
    fun saveAllStates() {
        saveState("splitPaneState", splitPaneState.value)
        saveState("splitPanePosition", splitPaneState.value.positionPercentage)
        saveState("searchText", searchText.value)
        saveState("scrollPosition", paragraphScrollPosition.value)
        saveState("selectedChapter", selectedChapter.value)
    }

    /**
     * Public method to get a state value for a specific key.
     * This is needed because the getState method in TabAwareViewModel is protected.
     */
    fun <T> getStateValue(key: String): T? {
        return getState(key)
    }

    /**
     * Public method to save a state value for a specific key.
     * This is needed because the saveState method in TabAwareViewModel is protected.
     */
    fun saveStateValue(key: String, value: Any) {
        saveState(key, value)
    }
}
