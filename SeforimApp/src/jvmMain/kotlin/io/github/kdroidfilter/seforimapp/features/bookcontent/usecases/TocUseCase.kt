@file:OptIn(ExperimentalSplitPaneApi::class)

package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentStateManager
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi

/**
 * UseCase pour gérer la table des matières (TOC)
 */
class TocUseCase(
    private val repository: SeforimRepository,
    private val stateManager: BookContentStateManager,
) {
    /**
     * Charge les entrées racine du TOC pour un livre
     */
    suspend fun loadRootToc(bookId: Long) {
        val rootToc = repository.getBookRootToc(bookId)

        stateManager.updateToc {
            copy(
                entries = rootToc,
                children = mapOf(-1L to rootToc),
                // Auto-expand la première entrée si elle a des enfants
                expandedEntries =
                    expandedEntries.ifEmpty {
                        rootToc
                            .firstOrNull()
                            ?.takeIf { it.hasChildren }
                            ?.let { setOf(it.id) }
                            ?: emptySet()
                    },
            )
        }

        // Charger les enfants des entrées déjà expandées
        val currentState = stateManager.state.first().toc
        currentState.expandedEntries.forEach { id ->
            if (!currentState.children.containsKey(id)) {
                loadTocChildren(id)
            }
        }
    }

    /**
     * Charge les enfants d'une entrée TOC
     */
    private suspend fun loadTocChildren(entryId: Long) {
        val children = repository.getTocChildren(entryId)
        if (children.isNotEmpty()) {
            stateManager.updateToc {
                copy(children = this.children + (entryId to children))
            }
        }
    }

    /**
     * Expand/collapse une entrée TOC
     */
    suspend fun toggleTocEntry(entry: TocEntry) {
        val currentState = stateManager.state.first().toc
        val isExpanded = currentState.expandedEntries.contains(entry.id)

        if (isExpanded) {
            // Collapse - retirer l'entrée et tous ses descendants
            val descendants = getAllDescendantIds(entry.id, currentState.children)
            stateManager.updateToc {
                copy(
                    expandedEntries = expandedEntries - entry.id - descendants,
                )
            }
        } else {
            // Expand
            stateManager.updateToc {
                copy(expandedEntries = expandedEntries + entry.id)
            }

            // Charger les enfants si nécessaire
            if (entry.hasChildren && !currentState.children.containsKey(entry.id)) {
                loadTocChildren(entry.id)
            }
        }
    }

    /**
     * Récupère tous les IDs descendants d'une entrée
     */
    private fun getAllDescendantIds(
        entryId: Long,
        childrenMap: Map<Long, List<TocEntry>>,
    ): Set<Long> =
        buildSet {
            childrenMap[entryId]?.forEach { child ->
                add(child.id)
                addAll(getAllDescendantIds(child.id, childrenMap))
            }
        }

    /**
     * Toggle la visibilité du TOC
     */
    fun toggleToc(): Boolean {
        val currentState = stateManager.state.value
        val isVisible = currentState.toc.isVisible
        val newPosition: Float

        if (isVisible) {
            // Cacher
            val prev = currentState.layout.tocSplitState.positionPercentage
            stateManager.updateLayout {
                copy(
                    previousPositions =
                        previousPositions.copy(
                            toc = prev,
                        ),
                )
            }
            newPosition = 0f
            currentState.layout.tocSplitState.positionPercentage = newPosition
        } else {
            // Montrer
            newPosition = currentState.layout.previousPositions.toc
            currentState.layout.tocSplitState.positionPercentage = newPosition
        }

        stateManager.updateToc {
            copy(isVisible = !isVisible)
        }

        return !isVisible
    }

    /**
     * Met à jour la position de scroll du TOC
     */
    fun updateTocScrollPosition(
        index: Int,
        offset: Int,
    ) {
        stateManager.updateToc {
            copy(
                scrollIndex = index,
                scrollOffset = offset,
            )
        }
    }

    /**
     * Réinitialise le TOC
     */
    fun resetToc() {
        stateManager.updateToc(save = false) {
            copy(
                entries = emptyList(),
                expandedEntries = emptySet(),
                children = emptyMap(),
                selectedEntryId = null,
                breadcrumbPath = emptyList(),
                scrollIndex = 0,
                scrollOffset = 0,
            )
        }
    }

    /**
     * Expand all parent entries from the given TOC entry to the root so that
     * the branch to this entry is visible in the TOC panel. Children lists
     * are loaded on demand.
     */
    suspend fun expandPathToTocEntry(tocId: Long) {
        // Build path from leaf to root
        val path = mutableListOf<TocEntry>()
        var currentId: Long? = tocId
        var guard = 0
        while (currentId != null && guard++ < 512) {
            val entry = runCatching { repository.getTocEntry(currentId) }.getOrNull() ?: break
            path += entry
            currentId = entry.parentId
        }
        if (path.isEmpty()) return
        val ordered = path.asReversed()
        // Ensure children for each ancestor are loaded and mark as expanded
        for ((idx, e) in ordered.withIndex()) {
            // Skip the last element (leaf) for children loading; expanding its parent is enough
            if (e.hasChildren &&
                !stateManager.state
                    .first()
                    .toc.children
                    .containsKey(e.id)
            ) {
                runCatching { loadTocChildren(e.id) }
            }
            // Expand all ancestors (and optionally the leaf if it has children)
            stateManager.updateToc(save = false) {
                copy(expandedEntries = expandedEntries + e.id)
            }
        }
    }

    /** Convenience: expand path to the TOC entry associated with a line. */
    suspend fun expandPathToLine(lineId: Long) {
        val tocId = runCatching { repository.getTocEntryIdForLine(lineId) }.getOrNull() ?: return
        expandPathToTocEntry(tocId)
    }
}
