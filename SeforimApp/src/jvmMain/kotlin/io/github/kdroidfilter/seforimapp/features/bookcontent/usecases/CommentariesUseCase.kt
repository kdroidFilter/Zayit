package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.cachedIn
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentStateManager
import io.github.kdroidfilter.seforimapp.pagination.CommentsForLineOrTocPagingSource
import io.github.kdroidfilter.seforimapp.pagination.LineCommentsPagingSource
import io.github.kdroidfilter.seforimapp.pagination.LineTargumPagingSource
import io.github.kdroidfilter.seforimapp.pagination.PagingDefaults
import io.github.kdroidfilter.seforimlibrary.core.models.ConnectionType
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * UseCase pour gérer les commentaires et liens
 */
class CommentariesUseCase(
    private val repository: SeforimRepository,
    private val stateManager: BookContentStateManager,
    private val scope: CoroutineScope
) {

    private companion object {
        private const val MAX_COMMENTATORS = 4
    }
    
    /**
     * Construit un Pager pour les commentaires d'une ligne
     */
    fun buildCommentariesPager(
        lineId: Long,
        commentatorId: Long? = null
    ): Flow<PagingData<CommentaryWithText>> {
        val ids = commentatorId?.let { setOf(it) } ?: emptySet()

        return Pager(
            config = PagingDefaults.COMMENTS.config(placeholders = false),
            pagingSourceFactory = {
                CommentsForLineOrTocPagingSource(repository, lineId, ids)
            }
        ).flow.cachedIn(scope)
    }
    
    /**
     * Construit un Pager pour les liens/targum d'une ligne
     */
    fun buildLinksPager(
        lineId: Long,
        sourceBookId: Long? = null
    ): Flow<PagingData<CommentaryWithText>> {
        val ids = sourceBookId?.let { setOf(it) } ?: emptySet()
        
        return Pager(
            config = PagingDefaults.COMMENTS.config(placeholders = false),
            pagingSourceFactory = {
                LineTargumPagingSource(repository, lineId, ids)
            }
        ).flow.cachedIn(scope)
    }
    
    /**
     * Récupère les commentateurs disponibles pour une ligne
     */
    suspend fun getAvailableCommentators(lineId: Long): Map<String, Long> {
        return try {
            val headingToc = repository.getHeadingTocEntryByLineId(lineId)
            val baseIds = if (headingToc != null) {
                repository.getLineIdsForTocEntry(headingToc.id).filter { it != lineId }
            } else listOf(lineId)

            val commentaries = repository.getCommentariesForLines(baseIds)
                .filter { it.link.connectionType == ConnectionType.COMMENTARY }

            val currentBookTitle = stateManager.state.first().navigation.selectedBook?.title?.trim().orEmpty()

            // Map display name -> bookId with sanitization, preserving order
            val map = LinkedHashMap<String, Long>()
            commentaries.forEach { commentary ->
                val raw = commentary.targetBookTitle
                val display = sanitizeCommentatorName(raw, currentBookTitle)
                if (!map.containsKey(display)) {
                    map[display] = commentary.link.targetBookId
                }
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun sanitizeCommentatorName(raw: String, currentBookTitle: String): String {
        if (currentBookTitle.isBlank()) return raw
        val t = currentBookTitle.trim()
        // Remove suffix variants like " על ספר <title>" or " על <title>"
        return raw
            .replace(" על ספר $t", "")
            .replace(" על $t", "")
            .trim()
    }
    
    /**
     * Récupère les sources de liens disponibles pour une ligne
     */
    suspend fun getAvailableLinks(lineId: Long): Map<String, Long> {
        return try {
            val links = repository.getCommentariesForLines(listOf(lineId))
                .filter { it.link.connectionType == ConnectionType.TARGUM }

            val currentBookTitle = stateManager.state.first().navigation.selectedBook?.title?.trim().orEmpty()

            val map = LinkedHashMap<String, Long>()
            links.forEach { link ->
                val raw = link.targetBookTitle
                val display = sanitizeCommentatorName(raw, currentBookTitle)
                if (!map.containsKey(display)) {
                    map[display] = link.link.targetBookId
                }
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    /**
     * Met à jour les commentateurs sélectionnés pour une ligne
     */
    suspend fun updateSelectedCommentators(lineId: Long, selectedIds: Set<Long>) {
        val currentState = stateManager.state.first()
        val currentContent = currentState.content
        val bookId = currentState.navigation.selectedBook?.id ?: return

        val prevLineSelected = currentContent.selectedCommentatorsByLine[lineId] ?: emptySet()
        val oldSticky = currentContent.selectedCommentatorsByBook[bookId] ?: emptySet()

        val additions = selectedIds.minus(prevLineSelected)
        val removals = prevLineSelected.minus(selectedIds)

        val newSticky = oldSticky
            .plus(additions)
            .minus(removals)

        val byLine = currentContent.selectedCommentatorsByLine.toMutableMap().apply {
            if (selectedIds.isEmpty()) remove(lineId) else this[lineId] = selectedIds
        }
        val byBook = currentContent.selectedCommentatorsByBook.toMutableMap().apply {
            if (newSticky.isEmpty()) remove(bookId) else this[bookId] = newSticky
        }

        stateManager.updateContent {
            copy(
                selectedCommentatorsByLine = byLine,
                selectedCommentatorsByBook = byBook
            )
        }
    }
    
    /**
     * Met à jour les sources de liens sélectionnées pour une ligne
     */
    suspend fun updateSelectedLinkSources(lineId: Long, selectedIds: Set<Long>) {
        val currentContent = stateManager.state.first().content
        val bookId = stateManager.state.first().navigation.selectedBook?.id ?: return
        
        // Mettre à jour par ligne
        val byLine = currentContent.selectedLinkSourcesByLine.toMutableMap()
        if (selectedIds.isEmpty()) {
            byLine.remove(lineId)
        } else {
            byLine[lineId] = selectedIds
        }
        
        // Mettre à jour par livre
        val byBook = currentContent.selectedLinkSourcesByBook.toMutableMap()
        if (selectedIds.isEmpty()) {
            byBook.remove(bookId)
        } else {
            byBook[bookId] = selectedIds
        }
        
        stateManager.updateContent {
            copy(
                selectedLinkSourcesByLine = byLine,
                selectedLinkSourcesByBook = byBook
            )
        }
    }
    
    /**
     * Réapplique les commentateurs sélectionnés pour une nouvelle ligne
     */
    suspend fun reapplySelectedCommentators(line: Line) {
        val currentState = stateManager.state.first()
        val bookId = currentState.navigation.selectedBook?.id ?: line.bookId
        val sticky = currentState.content.selectedCommentatorsByBook[bookId] ?: emptySet()

        if (sticky.isEmpty()) return

        try {
            val available = getAvailableCommentators(line.id)
            if (available.isEmpty()) return

            val desired = mutableListOf<Long>()
            for ((_, id) in available) {
                if (id in sticky) desired.add(id)
                if (desired.size >= MAX_COMMENTATORS) break
            }

            if (desired.isNotEmpty()) {
                updateSelectedCommentatorsForLine(line.id, desired.toSet())
            }
        } catch (_: Exception) {
        }
    }

    suspend fun updateSelectedCommentatorsForLine(lineId: Long, selectedIds: Set<Long>) {
        val currentState = stateManager.state.first()
        val byLine = currentState.content.selectedCommentatorsByLine.toMutableMap().apply {
            if (selectedIds.isEmpty()) remove(lineId) else this[lineId] = selectedIds
        }
        stateManager.updateContent {
            copy(selectedCommentatorsByLine = byLine)
        }
    }
    
    /**
     * Réapplique les sources de liens sélectionnées pour une nouvelle ligne
     */
    suspend fun reapplySelectedLinkSources(line: Line) {
        val currentState = stateManager.state.first()
        val bookId = currentState.navigation.selectedBook?.id ?: line.bookId
        val remembered = currentState.content.selectedLinkSourcesByBook[bookId] ?: emptySet()
        
        if (remembered.isEmpty()) return
        
        try {
            val available = getAvailableLinks(line.id)
            val availableIds = available.values.toSet()
            val intersection = remembered.intersect(availableIds)
            
            if (intersection.isNotEmpty()) {
                updateSelectedLinkSources(line.id, intersection)
            }
        } catch (e: Exception) {
            // Ignorer les erreurs silencieusement
        }
    }
    
    /**
     * Met à jour l'onglet sélectionné des commentaires
     */
    fun updateCommentariesTab(index: Int) {
        stateManager.updateContent {
            copy(
                commentariesSelectedTab = index
            )
        }
    }
    
    /**
     * Met à jour la position de scroll des commentaires
     */
    fun updateCommentariesScrollPosition(index: Int, offset: Int) {
        stateManager.updateContent {
            copy(
                commentariesScrollIndex = index,
                commentariesScrollOffset = offset
            )
        }
    }
    
    /**
     * Met à jour la position de scroll de la liste des commentateurs
     */
    fun updateCommentatorsListScrollPosition(index: Int, offset: Int) {
        stateManager.updateContent {
            copy(
                commentatorsListScrollIndex = index,
                commentatorsListScrollOffset = offset
            )
        }
    }
    
    /**
     * Met à jour la position de scroll d'une colonne de commentaires (par commentateur)
     */
    fun updateCommentaryColumnScrollPosition(commentatorId: Long, index: Int, offset: Int) {
        stateManager.updateContent {
            val idxMap = commentariesColumnScrollIndexByCommentator.toMutableMap()
            val offMap = commentariesColumnScrollOffsetByCommentator.toMutableMap()
            idxMap[commentatorId] = index
            offMap[commentatorId] = offset
            copy(
                commentariesColumnScrollIndexByCommentator = idxMap,
                commentariesColumnScrollOffsetByCommentator = offMap
            )
        }
    }
}
