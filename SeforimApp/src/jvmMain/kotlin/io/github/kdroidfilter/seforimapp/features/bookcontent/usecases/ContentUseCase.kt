@file:OptIn(ExperimentalSplitPaneApi::class)

package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import androidx.paging.Pager
import androidx.paging.PagingData
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentStateManager
import io.github.kdroidfilter.seforimapp.logger.debugln
import io.github.kdroidfilter.seforimapp.pagination.LinesPagingSource
import io.github.kdroidfilter.seforimapp.pagination.PagingDefaults
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi

/**
 * UseCase pour gérer le contenu du livre et la navigation dans les lignes
 */
class ContentUseCase(
    private val repository: SeforimRepository,
    private val stateManager: BookContentStateManager,
) {
    /**
     * Construit un Pager pour les lignes du livre
     */
    fun buildLinesPager(
        bookId: Long,
        initialLineId: Long? = null,
    ): Flow<PagingData<Line>> =
        Pager(
            config = PagingDefaults.LINES.config(placeholders = false),
            pagingSourceFactory = {
                LinesPagingSource(repository, bookId, initialLineId)
            },
        ).flow

    /**
     * Sélectionne une ligne (single selection - clears multi-selection)
     * When selecting a line directly, don't highlight any TOC entry.
     */
    suspend fun selectLine(line: Line) {
        debugln { "[selectLine] Selecting line with id=${line.id}, index=${line.lineIndex}" }

        stateManager.updateContent {
            copy(
                selectedLine = line,
                selectedLineIds = emptySet(),
                isTocBasedSelection = false,
            )
        }

        // Update breadcrumb path but don't highlight TOC entry (only line is selected)
        val tocId =
            try {
                repository.getTocEntryIdForLine(line.id)
            } catch (_: Exception) {
                null
            }
        val tocPath = if (tocId != null) buildTocPathToRoot(tocId) else emptyList()
        stateManager.updateToc(save = false) {
            copy(
                selectedEntryId = null, // Don't highlight TOC when line is selected
                selectedEntryIds = emptySet(),
                breadcrumbPath = tocPath,
            )
        }
    }

    /**
     * Toggles a line in multi-selection (Ctrl+Click behavior).
     * - If the line is already selected, removes it from the selection.
     * - If the line is not selected, adds it to the selection.
     * - Always sets selectedLine to the clicked line for backwards compatibility.
     * - If selection came from TOC, clears to single-line mode with just the clicked line.
     */
    suspend fun toggleLineInSelection(line: Line) {
        debugln { "[toggleLineInSelection] Toggling line with id=${line.id}" }

        val currentState = stateManager.state.first()

        // If selection came from TOC, reset to single-line mode with just the clicked line
        if (currentState.content.isTocBasedSelection) {
            debugln { "[toggleLineInSelection] Clearing TOC-based selection" }
            stateManager.updateContent {
                copy(
                    selectedLine = line,
                    selectedLineIds = setOf(line.id),
                    isTocBasedSelection = false,
                )
            }
            // Update TOC highlighting for the single clicked line
            val tocId =
                try {
                    repository.getTocEntryIdForLine(line.id)
                } catch (_: Exception) {
                    null
                }
            val tocPath = if (tocId != null) buildTocPathToRoot(tocId) else emptyList()
            stateManager.updateToc(save = false) {
                copy(
                    selectedEntryId = tocId,
                    selectedEntryIds = tocId?.let { setOf(it) } ?: emptySet(),
                    breadcrumbPath = tocPath,
                )
            }
            return
        }

        val currentSelection = currentState.content.selectedLineIds
        val currentSelectedLine = currentState.content.selectedLine

        val isDeselecting = line.id in currentSelection
        val newSelection: Set<Long>
        val newPrimaryLine: Line

        if (isDeselecting) {
            // Remove from selection
            val remaining = currentSelection - line.id
            if (remaining.isEmpty()) {
                // If nothing left after removal, keep the clicked line as the only selection
                newSelection = setOf(line.id)
                newPrimaryLine = line
            } else {
                newSelection = remaining
                // Keep the current primary line if it's still in selection, otherwise pick the first remaining
                newPrimaryLine =
                    if (currentSelectedLine != null && currentSelectedLine.id in remaining) {
                        currentSelectedLine
                    } else {
                        // Load the first remaining line as the new primary
                        val firstRemainingId = remaining.first()
                        repository.getLine(firstRemainingId) ?: line
                    }
            }
        } else {
            // Add to selection
            // If no multi-selection yet, start with the currently selected line (if any) + new line
            newSelection =
                if (currentSelection.isEmpty() && currentSelectedLine != null) {
                    setOf(currentSelectedLine.id, line.id)
                } else {
                    currentSelection + line.id
                }
            newPrimaryLine = line
        }

        debugln { "[toggleLineInSelection] newSelection=$newSelection, newPrimaryLine=${newPrimaryLine.id}" }

        stateManager.updateContent {
            copy(
                selectedLine = newPrimaryLine,
                selectedLineIds = newSelection,
                isTocBasedSelection = false,
            )
        }

        // Update TOC highlighting for all selected lines
        val tocIds = mutableSetOf<Long>()
        newSelection.forEach { lineId ->
            try {
                repository.getTocEntryIdForLine(lineId)?.let { tocIds.add(it) }
            } catch (_: Exception) {
                // Ignore errors for individual lines
            }
        }
        val primaryTocId = tocIds.firstOrNull()
        val tocPath = if (primaryTocId != null) buildTocPathToRoot(primaryTocId) else emptyList()
        stateManager.updateToc(save = false) {
            copy(
                selectedEntryId = primaryTocId,
                selectedEntryIds = tocIds,
                breadcrumbPath = tocPath,
            )
        }
    }

    /**
     * Charge et sélectionne une ligne spécifique
     */
    suspend fun loadAndSelectLine(lineId: Long): Line? {
        val line = repository.getLine(lineId)

        if (line != null) {
            debugln { "[loadAndSelectLine] Loading line $lineId at index ${line.lineIndex}" }

            // Calculate the correct position in the paged items list
            // When target is near the beginning, it won't be at INITIAL_LOAD_SIZE/2
            val halfLoad = PagingDefaults.LINES.INITIAL_LOAD_SIZE / 2
            val computedAnchorIndex = minOf(line.lineIndex, halfLoad)

            stateManager.updateContent {
                copy(
                    selectedLine = line,
                    // Clear multi-selection when selecting from TOC
                    selectedLineIds = emptySet(),
                    isTocBasedSelection = true,
                    anchorId = line.id,
                    anchorIndex = computedAnchorIndex,
                    // When selection originates from TOC/breadcrumb, force anchoring at top
                    // by resetting scroll position before pager restoration.
                    scrollIndex = computedAnchorIndex,
                    scrollOffset = 0,
                    scrollToLineTimestamp = System.currentTimeMillis(),
                    topAnchorLineId = line.id,
                    topAnchorRequestTimestamp = System.currentTimeMillis(),
                )
            }

            // Update selected TOC entry for highlighting and breadcrumb path
            val tocId =
                try {
                    repository.getTocEntryIdForLine(line.id)
                } catch (_: Exception) {
                    null
                }
            val tocPath = if (tocId != null) buildTocPathToRoot(tocId) else emptyList()
            stateManager.updateToc(save = false) {
                copy(
                    selectedEntryId = tocId,
                    // Clear multi-TOC highlighting when selecting from TOC
                    selectedEntryIds = emptySet(),
                    breadcrumbPath = tocPath,
                )
            }
        }

        return line
    }

    private suspend fun buildTocPathToRoot(startId: Long): List<io.github.kdroidfilter.seforimlibrary.core.models.TocEntry> {
        val path = mutableListOf<io.github.kdroidfilter.seforimlibrary.core.models.TocEntry>()
        var currentId: Long? = startId
        var safety = 0
        while (currentId != null && safety++ < 200) {
            val entry = repository.getTocEntry(currentId)
            if (entry != null) {
                path.add(0, entry)
                currentId = entry.parentId
            } else {
                break
            }
        }
        return path
    }

    /**
     * Navigue vers la ligne précédente
     */
    suspend fun navigateToPreviousLine(): Line? {
        val currentState = stateManager.state.first()
        val currentLine = currentState.content.selectedLine ?: return null
        val currentBook = currentState.navigation.selectedBook ?: return null

        debugln { "[navigateToPreviousLine] Current line index=${currentLine.lineIndex}" }

        // Vérifier qu'on est dans le bon livre
        if (currentLine.bookId != currentBook.id) return null

        // Si on est déjà à la première ligne
        if (currentLine.lineIndex <= 0) return null

        return try {
            val previousLine = repository.getPreviousLine(currentBook.id, currentLine.lineIndex)

            if (previousLine != null) {
                debugln { "[navigateToPreviousLine] Found line at index ${previousLine.lineIndex}" }
                selectLine(previousLine)

                stateManager.updateContent {
                    copy(scrollToLineTimestamp = System.currentTimeMillis())
                }
            }

            previousLine
        } catch (e: Exception) {
            debugln { "[navigateToPreviousLine] Error: ${e.message}" }
            null
        }
    }

    /**
     * Navigue vers la ligne suivante
     */
    suspend fun navigateToNextLine(): Line? {
        val currentState = stateManager.state.first()
        val currentLine = currentState.content.selectedLine ?: return null
        val currentBook = currentState.navigation.selectedBook ?: return null

        debugln { "[navigateToNextLine] Current line index=${currentLine.lineIndex}" }

        // Vérifier qu'on est dans le bon livre
        if (currentLine.bookId != currentBook.id) return null

        return try {
            val nextLine = repository.getNextLine(currentBook.id, currentLine.lineIndex)

            if (nextLine != null) {
                debugln { "[navigateToNextLine] Found line at index ${nextLine.lineIndex}" }
                selectLine(nextLine)

                stateManager.updateContent {
                    copy(scrollToLineTimestamp = System.currentTimeMillis())
                }
            }

            nextLine
        } catch (e: Exception) {
            debugln { "[navigateToNextLine] Error: ${e.message}" }
            null
        }
    }

    /**
     * Met à jour la position de scroll du contenu
     */
    fun updateContentScrollPosition(
        anchorId: Long,
        anchorIndex: Int,
        scrollIndex: Int,
        scrollOffset: Int,
    ) {
        debugln { "Updating scroll: anchor=$anchorId, anchorIndex=$anchorIndex, scrollIndex=$scrollIndex, offset=$scrollOffset" }

        stateManager.updateContent {
            copy(
                anchorId = anchorId,
                anchorIndex = anchorIndex,
                scrollIndex = scrollIndex,
                scrollOffset = scrollOffset,
            )
        }
    }

    /**
     * Toggle l'affichage des commentaires
     */
    fun toggleCommentaries(): Boolean {
        val currentState = stateManager.state.value
        val isVisible = currentState.content.showCommentaries
        val newPosition: Float

        if (isVisible) {
            // Cacher
            val prev = currentState.layout.contentSplitState.positionPercentage
            stateManager.updateLayout {
                copy(
                    previousPositions =
                        previousPositions.copy(
                            content = prev,
                        ),
                )
            }
            // Fully expand the main content when comments are hidden
            newPosition = 1f
            currentState.layout.contentSplitState.positionPercentage = newPosition
        } else {
            // Montrer
            newPosition = currentState.layout.previousPositions.content
            currentState.layout.contentSplitState.positionPercentage = newPosition
        }

        stateManager.updateContent {
            copy(showCommentaries = !isVisible, showSources = if (!isVisible) false else showSources)
        }

        return !isVisible
    }

    fun toggleSources(): Boolean {
        val currentState = stateManager.state.value
        val isVisible = currentState.content.showSources
        val newPosition: Float

        if (isVisible) {
            val prev = currentState.layout.contentSplitState.positionPercentage
            stateManager.updateLayout {
                copy(
                    previousPositions =
                        previousPositions.copy(
                            sources = prev,
                        ),
                )
            }
            newPosition = 1f
            currentState.layout.contentSplitState.positionPercentage = newPosition
        } else {
            newPosition = currentState.layout.previousPositions.sources
            currentState.layout.contentSplitState.positionPercentage = newPosition
        }

        stateManager.updateContent {
            copy(
                showSources = !isVisible,
                showCommentaries = if (!isVisible) false else showCommentaries,
            )
        }

        return !isVisible
    }

    /**
     * Toggle l'affichage des liens/targum
     */
    fun toggleTargum(): Boolean {
        val currentState = stateManager.state.value
        val isVisible = currentState.content.showTargum
        val newPosition: Float

        if (isVisible) {
            // Cacher: d'abord sauvegarder la position actuelle, puis réduire
            val prev = currentState.layout.targumSplitState.positionPercentage
            stateManager.updateLayout {
                copy(
                    previousPositions =
                        previousPositions.copy(
                            links = prev,
                        ),
                )
            }
            // Fully expand the main content when links pane is hidden
            newPosition = 1f
            currentState.layout.targumSplitState.positionPercentage = newPosition
        } else {
            // Montrer: restaurer la dernière position enregistrée
            newPosition = currentState.layout.previousPositions.links
            currentState.layout.targumSplitState.positionPercentage = newPosition
        }

        stateManager.updateContent {
            copy(showTargum = !isVisible)
        }

        return !isVisible
    }

    /**
     * Met à jour les positions de scroll des paragraphes et chapitres
     */
    fun updateParagraphScrollPosition(position: Int) {
        stateManager.updateContent {
            copy(paragraphScrollPosition = position)
        }
    }

    fun updateChapterScrollPosition(position: Int) {
        stateManager.updateContent {
            copy(chapterScrollPosition = position)
        }
    }

    fun selectChapter(index: Int) {
        stateManager.updateContent {
            copy(selectedChapter = index)
        }
    }

    /**
     * Réinitialise les positions de scroll lors du changement de livre
     */
    fun resetScrollPositions() {
        stateManager.updateContent(save = false) {
            copy(
                scrollIndex = 0,
                scrollOffset = 0,
                anchorId = -1L,
                anchorIndex = 0,
                paragraphScrollPosition = 0,
                chapterScrollPosition = 0,
            )
        }
    }
}
