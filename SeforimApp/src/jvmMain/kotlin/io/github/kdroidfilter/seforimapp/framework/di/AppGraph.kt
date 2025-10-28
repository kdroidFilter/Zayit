package io.github.kdroidfilter.seforimapp.framework.di

import androidx.lifecycle.SavedStateHandle
import com.russhwolf.settings.Settings
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import io.github.kdroidfilter.seforim.navigation.Navigator
import io.github.kdroidfilter.seforim.tabs.TabStateManager
import io.github.kdroidfilter.seforim.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentViewModel
import io.github.kdroidfilter.seforimapp.features.onboarding.diskspace.AvailableDiskSpaceViewModel
import io.github.kdroidfilter.seforimapp.features.onboarding.download.DownloadViewModel
import io.github.kdroidfilter.seforimapp.features.onboarding.extract.ExtractViewModel
import io.github.kdroidfilter.seforimapp.features.onboarding.region.RegionConfigViewModel
import io.github.kdroidfilter.seforimapp.features.onboarding.typeofinstall.TypeOfInstallationViewModel
import io.github.kdroidfilter.seforimapp.features.onboarding.userprofile.UserProfileViewModel
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeViewModel
import io.github.kdroidfilter.seforimapp.features.search.SearchResultViewModel
import io.github.kdroidfilter.seforimapp.features.search.SearchResultsCache
import io.github.kdroidfilter.seforimapp.features.settings.SettingsWindowViewModel
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository

/**
 * Metro DI graph: provider functions annotated with @Provides.
 * Singletons are scoped to [AppScope].
 */
@DependencyGraph(AppScope::class)
abstract class AppGraph {

    // Expose strongly-typed graph entries as abstract vals for generated implementation
    abstract val navigator: Navigator
    abstract val tabStateManager: TabStateManager
    abstract val tabTitleUpdateManager: TabTitleUpdateManager
    abstract val settings: Settings
    abstract val repository: SeforimRepository
    abstract val tabsViewModel: TabsViewModel
    abstract val settingsWindowViewModel: SettingsWindowViewModel
    abstract val generalSettingsViewModel: io.github.kdroidfilter.seforimapp.features.settings.general.GeneralSettingsViewModel
    abstract val fontsSettingsViewModel: io.github.kdroidfilter.seforimapp.features.settings.fonts.FontsSettingsViewModel
    abstract val searchResultsCache: SearchResultsCache
    abstract val searchHomeViewModel: SearchHomeViewModel

    abstract val typeOfInstallationViewModel: TypeOfInstallationViewModel
    abstract val downloadViewModel: DownloadViewModel
    abstract val extractViewModel: ExtractViewModel
    abstract val availableDiskSpaceViewModel: AvailableDiskSpaceViewModel
    abstract val regionConfigViewModel: RegionConfigViewModel
    abstract val userProfileViewModel: UserProfileViewModel

    @Provides
    fun provideBookContentViewModel(
        savedStateHandle: SavedStateHandle,
        tabStateManager: TabStateManager,
        repository: SeforimRepository,
        titleUpdateManager: TabTitleUpdateManager,
        navigator: Navigator,
        settings: Settings
    ): BookContentViewModel = BookContentViewModel(
        savedStateHandle = savedStateHandle,
        tabStateManager = tabStateManager,
        repository = repository,
        titleUpdateManager = titleUpdateManager,
        navigator = navigator
    )

    // Convenience factory to create a route-scoped BookContentViewModel from a NavBackStackEntry
    fun bookContentViewModel(savedStateHandle: SavedStateHandle): BookContentViewModel =
        provideBookContentViewModel(
            savedStateHandle = savedStateHandle,
            tabStateManager = tabStateManager,
            repository = repository,
            titleUpdateManager = tabTitleUpdateManager,
            navigator = navigator,
            settings = settings
        )

    // Convenience factory to create a route-scoped SearchResultViewModel
    fun searchResultViewModel(savedStateHandle: SavedStateHandle): SearchResultViewModel =
        provideSearchResultViewModel(
            savedStateHandle = savedStateHandle,
            tabStateManager = tabStateManager,
            repository = repository,
            navigator = navigator,
            titleUpdateManager = tabTitleUpdateManager,
            cache = searchResultsCache,
            tabsViewModel = tabsViewModel
        )

    @Provides
    fun provideSearchResultViewModel(
        savedStateHandle: SavedStateHandle,
        tabStateManager: TabStateManager,
        repository: SeforimRepository,
        navigator: Navigator,
        titleUpdateManager: TabTitleUpdateManager,
        cache: SearchResultsCache,
        tabsViewModel: TabsViewModel
    ): SearchResultViewModel = SearchResultViewModel(
        savedStateHandle = savedStateHandle,
        stateManager = tabStateManager,
        repository = repository,
        navigator = navigator,
        titleUpdateManager = titleUpdateManager,
        cache = cache,
        tabsViewModel = tabsViewModel
    )

}
