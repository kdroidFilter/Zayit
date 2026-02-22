@file:OptIn(ExperimentalSplitPaneApi::class)

package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import androidx.paging.Pager
import androidx.paging.PagingData
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentStateManager
import io.github.kdroidfilter.seforimapp.logger.debugln
import io.github.kdroidfilter.seforimapp.pagination.LinesPagingSource
import io.github.kdroidfilter.seforimapp.pagination.PagingDefaults
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.LineSelectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi

/**
 * UseCase pour gérer le contenu du livre et la navigation dans les lignes
 */
class ContentUseCase(
    private val repository: LineSelectionRepository,
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
     * Sélectionne une ligne.
     * Si isModifierPressed est true (Ctrl/Cmd+clic), on toggle la ligne dans la sélection.
     * Sinon, si la ligne est un TOC heading, on sélectionne toutes les lignes de la section.
     */
    suspend fun selectLine(
        line: Line,
        isModifierPressed: Boolean = false,
    ) {
        debugln { "[selectLine] Selecting line with id=${line.id}, index=${line.lineIndex}, modifier=$isModifierPressed" }

        // Si Ctrl/Cmd+clic, toggle la ligne dans la sélection
        if (isModifierPressed) {
            val currentState = stateManager.state.first()
            val currentSelection = currentState.content.selectedLines
            val isAlreadySelected = currentSelection.any { it.id == line.id }

            val newSelection =
                if (isAlreadySelected) {
                    // Retirer la ligne de la sélection
                    currentSelection.filterNot { it.id == line.id }.toSet()
                } else {
                    // Ajouter la ligne à la sélection
                    currentSelection + line
                }

            // La ligne primaire devient la dernière cliquée (si on ajoute) ou la première restante (si on retire)
            val newPrimaryId =
                if (isAlreadySelected) {
                    newSelection.firstOrNull()?.id
                } else {
                    line.id
                }

            stateManager.updateContent {
                copy(
                    selectedLines = newSelection,
                    primarySelectedLineId = newPrimaryId,
                    isTocEntrySelection = false, // Ctrl+click = sélection manuelle
                )
            }
        } else {
            // Vérifier si la ligne est un TOC heading pour sélectionner toute la section
            val headingToc = runSuspendCatching { repository.getHeadingTocEntryByLineId(line.id) }.getOrNull()

            if (headingToc != null) {
                // La ligne est un TOC heading - sélectionner toutes les lignes de la section (max 128)
                val sectionLineIds =
                    runSuspendCatching {
                        repository.getLineIdsForTocEntry(headingToc.id)
                    }.getOrElse { emptyList() }

                // Appliquer la même limite que CommentsForLineOrTocPagingSource (max 128 lignes)
                val maxBatchSize = 128
                val limitedLineIds =
                    if (sectionLineIds.size > maxBatchSize) {
                        // Sliding window centered on the heading line
                        val idx = sectionLineIds.indexOf(line.id).coerceAtLeast(0)
                        val half = maxBatchSize / 2
                        val start = (idx - half).coerceAtLeast(0)
                        val end = (start + maxBatchSize).coerceAtMost(sectionLineIds.size)
                        sectionLineIds.subList(start, end)
                    } else {
                        sectionLineIds
                    }

                // Charger les objets Line pour les IDs limités
                val sectionLines =
                    runSuspendCatching {
                        limitedLineIds.mapNotNull { id -> repository.getLine(id) }.toSet()
                    }.getOrElse { setOf(line) }

                stateManager.updateContent {
                    copy(
                        selectedLines = sectionLines,
                        primarySelectedLineId = line.id,
                        isTocEntrySelection = true, // TOC entry = afficher seulement targum par défaut
                    )
                }
            } else {
                // Ligne normale - sélection simple
                stateManager.updateContent {
                    copy(
                        selectedLines = setOf(line),
                        primarySelectedLineId = line.id,
                        isTocEntrySelection = false,
                    )
                }
            }
        }

        // Update selected TOC entry for highlighting in TOC
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
                breadcrumbPath = tocPath,
            )
        }
    }

    /**
     * Charge et sélectionne une ligne spécifique.
     * Si la ligne est un TOC heading, sélectionne toutes les lignes de la section.
     */
    suspend fun loadAndSelectLine(lineId: Long): Line? {
        val line = repository.getLine(lineId)

        if (line != null) {
            debugln { "[loadAndSelectLine] Loading line $lineId at index ${line.lineIndex}" }

            // Calculate the correct position in the paged items list
            // When target is near the beginning, it won't be at INITIAL_LOAD_SIZE/2
            val halfLoad = PagingDefaults.LINES.INITIAL_LOAD_SIZE / 2
            val computedAnchorIndex = minOf(line.lineIndex, halfLoad)

            // Vérifier si la ligne est un TOC heading pour sélectionner toute la section
            val headingToc = runSuspendCatching { repository.getHeadingTocEntryByLineId(line.id) }.getOrNull()
            val selectedLines: Set<Line> =
                if (headingToc != null) {
                    val sectionLineIds =
                        runSuspendCatching {
                            repository.getLineIdsForTocEntry(headingToc.id)
                        }.getOrElse { emptyList() }

                    // Appliquer la même limite que CommentsForLineOrTocPagingSource (max 128 lignes)
                    val maxBatchSize = 128
                    val limitedLineIds =
                        if (sectionLineIds.size > maxBatchSize) {
                            val idx = sectionLineIds.indexOf(line.id).coerceAtLeast(0)
                            val half = maxBatchSize / 2
                            val start = (idx - half).coerceAtLeast(0)
                            val end = (start + maxBatchSize).coerceAtMost(sectionLineIds.size)
                            sectionLineIds.subList(start, end)
                        } else {
                            sectionLineIds
                        }

                    runSuspendCatching {
                        limitedLineIds.mapNotNull { id -> repository.getLine(id) }.toSet()
                    }.getOrElse { setOf(line) }
                } else {
                    setOf(line)
                }

            stateManager.updateContent {
                copy(
                    selectedLines = selectedLines,
                    primarySelectedLineId = line.id,
                    isTocEntrySelection = headingToc != null,
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
        val currentLine = currentState.content.primaryLine ?: return null
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
        val currentLine = currentState.content.primaryLine ?: return null
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
