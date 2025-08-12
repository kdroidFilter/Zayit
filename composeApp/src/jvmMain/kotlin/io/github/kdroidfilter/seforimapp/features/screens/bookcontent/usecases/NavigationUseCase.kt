package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.usecases

import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.state.BookContentStateManager
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.flow.first

/**
 * UseCase pour gérer la navigation dans l'arbre des catégories et livres
 */
class NavigationUseCase(
    private val repository: SeforimRepository,
    private val stateManager: BookContentStateManager
) {
    
    /**
     * Charge les catégories racine
     */
    suspend fun loadRootCategories() {
        val rootCategories = repository.getRootCategories()
        
        stateManager.updateNavigation {
            copy(rootCategories = rootCategories)
        }
        
        // Si des catégories étaient déjà expandées, recharger leurs livres
        val expandedCategories = stateManager.state.first().navigation.expandedCategories
        if (expandedCategories.isNotEmpty()) {
            val booksToLoad = mutableSetOf<Book>()
            
            expandedCategories.forEach { categoryId ->
                try {
                    val books = repository.getBooksByCategory(categoryId)
                    if (books.isNotEmpty()) {
                        booksToLoad.addAll(books)
                    }
                } catch (e: Exception) {
                    // Ignorer les erreurs pour les catégories individuelles
                }
            }
            
            if (booksToLoad.isNotEmpty()) {
                stateManager.updateNavigation {
                    copy(booksInCategory = booksInCategory + booksToLoad)
                }
            }
        }
    }
    
    /**
     * Sélectionne une catégorie
     */
    suspend fun selectCategory(category: Category) {
        stateManager.updateNavigation {
            copy(selectedCategory = category)
        }
        expandCategory(category)
    }
    
    /**
     * Expand/collapse une catégorie
     */
    suspend fun expandCategory(category: Category) {
        val currentState = stateManager.state.first().navigation
        val isExpanded = currentState.expandedCategories.contains(category.id)
        
        if (isExpanded) {
            // Collapse
            stateManager.updateNavigation {
                copy(expandedCategories = expandedCategories - category.id)
            }
        } else {
            // Expand - charger les enfants et les livres si nécessaire
            val needsChildrenLoad = !currentState.categoryChildren.containsKey(category.id)
            
            if (needsChildrenLoad) {
                val children = repository.getCategoryChildren(category.id)
                val books = repository.getBooksByCategory(category.id)
                
                stateManager.updateNavigation {
                    copy(
                        expandedCategories = expandedCategories + category.id,
                        categoryChildren = if (children.isNotEmpty()) {
                            categoryChildren + (category.id to children)
                        } else categoryChildren,
                        booksInCategory = if (books.isNotEmpty()) {
                            booksInCategory + books
                        } else booksInCategory
                    )
                }
            } else {
                // Juste toggle l'expansion
                stateManager.updateNavigation {
                    copy(expandedCategories = expandedCategories + category.id)
                }
            }
        }
    }
    
    /**
     * Met à jour le texte de recherche
     */
    fun updateSearchText(text: String) {
        stateManager.updateNavigation {
            copy(searchText = text)
        }
    }
    
    /**
     * Toggle la visibilité de l'arbre de navigation
     */
    fun toggleBookTree(): Float {
        val currentState = stateManager.state.value
        val isVisible = currentState.navigation.isVisible
        val newPosition: Float
        
        if (isVisible) {
            // Cacher - sauvegarder la position actuelle
            newPosition = 0f
            stateManager.updateLayout {
                copy(
                    mainSplitPosition = newPosition,
                    previousPositions = previousPositions.copy(
                        main = currentState.layout.mainSplitPosition
                    )
                )
            }
        } else {
            // Montrer - restaurer la position précédente
            newPosition = currentState.layout.previousPositions.main
            stateManager.updateLayout {
                copy(mainSplitPosition = newPosition)
            }
        }
        
        stateManager.updateNavigation {
            copy(isVisible = !isVisible)
        }
        
        return newPosition
    }
    
    /**
     * Met à jour la position de scroll de l'arbre
     */
    fun updateBookTreeScrollPosition(index: Int, offset: Int) {
        stateManager.updateNavigation {
            copy(
                scrollPosition = scrollPosition.copy(
                    index = index,
                    offset = offset
                )
            )
        }
    }
    
    /**
     * Sélectionne un livre
     */
    fun selectBook(book: Book) {
        stateManager.updateNavigation {
            copy(selectedBook = book)
        }
    }
}