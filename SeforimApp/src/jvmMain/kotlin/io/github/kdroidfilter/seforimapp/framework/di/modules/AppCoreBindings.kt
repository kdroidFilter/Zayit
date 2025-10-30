package io.github.kdroidfilter.seforimapp.framework.di.modules

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.russhwolf.settings.Settings
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforim.navigation.TabNavControllerRegistry
import io.github.kdroidfilter.seforim.tabs.TabStateManager
import io.github.kdroidfilter.seforim.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeViewModel
import io.github.kdroidfilter.seforimapp.framework.database.getDatabasePath
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import java.util.UUID

@ContributesTo(AppScope::class)
@BindingContainer
object AppCoreBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun provideTabStateManager(): TabStateManager = TabStateManager()

    @Provides
    @SingleIn(AppScope::class)
    fun provideTabNavControllerRegistry(): TabNavControllerRegistry = TabNavControllerRegistry()

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
        titleUpdateManager: TabTitleUpdateManager,
        stateManager: TabStateManager
    ): TabsViewModel = TabsViewModel(
        titleUpdateManager = titleUpdateManager,
        stateManager = stateManager,
        startDestination = TabsDestination.BookContent(
            bookId = -1,
            tabId = UUID.randomUUID().toString()
        )
    )


    @Provides
    @SingleIn(AppScope::class)
    fun provideSearchHomeViewModel(
        tabsViewModel: TabsViewModel,
        stateManager: TabStateManager,
        repository: SeforimRepository,
        settings: Settings
    ): SearchHomeViewModel = SearchHomeViewModel(
        tabsViewModel = tabsViewModel,
        stateManager = stateManager,
        repository = repository,
        settings = settings
    )
}
