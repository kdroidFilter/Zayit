package io.github.kdroidfilter.seforimapp.framework.di.modules

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.russhwolf.settings.Settings
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforim.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforimapp.core.MainAppState
import io.github.kdroidfilter.seforimapp.core.annotations.HighlightStore
import io.github.kdroidfilter.seforimapp.core.annotations.NoteStore
import io.github.kdroidfilter.seforimapp.core.catalog.CatalogAccess
import io.github.kdroidfilter.seforimapp.core.e2e.E2e
import io.github.kdroidfilter.seforimapp.core.favorites.FavoritesStore
import io.github.kdroidfilter.seforimapp.core.history.HistoryStore
import io.github.kdroidfilter.seforimapp.core.selection.DefaultSelectionContext
import io.github.kdroidfilter.seforimapp.core.selection.SelectionContext
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.core.settings.CategoryDisplaySettingsStore
import io.github.kdroidfilter.seforimapp.core.shnayimmikra.ShnayimMikraStore
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeViewModel
import io.github.kdroidfilter.seforimapp.framework.database.CatalogCache
import io.github.kdroidfilter.seforimapp.framework.database.DatabasePathProvider
import io.github.kdroidfilter.seforimapp.framework.database.PersistentSqliteDriver
import io.github.kdroidfilter.seforimapp.framework.database.getUserSettingsDatabasePath
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopManager
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.kdroidfilter.seforimapp.framework.search.AcronymFrequencyCache
import io.github.kdroidfilter.seforimapp.framework.search.LuceneLookupSearchService
import io.github.kdroidfilter.seforimapp.framework.search.RepositorySnippetSourceProvider
import io.github.kdroidfilter.seforimapp.framework.session.SessionManager
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import io.github.kdroidfilter.seforimapp.framework.session.TabThumbnailStore
import io.github.kdroidfilter.seforimapp.framework.update.AppUpdateService
import io.github.kdroidfilter.seforimapp.network.KtorConfig
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.HybridSearchEngine
import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.LuceneSearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import io.ktor.client.HttpClient
import java.io.File
import java.nio.file.Paths

@ContributesTo(AppScope::class)
@BindingContainer
object AppCoreBindings {
    /** The app's single HTTP client, trusting the OS certificate stores. */
    @Provides
    @SingleIn(AppScope::class)
    fun provideHttpClient(): HttpClient = KtorConfig.createHttpClient()

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
    fun provideHistoryStore(database: UserSettingsDb): HistoryStore = HistoryStore(database)

    @Provides
    @SingleIn(AppScope::class)
    fun provideFavoritesStore(database: UserSettingsDb): FavoritesStore = FavoritesStore(database)

    @Provides
    @SingleIn(AppScope::class)
    fun provideShnayimMikraStore(database: UserSettingsDb): ShnayimMikraStore = ShnayimMikraStore(database)

    @Provides
    @SingleIn(AppScope::class)
    fun provideRepository(databasePathProvider: DatabasePathProvider): SeforimRepository {
        val dbPath = databasePathProvider.get()
        // Persistent single-connection driver with prepared-statement cache +
        // read-tuning PRAGMAs. Replaces `JdbcSqliteDriver` whose ThreadedConnectionManager
        // closes the SQLite connection after every non-transactional query (confirmed by
        // JFR 2026-04-23: ~70 `NativeDB.prepare_utf8` + `NativeDB._close()` pairs / 20 s).
        val driver = PersistentSqliteDriver("jdbc:sqlite:$dbPath")
        return SeforimRepository(dbPath, driver)
    }

