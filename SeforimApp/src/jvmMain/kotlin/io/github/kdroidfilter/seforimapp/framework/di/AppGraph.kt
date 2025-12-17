package io.github.kdroidfilter.seforimapp.framework.di

import com.russhwolf.settings.Settings
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import io.github.kdroidfilter.seforim.navigation.TabNavControllerRegistry
import io.github.kdroidfilter.seforim.tabs.TabStateManager
import io.github.kdroidfilter.seforim.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.features.database.update.DatabaseCleanupUseCase
import io.github.kdroidfilter.seforimapp.features.onboarding.data.OnboardingProcessRepository
import io.github.kdroidfilter.seforimapp.features.onboarding.diskspace.AvailableDiskSpaceViewModel
import io.github.kdroidfilter.seforimapp.features.onboarding.download.DownloadViewModel
import io.github.kdroidfilter.seforimapp.features.onboarding.extract.ExtractViewModel
import io.github.kdroidfilter.seforimapp.features.onboarding.region.RegionConfigViewModel
import io.github.kdroidfilter.seforimapp.features.onboarding.typeofinstall.TypeOfInstallationViewModel
import io.github.kdroidfilter.seforimapp.features.onboarding.userprofile.UserProfileViewModel
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeViewModel
import io.github.kdroidfilter.seforimapp.features.settings.SettingsWindowViewModel
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimapp.framework.search.LuceneSearchService

/**
 * Metro DI graph: provider functions annotated with @Provides.
 * Singletons are scoped to [AppScope].
 */
@DependencyGraph(AppScope::class)
abstract class AppGraph : ViewModelGraph {

    // Expose strongly-typed graph entries as abstract vals for generated implementation
    // Removed Navigator; use TabsViewModel + TabNavControllerRegistry
    abstract val tabStateManager: TabStateManager
    abstract val tabTitleUpdateManager: TabTitleUpdateManager
    abstract val tabNavControllerRegistry: TabNavControllerRegistry
    abstract val settings: Settings
    abstract val repository: SeforimRepository
    abstract val luceneSearchService: LuceneSearchService
    abstract val tabsViewModel: TabsViewModel
    abstract val settingsWindowViewModel: SettingsWindowViewModel
    abstract val generalSettingsViewModel: io.github.kdroidfilter.seforimapp.features.settings.general.GeneralSettingsViewModel
    abstract val fontsSettingsViewModel: io.github.kdroidfilter.seforimapp.features.settings.fonts.FontsSettingsViewModel
    abstract val searchHomeViewModel: SearchHomeViewModel

    abstract val typeOfInstallationViewModel: TypeOfInstallationViewModel
    abstract val downloadViewModel: DownloadViewModel
    abstract val extractViewModel: ExtractViewModel
    abstract val onboardingProcessRepository: OnboardingProcessRepository
    abstract val availableDiskSpaceViewModel: AvailableDiskSpaceViewModel
    abstract val regionConfigViewModel: RegionConfigViewModel
    abstract val userProfileViewModel: UserProfileViewModel
    abstract val databaseCleanupUseCase: DatabaseCleanupUseCase

}
