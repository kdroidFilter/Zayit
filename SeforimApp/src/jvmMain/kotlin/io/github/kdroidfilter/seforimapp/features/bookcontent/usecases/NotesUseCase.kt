@file:OptIn(ExperimentalSplitPaneApi::class)

package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentStateManager
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi

/**
 * UseCase for the Notes side pane visibility and scroll state. The notes themselves are owned by
 * [io.github.kdroidfilter.seforimapp.core.annotations.NoteStore]; this only drives the pane layout.
 */
class NotesUseCase(
    private val stateManager: BookContentStateManager,
) {
    /** Toggle the visibility of the Notes pane, mirroring [TocUseCase.toggleToc]. */
    fun toggleNotes(): Boolean {
        val currentState = stateManager.state.value
        val isVisible = currentState.notes.isVisible
        val newPosition: Float

        if (isVisible) {
            // Hide: save the current position first, then collapse.
            val prev = currentState.layout.notesSplitState.positionPercentage
            stateManager.updateLayout {
                copy(previousPositions = previousPositions.copy(notes = prev))
            }
            newPosition = 0f
            currentState.layout.notesSplitState.positionPercentage = newPosition
        } else {
            // Show: restore the last saved position.
            newPosition = currentState.layout.previousPositions.notes
            currentState.layout.notesSplitState.positionPercentage = newPosition
        }

        stateManager.updateNotes {
            copy(isVisible = !isVisible)
        }

        return !isVisible
    }

    /** Ensures the Notes pane is visible (used when the user creates a note). */
    fun showNotes() {
        if (!stateManager.state.value.notes.isVisible) {
            toggleNotes()
        }
    }

    fun updateNotesScrollPosition(
        index: Int,
        offset: Int,
    ) {
        stateManager.updateNotes {
            copy(scrollIndex = index, scrollOffset = offset)
        }
    }
}
