@file:OptIn(ExperimentalSplitPaneApi::class)

package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import io.github.kdroidfilter.seforimapp.features.bookcontent.state.AltTocState
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentStateManager
import io.github.kdroidfilter.seforimlibrary.core.models.AltTocEntry
import io.github.kdroidfilter.seforimlibrary.core.models.AltTocStructure
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi

class AltTocUseCase(
    private val repository: SeforimRepository,
    private val stateManager: BookContentStateManager,
) {
    suspend fun loadStructures(book: Book?) {
        if (book == null) {
            resetAltToc()
            return
        }
        // If the catalog-provided book lacks the flag, re-read from DB to get the truth.
        val effectiveBook =
            if (!book.hasAltStructures) {
                withContext(Dispatchers.IO) { repository.getBookCore(book.id) } ?: book
            } else {
                book
            }
        if (effectiveBook.hasAltStructures && effectiveBook != book) {
            stateManager.updateNavigation(save = false) { copy(selectedBook = effectiveBook) }
        }
        if (!effectiveBook.hasAltStructures) {
            resetAltToc()
            return
        }

        val structures =
            withContext(Dispatchers.IO) {
                repository.getAltTocStructuresForBook(effectiveBook.id)
            }
        if (structures.isEmpty()) {
            resetAltToc()
            return
        }

        val preferredId = stateManager.state.value.altToc.selectedStructureId
        val targetStructureId =
            structures.firstOrNull { it.id == preferredId }?.id
                ?: pickPreferredStructure(structures).id

        stateManager.updateAltToc {
            AltTocState(
                structures = structures,
                selectedStructureId = targetStructureId,
                entries = emptyList(),
                expandedEntries = emptySet(),
                children = emptyMap(),
                selectedEntryId = null,
                scrollIndex = 0,
                scrollOffset = 0,
            )
        }

        loadRoot(targetStructureId)
    }

    suspend fun loadRoot(structureId: Long) {
        val (root, allEntries) =
            withContext(Dispatchers.IO) {
                repository.getAltRootToc(structureId) to repository.getAltTocEntriesForStructure(structureId)
            }
        val lineHeadings =
            allEntries
                .filter { it.lineId != null }
                .groupBy { it.lineId!! }

        stateManager.updateAltToc {
            copy(
                selectedStructureId = structureId,
                entries = root,
                children = mapOf(-1L to root),
                expandedEntries =
                    expandedEntries.ifEmpty {
                        root.firstOrNull()?.takeIf { it.hasChildren }?.let { setOf(it.id) } ?: emptySet()
                    },
                selectedEntryId = null,
                scrollIndex = 0,
                scrollOffset = 0,
                lineHeadingsByLineId = lineHeadings,
                entriesById = allEntries.associateBy { it.id },
            )
        }

        val currentState = stateManager.state.value.altToc
        currentState.expandedEntries.forEach { id ->
            if (!currentState.children.containsKey(id)) {
                loadChildren(id)
            }
        }
    }

    private fun pickPreferredStructure(structures: List<AltTocStructure>): AltTocStructure {
        fun priority(key: String): Int =
            when (key.lowercase()) {
                "daf" -> 0
                "parasha" -> 1
                "chapters" -> 2
                "topic" -> 3
                "section" -> 4
                else -> 100
            }
        return structures.minByOrNull { priority(it.key) } ?: structures.first()
    }

    private suspend fun loadChildren(parentId: Long) {
        val children = withContext(Dispatchers.IO) { repository.getAltTocChildren(parentId) }
        if (children.isNotEmpty()) {
            stateManager.updateAltToc {
                copy(
                    children = this.children + (parentId to children),
                    entriesById = entriesById + children.associateBy { it.id },
                )
            }
        }
    }

    suspend fun toggleAltTocEntry(entry: AltTocEntry) {
        val currentState = stateManager.state.value.altToc
        val isExpanded = currentState.expandedEntries.contains(entry.id)

        if (isExpanded) {
            val descendants = getAllDescendantIds(entry.id, currentState.children)
            stateManager.updateAltToc {
                copy(expandedEntries = expandedEntries - entry.id - descendants)
            }
        } else {
            stateManager.updateAltToc {
                copy(expandedEntries = expandedEntries + entry.id)
            }

            if (entry.hasChildren && !currentState.children.containsKey(entry.id)) {
                loadChildren(entry.id)
            }
        }
    }

    fun updateAltTocScrollPosition(
        index: Int,
        offset: Int,
    ) {
        stateManager.updateAltToc {
            copy(scrollIndex = index, scrollOffset = offset)
        }
    }

    suspend fun selectStructure(structure: AltTocStructure) {
        loadRoot(structure.id)
    }

    suspend fun selectAltEntry(entry: AltTocEntry): Long? {
        stateManager.updateAltToc {
            copy(selectedEntryId = entry.id)
        }
        val lineIds = withContext(Dispatchers.IO) { repository.getLineIdsForAltTocEntry(entry.id) }
        return entry.lineId ?: lineIds.firstOrNull()
    }

    suspend fun selectAltEntryForLine(lineId: Long) {
        val altState = stateManager.state.value.altToc
        val structureId = altState.selectedStructureId ?: return
        val altEntryId =
            altState.lineHeadingsByLineId[lineId]?.firstOrNull()?.id
                ?: withContext(Dispatchers.IO) {
                    repository.getAltTocEntryIdForLine(lineId, structureId)
                }
                ?: return
        stateManager.updateAltToc {
            copy(selectedEntryId = altEntryId)
        }
        // Expand path so the branch is visible if needed
        expandPathToAltEntry(altEntryId)
    }

    private suspend fun expandPathToAltEntry(entryId: Long) {
        val path = mutableListOf<AltTocEntry>()
        var currentId: Long? = entryId
        var guard = 0
        while (currentId != null && guard++ < 512) {
            val cached = stateManager.state.value.altToc.entriesById[currentId]
            val entry =
                cached ?: withContext(Dispatchers.IO) {
                    runCatching { repository.getAltTocEntry(currentId) }.getOrNull()
                }
            if (entry == null) break
            path += entry
            currentId = entry.parentId
        }
        if (path.isEmpty()) return
        val ordered = path.asReversed()
        for (e in ordered) {
            val altState = stateManager.state.value.altToc
            if (e.hasChildren && !altState.children.containsKey(e.id)) {
                runCatching { loadChildren(e.id) }
            }
            stateManager.updateAltToc(save = false) {
                copy(expandedEntries = expandedEntries + e.id)
            }
        }
    }

    fun resetAltToc() {
        stateManager.updateAltToc(save = false) { AltTocState() }
    }

    private fun getAllDescendantIds(
        entryId: Long,
        childrenMap: Map<Long, List<AltTocEntry>>,
    ): Set<Long> =
        buildSet {
            childrenMap[entryId]?.forEach { child ->
                add(child.id)
                addAll(getAllDescendantIds(child.id, childrenMap))
            }
        }
}
