@file:OptIn(ExperimentalSplitPaneApi::class)

package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentStateManager
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.TocFilterResult
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi

/**
 * UseCase pour gérer la table des matières (TOC)
 */
class TocUseCase(
    private val repository: SeforimRepository,
    private val stateManager: BookContentStateManager,
    @StructuredScope private val scope: CoroutineScope,
) {
    // The whole TOC of a book, loaded once per search session: shared by the searches of
    // successive keystrokes, which cancel each other but not the load
    private var tocLoad: Pair<Long, Deferred<List<TocEntry>>>? = null
    private var selectingSearchEntry = false
    private val searchRunner =
        PaneSearchRunner<TocFilterResult> { transform ->
            stateManager.updateToc(save = false) { copy(search = search?.let(transform)) }
        }

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

    /** Shows or hides the search bar. */
    fun toggleSearch() {
        stateManager.updateToc(save = false) { copy(search = search.toggled()) }
    }

    fun closeSearch() {
        stateManager.updateToc(save = false) { copy(search = null) }
        tocLoad = null
    }

    /** Filters the TOC of the open book by title. */
    suspend fun search(query: String) {
        // A query flushed by a closing field, or arriving after Escape
        if (stateManager.state.value.toc.search == null) return
        searchRunner.run(query) {
            val bookId = openBookId() ?: return@run null
            val entries = bookToc(bookId).await()
            val result = withContext(Dispatchers.Default) { buildTocFilter(entries, query) }
            // Drop the result if another book was opened meanwhile
            result.takeIf { openBookId() == bookId }
        }
    }

    /** Hides the search bar and reveals the entry [tocId] in the tree. Returns the line to jump to. */
    suspend fun selectSearchEntry(tocId: Long): Long? {
        // Closed or already opening an entry: a double click or a held Enter must not jump twice
        if (stateManager.state.value.toc.search == null || selectingSearchEntry) return null
        selectingSearchEntry = true
        try {
            val entry = runSuspendCatching { repository.getTocEntry(tocId) }.getOrNull() ?: return null
            // Expand first so the regular tree comes back already open on the entry
            expandPathToTocEntry(entry.id)
            closeSearch()
            return entry.lineId
                ?: runSuspendCatching { repository.getLineIdsForTocEntry(entry.id).firstOrNull() }.getOrNull()
        } finally {
            selectingSearchEntry = false
        }
    }

    private fun openBookId(): Long? =
        stateManager.state.value.navigation.selectedBook
            ?.id

    private fun bookToc(bookId: Long): Deferred<List<TocEntry>> =
        tocLoad?.takeIf { it.first == bookId }?.second
            ?: scope
                .async { runSuspendCatching { repository.getBookToc(bookId) }.getOrElse { emptyList() } }
                .also { tocLoad = bookId to it }

    /**
     * Réinitialise le TOC
     */
    fun resetToc() {
        tocLoad = null
        stateManager.updateToc(save = false) {
            copy(
                search = null,
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
        // Single CTE query returns the full ancestor path ordered by level ASC
        val ordered = runSuspendCatching { repository.getAncestorPath(tocId) }.getOrElse { emptyList() }
        if (ordered.isEmpty()) return

        // Ensure children for each ancestor are loaded and mark as expanded
        for (e in ordered) {
            currentCoroutineContext().ensureActive()
            if (e.hasChildren &&
                !stateManager.state
                    .first()
                    .toc.children
                    .containsKey(e.id)
            ) {
                runSuspendCatching { loadTocChildren(e.id) }
            }
            // Expand all ancestors (and optionally the leaf if it has children)
            stateManager.updateToc(save = false) {
                copy(expandedEntries = expandedEntries + e.id)
            }
        }
    }

    /** Convenience: expand path to the TOC entry associated with a line. */
    suspend fun expandPathToLine(lineId: Long) {
        val tocId = runSuspendCatching { repository.getTocEntryIdForLine(lineId) }.getOrNull() ?: return
        expandPathToTocEntry(tocId)
    }
}
