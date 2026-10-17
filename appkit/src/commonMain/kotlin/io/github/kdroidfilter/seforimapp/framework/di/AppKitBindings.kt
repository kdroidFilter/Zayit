package io.github.kdroidfilter.seforimapp.framework.di

import app.cash.sqldelight.db.SqlDriver
import com.russhwolf.settings.Settings
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforim.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforimapp.core.MainAppState
import io.github.kdroidfilter.seforimapp.core.annotations.HighlightStore
import io.github.kdroidfilter.seforimapp.core.annotations.NoteStore
import io.github.kdroidfilter.seforimapp.core.catalog.CatalogAccess
import io.github.kdroidfilter.seforimapp.core.favorites.FavoritesStore
import io.github.kdroidfilter.seforimapp.core.history.HistoryStore
import io.github.kdroidfilter.seforimapp.core.selection.DefaultSelectionContext
import io.github.kdroidfilter.seforimapp.core.selection.SelectionContext
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.core.settings.CategoryDisplaySettingsStore
import io.github.kdroidfilter.seforimapp.core.shnayimmikra.ShnayimMikraStore
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import io.github.kdroidfilter.seforimapp.features.search.SearchHistory
import io.github.kdroidfilter.seforimapp.features.search.SearchSettings
import io.github.kdroidfilter.seforimapp.framework.database.AppDatabases
import io.github.kdroidfilter.seforimapp.framework.database.CatalogCache
import io.github.kdroidfilter.seforimapp.framework.database.DatabasePathProvider
import io.github.kdroidfilter.seforimapp.framework.database.USER_SETTINGS_DRIVER
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository

/**
 * What every app's graph shares: the settings, the user's data, the books' repository and the app state. Each app
 * provides [AppDatabases] (its SQLite connections) and the ports of its shell (tabs, session, search tabs).
 */
@ContributesTo(AppScope::class)
@BindingContainer
object AppKitBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun provideSettings(): Settings = Settings()

    @Provides
    @SingleIn(AppScope::class)
    fun provideMainAppState(appSettings: AppSettings): MainAppState = MainAppState(appSettings)

    @Provides
    @SingleIn(AppScope::class)
    fun provideCatalogAccess(catalogCache: CatalogCache): CatalogAccess = CatalogAccess { catalogCache.getCatalog() }

    @Provides
    @SingleIn(AppScope::class)
    fun provideSelectionContext(): SelectionContext = DefaultSelectionContext()

    @Provides
    @SingleIn(AppScope::class)
    fun provideTabPersistedStateStore(): TabPersistedStateStore = TabPersistedStateStore()

    @Provides
    @SingleIn(AppScope::class)
    fun provideTabTitleUpdateManager(): TabTitleUpdateManager = TabTitleUpdateManager()

    /**
     * Single shared connection to the local user database: every user store injects this instance instead of opening
     * its own. New tables reach existing users through CREATE TABLE IF NOT EXISTS in Schema.create(), on each open.
     */
    @Provides
    @SingleIn(AppScope::class)
    @Named(USER_SETTINGS_DRIVER)
    fun provideUserSettingsDriver(databases: AppDatabases): SqlDriver =
        databases.openUserSettings().also { UserSettingsDb.Schema.create(it) }

    @Provides
    @SingleIn(AppScope::class)
    fun provideUserSettingsDb(
        @Named(USER_SETTINGS_DRIVER) driver: SqlDriver,
    ): UserSettingsDb = UserSettingsDb(driver)

    @Provides
    @SingleIn(AppScope::class)
    fun provideCategoryDisplaySettingsStore(database: UserSettingsDb): CategoryDisplaySettingsStore = CategoryDisplaySettingsStore(database)

    @Provides
    @SingleIn(AppScope::class)
    fun provideHighlightStore(database: UserSettingsDb): HighlightStore = HighlightStore(database)

    @Provides
    @SingleIn(AppScope::class)
    fun provideNoteStore(database: UserSettingsDb): NoteStore = NoteStore(database)

    @Provides
    @SingleIn(AppScope::class)
    fun provideHistoryStore(database: UserSettingsDb): HistoryStore = HistoryStore(database)

    @Provides
    @SingleIn(AppScope::class)
    fun provideFavoritesStore(database: UserSettingsDb): FavoritesStore = FavoritesStore(database)

    @Provides
    @SingleIn(AppScope::class)
    fun provideShnayimMikraStore(database: UserSettingsDb): ShnayimMikraStore = ShnayimMikraStore(database)

    @Provides
    @SingleIn(AppScope::class)
    fun provideRepository(
        databasePathProvider: DatabasePathProvider,
        databases: AppDatabases,
    ): SeforimRepository = databases.openSeforimRepository(databasePathProvider.get())

    // What the search screens (searchkit) ask of the app's settings and history
    @Provides
    fun provideSearchSettings(appSettings: AppSettings): SearchSettings = appSettings

    @Provides
    fun provideSearchHistory(historyStore: HistoryStore): SearchHistory = historyStore
}
