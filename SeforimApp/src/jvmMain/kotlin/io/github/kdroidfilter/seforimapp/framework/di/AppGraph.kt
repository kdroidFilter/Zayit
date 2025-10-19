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
import io.github.kdroidfilter.seforimapp.core.settings.AppSettingsImpl
import io.github.kdroidfilter.seforimapp.core.settings.IAppSettings
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentViewModel
import io.github.kdroidfilter.seforimapp.features.settings.SettingsViewModel
import io.github.kdroidfilter.seforimapp.framework.database.getDatabasePath
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
    abstract val appSettings: IAppSettings
    abstract val tabsViewModel: TabsViewModel
    abstract val settingsViewModel: SettingsViewModel

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
    fun provideAppSettings(settings: Settings): IAppSettings = AppSettingsImpl(settings)

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
    fun provideSettingsViewModel(appSettings: IAppSettings): SettingsViewModel = SettingsViewModel(appSettings)

    @Provides
    fun provideBookContentViewModel(
        savedStateHandle: SavedStateHandle,
        tabStateManager: TabStateManager,
        repository: SeforimRepository,
        titleUpdateManager: TabTitleUpdateManager,
        navigator: Navigator,
        appSettings: IAppSettings
    ): BookContentViewModel = BookContentViewModel(
        savedStateHandle = savedStateHandle,
        tabStateManager = tabStateManager,
        repository = repository,
        titleUpdateManager = titleUpdateManager,
        navigator = navigator,
        appSettings = appSettings
    )

    // Convenience factory to create a route-scoped BookContentViewModel from a NavBackStackEntry
    fun bookContentViewModel(savedStateHandle: SavedStateHandle): BookContentViewModel =
        provideBookContentViewModel(
            savedStateHandle = savedStateHandle,
            tabStateManager = tabStateManager,
            repository = repository,
            titleUpdateManager = tabTitleUpdateManager,
            navigator = navigator,
            appSettings = appSettings
        )
}
