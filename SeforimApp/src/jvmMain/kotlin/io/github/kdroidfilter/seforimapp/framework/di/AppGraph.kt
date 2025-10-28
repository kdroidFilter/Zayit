package io.github.kdroidfilter.seforimapp.framework.di

import androidx.lifecycle.SavedStateHandle
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.russhwolf.settings.Settings
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforim.navigation.DefaultNavigator
import io.github.kdroidfilter.seforim.navigation.Navigator
import io.github.kdroidfilter.seforim.tabs.TabStateManager
import io.github.kdroidfilter.seforim.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentViewModel
import io.github.kdroidfilter.seforimapp.features.onboarding.download.DownloadUseCase
import io.github.kdroidfilter.seforimapp.features.onboarding.extract.ExtractUseCase
import io.github.kdroidfilter.seforimapp.features.onboarding.data.OnboardingProcessRepository
import io.github.kdroidfilter.seforimapp.features.onboarding.download.DownloadViewModel
import io.github.kdroidfilter.seforimapp.features.onboarding.extract.ExtractViewModel
import io.github.kdroidfilter.seforimapp.features.onboarding.diskspace.AvailableDiskSpaceUseCase
import io.github.kdroidfilter.seforimapp.features.onboarding.diskspace.AvailableDiskSpaceViewModel
import io.github.kdroidfilter.seforimapp.features.onboarding.typeofinstall.TypeOfInstallationViewModel
import io.github.kdroidfilter.seforimapp.features.onboarding.data.databaseFetcher
import io.github.kdroidfilter.seforimapp.features.settings.SettingsWindowViewModel
import io.github.kdroidfilter.seforimapp.framework.database.getDatabasePath
import io.github.kdroidfilter.seforimapp.features.onboarding.region.RegionConfigUseCase
import io.github.kdroidfilter.seforimapp.features.onboarding.region.RegionConfigViewModel
import io.github.kdroidfilter.seforimapp.features.onboarding.userprofile.UserProfileUseCase
import io.github.kdroidfilter.seforimapp.features.onboarding.userprofile.UserProfileViewModel
import io.github.kdroidfilter.seforimapp.features.search.SearchResultViewModel
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeViewModel
import io.github.kdroidfilter.seforimapp.features.search.SearchResultsCache
import java.util.UUID

/**
 * Metro DI graph: provider functions annotated with @Provides.
 * Singletons are scoped to [AppScope].
 */
@DependencyGraph
@SingleIn(AppScope::class)
abstract class AppGraph {

    // Expose strongly-typed graph entries as abstract vals for generated implementation
    abstract val navigator: Navigator
    abstract val tabStateManager: TabStateManager
    abstract val tabTitleUpdateManager: TabTitleUpdateManager
    abstract val settings: Settings
    abstract val repository: SeforimRepository
    abstract val tabsViewModel: TabsViewModel
    abstract val settingsWindowViewModel: SettingsWindowViewModel
    abstract val searchResultsCache: SearchResultsCache

    abstract val typeOfInstallationViewModel: TypeOfInstallationViewModel
    abstract val downloadViewModel: DownloadViewModel
    abstract val extractViewModel: ExtractViewModel
    abstract val availableDiskSpaceViewModel: AvailableDiskSpaceViewModel
    abstract val regionConfigViewModel: RegionConfigViewModel
    abstract val userProfileViewModel: UserProfileViewModel
    // Settings categories ViewModels (provided via helper functions)

    @Provides
    @SingleIn(AppScope::class)
    fun provideNavigator(): Navigator =
        DefaultNavigator(startDestination = TabsDestination.BookContent(bookId = -1, tabId = UUID.randomUUID().toString()))

    @Provides
    @SingleIn(AppScope::class)
    fun provideTabStateManager(): TabStateManager = TabStateManager()

    @Provides
    @SingleIn(AppScope::class)
    fun provideTabTitleUpdateManager(): TabTitleUpdateManager = TabTitleUpdateManager()

    @Provides
    @SingleIn(AppScope::class)
    fun provideSettings(): Settings = Settings()