    /**
     * The app's search engine. Returns a HYBRID engine (lexical BM25 + MagicDictionary
     * FUSED with dense semantic search via the v5 embedding model, RRF) over a SINGLE
     * Lucene index that holds both the text fields and the dense vectors.
     * It implements [SearchEngine], so the rest of the app uses it transparently.
     *
     * Degrades gracefully to pure lexical if the embedding model is absent (the app
     * works with or without the model from the SeforimEmbedding project). The dense
     * path uses the model bundled next to the DB (or `-DseforimEmbedModelDir`).
     */
    @Provides
    @SingleIn(AppScope::class)
    fun provideSearchEngine(
        repository: SeforimRepository,
        databasePathProvider: DatabasePathProvider,
    ): SearchEngine {
        val dbPath = databasePathProvider.get()
        val indexPath = Paths.get(if (dbPath.endsWith(".db")) "$dbPath.lucene" else "$dbPath.luceneindex")
        val dictionaryPath = indexPath.resolveSibling("lexical.db")
        val snippetProvider = RepositorySnippetSourceProvider(repository)
        val lexical = LuceneSearchEngine(indexPath, snippetProvider, dictionaryPath = dictionaryPath)
        // Single fused index: dense vectors live in the SAME Lucene index as the text
        // (seforim.db.lucene), so the dense searcher opens that same directory.
        // The embedding model is bundled next to the DB (extracted from the .tar.zst),
        // so the embedder looks in the database directory.
        val modelDir = Paths.get(dbPath).parent
        return HybridSearchEngine.create(lexical, indexDir = indexPath, modelDir = modelDir) { lineId, _, query ->
            val line = repository.getLine(lineId)
            if (line == null) {
                null
            } else {
                val title = repository.getBook(line.bookId)?.title ?: ""
                LineHit(
                    bookId = line.bookId,
                    bookTitle = title,
                    lineId = lineId,
                    lineIndex = line.lineIndex,
                    snippet = lexical.buildSnippet(line.content, query, 5),
                    score = 0f,
                    rawText = line.content,
                )
            }
        }
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideAcronymFrequencyCache(databasePathProvider: DatabasePathProvider): AcronymFrequencyCache =
        AcronymFrequencyCache(databasePathProvider)

    @Provides
    @SingleIn(AppScope::class)
    fun provideLuceneLookupSearchService(
        acronymCache: AcronymFrequencyCache,
        databasePathProvider: DatabasePathProvider,
    ): LuceneLookupSearchService {
        val dbPath = databasePathProvider.get()
        val indexPath = if (dbPath.endsWith(".db")) "$dbPath.lookup.lucene" else "$dbPath.lookupindex"
        return LuceneLookupSearchService(Paths.get(indexPath), acronymCache = acronymCache)
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideDbDeltaUpdateService(
        databasePathProvider: DatabasePathProvider,
    ): io.github.kdroidfilter.seforimapp.framework.update.DbDeltaUpdateService {
        val dbPath = databasePathProvider.get()
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
    fun provideTabThumbnailStore(): TabThumbnailStore =
        // The end-to-end harness keeps its pictures in its own output, never in the user's session.
        TabThumbnailStore(E2e.outDir?.let { File(it, "thumbnails") } ?: File(FileKit.databasesDir.path, "session/thumbnails"))

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppUpdateService(): AppUpdateService = AppUpdateService.create()

    @Provides
    @SingleIn(AppScope::class)
    fun provideDesktopManager(
        tabPersistedStateStore: TabPersistedStateStore,
        thumbnails: TabThumbnailStore,
        titleUpdateManager: TabTitleUpdateManager,
        repository: SeforimRepository,
        lookup: LuceneLookupSearchService,
        appSettings: AppSettings,
        sessionManager: SessionManager,
    ): DesktopManager =
        DesktopManager(
            tabPersistedStateStore = tabPersistedStateStore,
            thumbnails = thumbnails,
            titleUpdateManager = titleUpdateManager,
            // TabsViewModel + SearchHomeViewModel are window-scoped: one pair per open window,
            // created and disposed by DesktopManager.
            searchHomeViewModelFactory = {
                SearchHomeViewModel(
                    persistedStore = tabPersistedStateStore,
                    repository = repository,
                    lookup = lookup,
                    appSettings = appSettings,
                )
            },
            bootState = sessionManager.loadBootState(repository),
            defaultDesktopName = "\u05DE\u05E8\u05D7\u05D1 \u05D0׳",
        )
}
