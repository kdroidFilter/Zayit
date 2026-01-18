package io.github.kdroidfilter.seforimapp.framework.di

import com.russhwolf.settings.Settings
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import io.github.kdroidfilter.seforim.navigation.TabNavControllerRegistry
import io.github.kdroidfilter.seforim.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.core.settings.CategoryDisplaySettingsStore
import io.github.kdroidfilter.seforimapp.features.database.update.DatabaseCleanupUseCase
import io.github.kdroidfilter.seforimapp.features.onboarding.data.OnboardingProcessRepository
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeViewModel
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine

/**
 * Metro DI graph: provider functions annotated with @Provides.
 * Singletons are scoped to [AppScope].
 */
@DependencyGraph(AppScope::class)
abstract class AppGraph : ViewModelGraph {

    // Expose strongly-typed graph entries as abstract vals for generated implementation
    // Removed Navigator; use TabsViewModel + TabNavControllerRegistry
    abstract val tabPersistedStateStore: TabPersistedStateStore
    abstract val tabTitleUpdateManager: TabTitleUpdateManager
    abstract val tabNavControllerRegistry: TabNavControllerRegistry
    abstract val settings: Settings
    abstract val categoryDisplaySettingsStore: CategoryDisplaySettingsStore
    abstract val repository: SeforimRepository
    abstract val searchEngine: SearchEngine
    abstract val tabsViewModel: TabsViewModel
    abstract val searchHomeViewModel: SearchHomeViewModel

    abstract val onboardingProcessRepository: OnboardingProcessRepository
    abstract val databaseCleanupUseCase: DatabaseCleanupUseCase

}