    @Provides
    @SingleIn(AppScope::class)
    fun provideRepository(): SeforimRepository {
        val dbPath = getDatabasePath()
        val driver = JdbcSqliteDriver("jdbc:sqlite:$dbPath")
        return SeforimRepository(dbPath, driver)
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideTabsViewModel(
        navigator: Navigator,
        titleUpdateManager: TabTitleUpdateManager,
        stateManager: TabStateManager
    ): TabsViewModel = TabsViewModel(
        navigator = navigator,
        titleUpdateManager = titleUpdateManager,
        stateManager = stateManager
    )

    @Provides
    @SingleIn(AppScope::class)
    fun provideSettingsWindowViewModel(settings: Settings): SettingsWindowViewModel {
        // Ensure AppSettings uses the same Settings instance as provided by DI
        AppSettings.initialize(settings)
        return SettingsWindowViewModel()
    }

    @Provides
    fun provideBookContentViewModel(
        savedStateHandle: SavedStateHandle,
        tabStateManager: TabStateManager,
        repository: SeforimRepository,
        titleUpdateManager: TabTitleUpdateManager,
        navigator: Navigator,
        settings: Settings
    ): BookContentViewModel {
        // Ensure AppSettings uses the same Settings instance
        AppSettings.initialize(settings)
        return BookContentViewModel(
            savedStateHandle = savedStateHandle,
            tabStateManager = tabStateManager,
            repository = repository,
            titleUpdateManager = titleUpdateManager,
            navigator = navigator
        )
    }

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

    @Provides
    @SingleIn(AppScope::class)
    fun provideSearchResultsCache(): SearchResultsCache = SearchResultsCache(maxSize = 64)

    // Home search ViewModel (no SavedStateHandle; uses current tab from TabsViewModel)
    @Provides
    fun provideSearchHomeViewModel(
        tabsViewModel: TabsViewModel,
        tabStateManager: TabStateManager,
        repository: SeforimRepository,
        settings: Settings
    ): SearchHomeViewModel = SearchHomeViewModel(
        tabsViewModel = tabsViewModel,
        stateManager = tabStateManager,
        repository = repository,
        settings = settings
    )

    // Convenience accessor to get a fresh instance for Composables
    fun searchHomeViewModel(): SearchHomeViewModel =
        provideSearchHomeViewModel(
            tabsViewModel = tabsViewModel,
            tabStateManager = tabStateManager,
            repository = repository,
            settings = settings
        )

    // Settings: General
    @Provides
    fun provideGeneralSettingsViewModel(): io.github.kdroidfilter.seforimapp.features.settings.general.GeneralSettingsViewModel =
        io.github.kdroidfilter.seforimapp.features.settings.general.GeneralSettingsViewModel()

    fun generalSettingsViewModel(): io.github.kdroidfilter.seforimapp.features.settings.general.GeneralSettingsViewModel =
        provideGeneralSettingsViewModel()

    // Settings: Fonts
    @Provides
    fun provideFontsSettingsViewModel(): io.github.kdroidfilter.seforimapp.features.settings.fonts.FontsSettingsViewModel =
        io.github.kdroidfilter.seforimapp.features.settings.fonts.FontsSettingsViewModel()

    fun fontsSettingsViewModel(): io.github.kdroidfilter.seforimapp.features.settings.fonts.FontsSettingsViewModel =
        provideFontsSettingsViewModel()

    @Provides
    @SingleIn(AppScope::class)
    fun provideOnboardingProcessRepository(): OnboardingProcessRepository = OnboardingProcessRepository()

    @Provides
    @SingleIn(AppScope::class)
    fun provideDownloadUseCase(): DownloadUseCase = DownloadUseCase(
        gitHubReleaseFetcher = databaseFetcher
    )


    @Provides
    @SingleIn(AppScope::class)
    fun provideExtractUseCase(settings: Settings): ExtractUseCase {
        // Ensure AppSettings uses the same Settings instance
        AppSettings.initialize(settings)
        return ExtractUseCase()
    }

    @Provides
    fun provideTypeOfInstallationViewModel(
        processRepository: OnboardingProcessRepository
    ): TypeOfInstallationViewModel = TypeOfInstallationViewModel(processRepository)

    @Provides
    @SingleIn(AppScope::class)
    fun provideDownloadViewModel(
        downloadUseCase: DownloadUseCase,
        processRepository: OnboardingProcessRepository
    ): DownloadViewModel = DownloadViewModel(downloadUseCase, processRepository)

    @Provides
    @SingleIn(AppScope::class)
    fun provideExtractViewModel(
        extractUseCase: ExtractUseCase,
        processRepository: OnboardingProcessRepository
    ): ExtractViewModel = ExtractViewModel(extractUseCase, processRepository)

    @Provides
    @SingleIn(AppScope::class)
    fun provideAvailableDiskSpaceUseCase(): AvailableDiskSpaceUseCase = AvailableDiskSpaceUseCase()

    @Provides
    @SingleIn(AppScope::class)
    fun provideAvailableDiskSpaceViewModel(
        useCase: AvailableDiskSpaceUseCase
    ): AvailableDiskSpaceViewModel = AvailableDiskSpaceViewModel(useCase)

    @Provides
    @SingleIn(AppScope::class)
    fun provideRegionConfigUseCase(): RegionConfigUseCase = RegionConfigUseCase()

    @Provides
    @SingleIn(AppScope::class)
    fun provideRegionConfigViewModel(
        useCase: RegionConfigUseCase
    ): RegionConfigViewModel = RegionConfigViewModel(useCase)

    @Provides
    @SingleIn(AppScope::class)
    fun provideUserProfileUseCase(): UserProfileUseCase = UserProfileUseCase()

    @Provides
    @SingleIn(AppScope::class)
    fun provideUserProfileViewModel(
        useCase: UserProfileUseCase
    ): UserProfileViewModel = UserProfileViewModel(useCase)
}
