package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.usecases

import app.cash.paging.Pager
import app.cash.paging.PagingData
import app.cash.paging.cachedIn
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.pagination.LineCommentsPagingSource
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.pagination.LineTargumPagingSource
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.pagination.PagingDefaults
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.state.BookContentStateManager
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
                LineCommentsPagingSource(repository, lineId, ids)
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
            val commentaries = repository.getCommentariesForLines(listOf(lineId))
                .filter { it.link.connectionType == ConnectionType.COMMENTARY }
            
            // Utiliser LinkedHashMap pour préserver l'ordre
            val map = LinkedHashMap<String, Long>()
            commentaries.forEach { commentary ->
                if (!map.containsKey(commentary.targetBookTitle)) {
                    map[commentary.targetBookTitle] = commentary.link.targetBookId
                }
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    /**
     * Récupère les sources de liens disponibles pour une ligne
     */
    suspend fun getAvailableLinks(lineId: Long): Map<String, Long> {
        return try {
            val links = repository.getCommentariesForLines(listOf(lineId))
                .filter { it.link.connectionType == ConnectionType.TARGUM }
            
            val map = LinkedHashMap<String, Long>()
            links.forEach { link ->
                if (!map.containsKey(link.targetBookTitle)) {
                    map[link.targetBookTitle] = link.link.targetBookId
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
        val currentState = stateManager.state.first().content.commentariesState
        val bookId = stateManager.state.first().navigation.selectedBook?.id ?: return
        
        // Mettre à jour par ligne
        val byLine = currentState.selectedCommentatorsByLine.toMutableMap()
        if (selectedIds.isEmpty()) {
            byLine.remove(lineId)
        } else {
            byLine[lineId] = selectedIds
        }
        
        // Mettre à jour par livre
        val byBook = currentState.selectedCommentatorsByBook.toMutableMap()
        if (selectedIds.isEmpty()) {
            byBook.remove(bookId)
        } else {
            byBook[bookId] = selectedIds
        }
        
        stateManager.updateContent {
            copy(
                commentariesState = commentariesState.copy(
                    selectedCommentatorsByLine = byLine,
                    selectedCommentatorsByBook = byBook
                )
            )
        }
    }
    
    /**
     * Met à jour les sources de liens sélectionnées pour une ligne
     */
    suspend fun updateSelectedLinkSources(lineId: Long, selectedIds: Set<Long>) {
        val currentState = stateManager.state.first().content.commentariesState
        val bookId = stateManager.state.first().navigation.selectedBook?.id ?: return
        
        // Mettre à jour par ligne
        val byLine = currentState.selectedLinkSourcesByLine.toMutableMap()
        if (selectedIds.isEmpty()) {
            byLine.remove(lineId)
        } else {
            byLine[lineId] = selectedIds
        }
        
        // Mettre à jour par livre
        val byBook = currentState.selectedLinkSourcesByBook.toMutableMap()
        if (selectedIds.isEmpty()) {
            byBook.remove(bookId)
        } else {
            byBook[bookId] = selectedIds
        }
        
        stateManager.updateContent {
            copy(
                commentariesState = commentariesState.copy(
                    selectedLinkSourcesByLine = byLine,
                    selectedLinkSourcesByBook = byBook
                )
            )
        }
    }
    
    /**
     * Réapplique les commentateurs sélectionnés pour une nouvelle ligne
     */
    suspend fun reapplySelectedCommentators(line: Line) {
        val currentState = stateManager.state.first()
        val bookId = currentState.navigation.selectedBook?.id ?: line.bookId
        val remembered = currentState.content.commentariesState.selectedCommentatorsByBook[bookId] ?: emptySet()
        
        if (remembered.isEmpty()) return
        
        try {
            val available = getAvailableCommentators(line.id)
            val availableIds = available.values.toSet()
            val intersection = remembered.intersect(availableIds)
            
            if (intersection.isNotEmpty()) {
                updateSelectedCommentators(line.id, intersection)
            }
        } catch (e: Exception) {
            // Ignorer les erreurs silencieusement
        }
    }
    
    /**
     * Réapplique les sources de liens sélectionnées pour une nouvelle ligne
     */
    suspend fun reapplySelectedLinkSources(line: Line) {
        val currentState = stateManager.state.first()
        val bookId = currentState.navigation.selectedBook?.id ?: line.bookId
        val remembered = currentState.content.commentariesState.selectedLinkSourcesByBook[bookId] ?: emptySet()
        
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
                commentariesState = commentariesState.copy(
                    selectedTab = index
                )
            )
        }
    }
    
    /**
     * Met à jour la position de scroll des commentaires
     */
    fun updateCommentariesScrollPosition(index: Int, offset: Int) {
        stateManager.updateContent {
            copy(
                commentariesState = commentariesState.copy(
                    scrollPosition = commentariesState.scrollPosition.copy(
                        index = index,
                        offset = offset
                    )
                )
            )
        }
    }
}