package io.github.kdroidfilter.seforimapp.framework.di.modules

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.russhwolf.settings.Settings
import dev.nucleusframework.core.runtime.AppRestarter.restartApplication
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforim.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforimapp.BuildConfig
import io.github.kdroidfilter.seforimapp.backup.BackupManager
import io.github.kdroidfilter.seforimapp.backup.drive.DriveSyncConfig
import io.github.kdroidfilter.seforimapp.backup.drive.GoogleDriveSync
import io.github.kdroidfilter.seforimapp.core.MainAppState
import io.github.kdroidfilter.seforimapp.core.annotations.HighlightStore
import io.github.kdroidfilter.seforimapp.core.annotations.NoteStore
import io.github.kdroidfilter.seforimapp.core.catalog.CatalogAccess
import io.github.kdroidfilter.seforimapp.core.e2e.E2e
import io.github.kdroidfilter.seforimapp.core.favorites.FavoritesStore
import io.github.kdroidfilter.seforimapp.core.history.HistoryStore
import io.github.kdroidfilter.seforimapp.core.presentation.utils.UrlOpener
import io.github.kdroidfilter.seforimapp.core.selection.DefaultSelectionContext
import io.github.kdroidfilter.seforimapp.core.selection.SelectionContext
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.core.settings.CategoryDisplaySettingsStore
import io.github.kdroidfilter.seforimapp.core.shnayimmikra.ShnayimMikraStore
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeViewModel
import io.github.kdroidfilter.seforimapp.features.search.semantic.installedSemanticSearch
import io.github.kdroidfilter.seforimapp.framework.backup.UserDataBackup
import io.github.kdroidfilter.seforimapp.framework.database.CatalogCache
import io.github.kdroidfilter.seforimapp.framework.database.DatabasePathProvider
import io.github.kdroidfilter.seforimapp.framework.database.PersistentSqliteDriver
import io.github.kdroidfilter.seforimapp.framework.database.USER_SETTINGS_DRIVER
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
import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.LuceneSearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import io.ktor.client.HttpClient
import java.io.File
import java.nio.file.Paths
import java.util.Properties

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
    @Named(USER_SETTINGS_DRIVER)
    fun provideUserSettingsDriver(): SqlDriver {
        // Single shared connection to the local user database (separate from the
        // read-only books DB). All user stores inject this instance instead of
        // opening their own driver. New tables are added transparently for
        // existing users via CREATE TABLE IF NOT EXISTS in Schema.create().
        // Wait for a concurrent writer instead of failing: the backup snapshot (VACUUM INTO) runs
        // on its own thread, hence its own connection, while the stores keep writing.
        val driver =
            JdbcSqliteDriver(
                "jdbc:sqlite:${getUserSettingsDatabasePath()}",
                Properties().apply { setProperty("busy_timeout", USER_DB_BUSY_TIMEOUT_MS.toString()) },
            )
        UserSettingsDb.Schema.create(driver)
        return driver
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideBackupManager(source: UserDataBackup): BackupManager = BackupManager(source)

    @Provides
    @SingleIn(AppScope::class)
    fun provideGoogleDriveSync(
        httpClient: HttpClient,
        backupManager: BackupManager,
    ): GoogleDriveSync =
        GoogleDriveSync(
            httpClient = httpClient,
            backupManager = backupManager,
            config =
                DriveSyncConfig(
                    clientId = BuildConfig.GOOGLE_DRIVE_CLIENT_ID,
                    clientSecret = BuildConfig.GOOGLE_DRIVE_CLIENT_SECRET,
                    backupFileName = "zayit-backup.zip",
                    storageDirectory = { File(FileKit.databasesDir.path, "sync") },
                    openUrl = UrlOpener::open,
                    onRestoreStaged = { restartApplication() },
                    signInPage = ::driveSignInPage,
                ),
        )

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
     * The app's search engine: lexical (BM25 + MagicDictionary) over the text index, fused with dense semantic search
     * in the official builds ([installedSemanticSearch], open core), whose vectors live in the same Lucene index.
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
        val semantic = installedSemanticSearch ?: return lexical
        return semantic.hybrid(lexical, indexPath) { lineId, query ->
            repository.getLine(lineId)?.let { line ->
                LineHit(
                    bookId = line.bookId,
                    bookTitle = repository.getBook(line.bookId)?.title.orEmpty(),
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
            searchHomeViewModelFactory = { observeProfile ->
                SearchHomeViewModel(
                    persistedStore = tabPersistedStateStore,
                    repository = repository,
                    lookup = lookup,
                    appSettings = appSettings,
                    observeProfile = observeProfile,
                )
            },
            bootState = sessionManager.loadBootState(repository),
            defaultDesktopName = "\u05DE\u05E8\u05D7\u05D1 \u05D0׳",
        )
}

private const val USER_DB_BUSY_TIMEOUT_MS = 5_000

/** The page the browser shows once the Google sign-in returns to the app. */
private fun driveSignInPage(success: Boolean): String {
    val message = if (success) "זית מחובר לגוגל דרייב." else "החיבור לגוגל דרייב נכשל."
    return """
        <!doctype html><html lang="he" dir="rtl"><head><meta charset="utf-8"><title>זית</title>
        <style>body{font-family:system-ui,sans-serif;display:flex;align-items:center;justify-content:center;
        min-height:100vh;margin:0;color-scheme:light dark}main{text-align:center}</style></head>
        <body><main><h1>$message</h1><p>אפשר לסגור את הלשונית ולחזור לזית.</p></main></body></html>
        """.trimIndent()
}
