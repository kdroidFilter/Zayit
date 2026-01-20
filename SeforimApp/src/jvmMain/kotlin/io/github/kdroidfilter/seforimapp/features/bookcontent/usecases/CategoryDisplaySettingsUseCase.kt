package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import io.github.kdroidfilter.seforimapp.core.settings.CategoryDisplaySettingsStore
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.NavigationState
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.flow.SharedFlow

class CategoryDisplaySettingsUseCase(
    private val repository: SeforimRepository,
    private val settingsStore: CategoryDisplaySettingsStore,
) {
    data class RootCategorySetting(
        val rootCategoryId: Long?,
        val showDiacritics: Boolean,
    )

    val categoryChanges: SharedFlow<Long> = settingsStore.categoryChanges

    suspend fun getShowDiacriticsForCategory(
        categoryId: Long,
        navigation: NavigationState,
    ): RootCategorySetting {
        val rootCategoryId = resolveRootCategoryId(categoryId, navigation)
        val show =
            if (rootCategoryId == null) {
                true
            } else {
                settingsStore.getShowDiacritics(rootCategoryId)
            }
        return RootCategorySetting(rootCategoryId, show)
    }

    suspend fun toggleShowDiacriticsForCategory(
        categoryId: Long,
        navigation: NavigationState,
    ): RootCategorySetting? {
        val rootCategoryId = resolveRootCategoryId(categoryId, navigation) ?: return null
        val show = settingsStore.toggleShowDiacritics(rootCategoryId)
        return RootCategorySetting(rootCategoryId, show)
    }

    suspend fun resolveRootCategoryId(
        categoryId: Long,
        navigation: NavigationState,
    ): Long? {
        if (categoryId <= 0) return null
        val parentIndex = buildParentIndex(navigation)
        if (parentIndex.containsKey(categoryId)) {
            var currentId = categoryId
            var guard = 0
            while (guard++ < 512) {
                val parentId = parentIndex[currentId] ?: return currentId
                currentId = parentId
            }
            return currentId
        }
        return resolveRootCategoryIdFromDb(categoryId)
    }

    private fun buildParentIndex(nav: NavigationState): Map<Long, Long?> {
        val parentById = mutableMapOf<Long, Long?>()
        nav.rootCategories.forEach { parentById[it.id] = it.parentId }
        nav.categoryChildren.values
            .flatten()
            .forEach { parentById[it.id] = it.parentId }
        return parentById
    }

    private suspend fun resolveRootCategoryIdFromDb(categoryId: Long): Long? {
        var currentId: Long? = categoryId
        var guard = 0
        while (currentId != null && guard++ < 512) {
            val category = repository.getCategory(currentId) ?: return null
            val parentId = category.parentId ?: return category.id
            currentId = parentId
        }
        return null
    }
}
