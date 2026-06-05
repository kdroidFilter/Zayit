package io.github.kdroidfilter.seforimapp.framework.di.modules

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.russhwolf.settings.Settings
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforim.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.core.MainAppState
import io.github.kdroidfilter.seforimapp.core.annotations.HighlightStore
import io.github.kdroidfilter.seforimapp.core.annotations.NoteStore
import io.github.kdroidfilter.seforimapp.core.catalog.CatalogAccess
import io.github.kdroidfilter.seforimapp.core.selection.DefaultSelectionContext
import io.github.kdroidfilter.seforimapp.core.selection.SelectionContext
import io.github.kdroidfilter.seforimapp.core.settings.CategoryDisplaySettingsStore
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeViewModel
import io.github.kdroidfilter.seforimapp.framework.database.CatalogCache
import io.github.kdroidfilter.seforimapp.framework.database.PersistentSqliteDriver
import io.github.kdroidfilter.seforimapp.framework.database.getDatabasePath
import io.github.kdroidfilter.seforimapp.framework.database.getUserSettingsDatabasePath
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopManager
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.kdroidfilter.seforimapp.framework.search.AcronymFrequencyCache
import io.github.kdroidfilter.seforimapp.framework.search.LuceneLookupSearchService
import io.github.kdroidfilter.seforimapp.framework.search.RepositorySnippetSourceProvider
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import io.github.kdroidfilter.seforimapp.framework.update.AppUpdateService
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.LuceneSearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import java.nio.file.Paths
import java.util.UUID

@ContributesTo(AppScope::class)
@BindingContainer
object AppCoreBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun provideMainAppState(): MainAppState = MainAppState()

    @Provides
    @SingleIn(AppScope::class)
    fun provideCatalogAccess(): CatalogAccess = CatalogAccess { CatalogCache.getCatalog() }

    @Provides
    @SingleIn(AppScope::class)
    fun provideSelectionContext(): SelectionContext = DefaultSelectionContext()

    @Provides
    @SingleIn(AppScope::class)
    fun provideTabPersistedStateStore(): TabPersistedStateStore = TabPersistedStateStore()

    @Provides
    @SingleIn(AppScope::class)
    fun provideTabTitleUpdateManager(): TabTitleUpdateManager = TabTitleUpdateManager()

    @Provides
    @SingleIn(AppScope::class)
    fun provideSettings(): Settings = Settings()

    @Provides
    @SingleIn(AppScope::class)
    fun provideUserSettingsDb(): UserSettingsDb {
        // Single shared connection to the local user database (separate from the
        // read-only books DB). All user stores inject this instance instead of
        // opening their own driver. New tables are added transparently for
        // existing users via CREATE TABLE IF NOT EXISTS in Schema.create().
        val driver = JdbcSqliteDriver("jdbc:sqlite:${getUserSettingsDatabasePath()}")
        UserSettingsDb.Schema.create(driver)
        return UserSettingsDb(driver)
    }

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
    fun provideRepository(): SeforimRepository {
        val dbPath = getDatabasePath()
        // Persistent single-connection driver with prepared-statement cache +
        // read-tuning PRAGMAs. Replaces `JdbcSqliteDriver` whose ThreadedConnectionManager
        // closes the SQLite connection after every non-transactional query (confirmed by
        // JFR 2026-04-23: ~70 `NativeDB.prepare_utf8` + `NativeDB._close()` pairs / 20 s).
        val driver = PersistentSqliteDriver("jdbc:sqlite:$dbPath")
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
    fun provideAcronymFrequencyCache(): AcronymFrequencyCache = AcronymFrequencyCache()

    @Provides
    @SingleIn(AppScope::class)
    fun provideLuceneLookupSearchService(acronymCache: AcronymFrequencyCache): LuceneLookupSearchService {
        val dbPath = getDatabasePath()
        val indexPath = if (dbPath.endsWith(".db")) "$dbPath.lookup.lucene" else "$dbPath.lookupindex"
        return LuceneLookupSearchService(Paths.get(indexPath), acronymCache = acronymCache)
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideDbDeltaUpdateService(): io.github.kdroidfilter.seforimapp.framework.update.DbDeltaUpdateService {
        val dbPath = getDatabasePath()
        val seforimDb = Paths.get(dbPath)
        val catalogPb = Paths.get(seforimDb.parent.toString(), "catalog.pb")
        val workDir = Paths.get(seforimDb.parent.toString(), "delta-cache")
        val releaseMetaUrl =
            System.getenv("SEFORIMAPP_RELEASE_META_URL")
                ?: "https://kdroidfilter.github.io/SefariaExport/release_meta.json"
        return io.github.kdroidfilter.seforimapp.framework.update.DbDeltaUpdateService(
            seforimDb = seforimDb,
            catalogPb = catalogPb,
            workDir = workDir,
            releaseMetaUrl = releaseMetaUrl,
            localDbVersionProvider = {
                // schema_meta.db_version is bumped by the patch; if absent (pre-Phase 2
                // builds), default to 0 so the very first delta is applied unconditionally.
                runCatching {
                    java.sql.DriverManager.getConnection("jdbc:sqlite:$dbPath").use { c ->
                        c.prepareStatement("SELECT value FROM schema_meta WHERE key='db_version'").use { ps ->
                            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1).toIntOrNull() else null }
                        }
                    }
                }.getOrNull() ?: 0
            },
        )
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppUpdateService(): AppUpdateService = AppUpdateService.create()

    @Provides
    @SingleIn(AppScope::class)
    fun provideTabsViewModel(titleUpdateManager: TabTitleUpdateManager): TabsViewModel =
        TabsViewModel(
            titleUpdateManager = titleUpdateManager,
            startDestination =
                TabsDestination.BookContent(
                    bookId = -1,
                    tabId = UUID.randomUUID().toString(),
                ),
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideDesktopManager(
        tabsViewModel: TabsViewModel,
        tabPersistedStateStore: TabPersistedStateStore,
    ): DesktopManager =
        DesktopManager(
            tabsViewModel = tabsViewModel,
            tabPersistedStateStore = tabPersistedStateStore,
            defaultDesktopName = "\u05DE\u05E8\u05D7\u05D1 \u05D0׳",
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideSearchHomeViewModel(
        persistedStore: TabPersistedStateStore,
        repository: SeforimRepository,
        lookup: LuceneLookupSearchService,
        settings: Settings,
    ): SearchHomeViewModel =
        SearchHomeViewModel(
            persistedStore = persistedStore,
            repository = repository,
            lookup = lookup,
            settings = settings,
        )
}
