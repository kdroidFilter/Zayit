package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.usecases

import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.state.BookContentStateManager
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.flow.first

/**
 * UseCase pour gérer la table des matières (TOC)
 */
class TocUseCase(
    private val repository: SeforimRepository,
    private val stateManager: BookContentStateManager
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
                expandedEntries = if (expandedEntries.isEmpty()) {
                    rootToc.firstOrNull()
                        ?.takeIf { it.hasChildren }
                        ?.let { setOf(it.id) }
                        ?: emptySet()
                } else expandedEntries
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
                    expandedEntries = expandedEntries - entry.id - descendants
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
        childrenMap: Map<Long, List<TocEntry>>
    ): Set<Long> = buildSet {
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
            newPosition = 0f
            stateManager.updateLayout {
                copy(
                    tocSplitPosition = newPosition,
                    previousPositions = previousPositions.copy(
                        toc = currentState.layout.tocSplitPosition
                    )
                )
            }
        } else {
            // Montrer
            newPosition = currentState.layout.previousPositions.toc
            stateManager.updateLayout {
                copy(tocSplitPosition = newPosition)
            }
        }
        
        stateManager.updateToc {
            copy(isVisible = !isVisible)
        }
        
        return !isVisible
    }
    
    /**
     * Met à jour la position de scroll du TOC
     */
    fun updateTocScrollPosition(index: Int, offset: Int) {
        stateManager.updateToc {
            copy(
                scrollPosition = scrollPosition.copy(
                    index = index,
                    offset = offset
                )
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
                scrollPosition = scrollPosition.copy(index = 0, offset = 0)
            )
        }
    }
}