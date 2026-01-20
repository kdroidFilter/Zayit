package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.core.settings.CategoryDisplaySettingsStore
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentStateManager
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.CoroutineScope

/**
 * Factory for creating BookContent use cases.
 *
 * Use cases depend on tab-specific [BookContentStateManager], so they cannot be
 * directly injected as singletons. This factory is injectable and creates use case
 * instances with the appropriate state manager.
 */
@Inject
@SingleIn(AppScope::class)
class BookContentUseCaseFactory(
    private val repository: SeforimRepository,
    private val categoryDisplaySettingsStore: CategoryDisplaySettingsStore,
) {
    /**
     * Creates a [TocUseCase] for managing table of contents.
     */
    fun createTocUseCase(stateManager: BookContentStateManager): TocUseCase = TocUseCase(repository, stateManager)

    /**
     * Creates a [ContentUseCase] for managing book content and line navigation.
     */
    fun createContentUseCase(stateManager: BookContentStateManager): ContentUseCase = ContentUseCase(repository, stateManager)

    /**
     * Creates a [NavigationUseCase] for managing category/book tree navigation.
     */
    fun createNavigationUseCase(stateManager: BookContentStateManager): NavigationUseCase = NavigationUseCase(repository, stateManager)

    /**
     * Creates an [AltTocUseCase] for managing alternative TOC structures.
     */
    fun createAltTocUseCase(stateManager: BookContentStateManager): AltTocUseCase = AltTocUseCase(repository, stateManager)

    /**
     * Creates a [CommentariesUseCase] for managing commentaries and links.
     * Requires a [CoroutineScope] for background operations.
     */
    fun createCommentariesUseCase(
        stateManager: BookContentStateManager,
        scope: CoroutineScope,
    ): CommentariesUseCase = CommentariesUseCase(repository, stateManager, scope)

    /**
     * Creates a [CategoryDisplaySettingsUseCase] for managing category display settings.
     * This use case doesn't depend on tab-specific state.
     */
    fun createCategoryDisplaySettingsUseCase(): CategoryDisplaySettingsUseCase =
        CategoryDisplaySettingsUseCase(repository, categoryDisplaySettingsStore)
}
