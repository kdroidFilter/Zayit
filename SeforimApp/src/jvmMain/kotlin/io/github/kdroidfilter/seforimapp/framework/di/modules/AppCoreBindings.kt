package io.github.kdroidfilter.seforimapp.framework.di.modules

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.russhwolf.settings.Settings
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforim.navigation.TabNavControllerRegistry
import io.github.kdroidfilter.seforim.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.core.settings.CategoryDisplaySettingsStore
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeViewModel
import io.github.kdroidfilter.seforimapp.framework.database.getDatabasePath
import io.github.kdroidfilter.seforimapp.framework.database.getUserSettingsDatabasePath
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.LuceneSearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.github.kdroidfilter.seforimapp.framework.search.LuceneLookupSearchService
import io.github.kdroidfilter.seforimapp.framework.search.AcronymFrequencyCache
import io.github.kdroidfilter.seforimapp.framework.search.RepositorySnippetSourceProvider
import java.nio.file.Paths
import java.util.UUID

@ContributesTo(AppScope::class)
@BindingContainer
object AppCoreBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun provideTabPersistedStateStore(): TabPersistedStateStore = TabPersistedStateStore()

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
    fun provideCategoryDisplaySettingsStore(): CategoryDisplaySettingsStore {
        val dbPath = getUserSettingsDatabasePath()
        val driver = JdbcSqliteDriver("jdbc:sqlite:$dbPath")
        UserSettingsDb.Schema.create(driver)
        val database = UserSettingsDb(driver)
        return CategoryDisplaySettingsStore(database)
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideRepository(): SeforimRepository {
        val dbPath = getDatabasePath()
        val driver = JdbcSqliteDriver("jdbc:sqlite:$dbPath")
        return SeforimRepository(dbPath, driver)
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideSearchEngine(repository: SeforimRepository): SearchEngine {
        val dbPath = getDatabasePath()
        val indexPath = Paths.get(if (dbPath.endsWith(".db")) "$dbPath.lucene" else "$dbPath.luceneindex")
        val dictionaryPath = indexPath.resolveSibling("lexical.db")
        val snippetProvider = RepositorySnippetSourceProvider(repository)
        return LuceneSearchEngine(indexPath, snippetProvider, dictionaryPath = dictionaryPath)
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideAcronymFrequencyCache(): AcronymFrequencyCache {
        return AcronymFrequencyCache()
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideLuceneLookupSearchService(
        acronymCache: AcronymFrequencyCache
    ): LuceneLookupSearchService {
        val dbPath = getDatabasePath()
        val indexPath = if (dbPath.endsWith(".db")) "$dbPath.lookup.lucene" else "$dbPath.lookupindex"
        return LuceneLookupSearchService(Paths.get(indexPath), acronymCache = acronymCache)
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideTabsViewModel(
        titleUpdateManager: TabTitleUpdateManager,
    ): TabsViewModel = TabsViewModel(
        titleUpdateManager = titleUpdateManager,
        startDestination = TabsDestination.BookContent(
            bookId = -1,
            tabId = UUID.randomUUID().toString()
        )
    )


    @Provides
    @SingleIn(AppScope::class)
    fun provideSearchHomeViewModel(
        tabsViewModel: TabsViewModel,
        persistedStore: TabPersistedStateStore,
        repository: SeforimRepository,
        searchEngine: SearchEngine,
        lookup: LuceneLookupSearchService,
        settings: Settings
    ): SearchHomeViewModel = SearchHomeViewModel(
        tabsViewModel = tabsViewModel,
        persistedStore = persistedStore,
        repository = repository,
        searchEngine = searchEngine,
        lookup = lookup,
        settings = settings
    )
}
