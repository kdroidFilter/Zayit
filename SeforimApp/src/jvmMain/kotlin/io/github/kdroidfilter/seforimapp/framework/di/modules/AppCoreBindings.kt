package io.github.kdroidfilter.seforimapp.framework.di.modules

import dev.nucleusframework.core.runtime.AppRestarter.restartApplication
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforim.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforim.tabs.TabsResolver
import io.github.kdroidfilter.seforimapp.BuildConfig
import io.github.kdroidfilter.seforimapp.backup.BackupManager
import io.github.kdroidfilter.seforimapp.backup.drive.DriveSyncConfig
import io.github.kdroidfilter.seforimapp.backup.drive.GoogleDriveSync
import io.github.kdroidfilter.seforimapp.core.e2e.E2e
import io.github.kdroidfilter.seforimapp.core.presentation.utils.UrlOpener
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeViewModel
import io.github.kdroidfilter.seforimapp.features.search.SearchSessionStore
import io.github.kdroidfilter.seforimapp.features.search.SearchTabs
import io.github.kdroidfilter.seforimapp.framework.backup.UserDataBackup
import io.github.kdroidfilter.seforimapp.framework.database.AppDatabases
import io.github.kdroidfilter.seforimapp.framework.database.DatabasePathProvider
import io.github.kdroidfilter.seforimapp.framework.database.getUserSettingsDatabasePath
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopManager
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopSessionShell
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.kdroidfilter.seforimapp.framework.search.AcronymFrequencyCache
import io.github.kdroidfilter.seforimapp.framework.search.DesktopSearchTabs
import io.github.kdroidfilter.seforimapp.framework.search.LuceneLookupSearchService
import io.github.kdroidfilter.seforimapp.framework.search.RepositorySnippetSourceProvider
import io.github.kdroidfilter.seforimapp.framework.search.TabSearchSessionStore
import io.github.kdroidfilter.seforimapp.framework.search.openLuceneIndex
import io.github.kdroidfilter.seforimapp.framework.search.withSemanticSearch
import io.github.kdroidfilter.seforimapp.framework.session.SessionManager
import io.github.kdroidfilter.seforimapp.framework.session.SessionRestoreState
import io.github.kdroidfilter.seforimapp.framework.session.SessionShell
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import io.github.kdroidfilter.seforimapp.framework.session.TabThumbnailStore
import io.github.kdroidfilter.seforimapp.framework.update.AppUpdateService
import io.github.kdroidfilter.seforimapp.network.KtorConfig
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
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

    /** The desktop's SQLite connections (the common bindings open the repository and the user database on them). */
    @Provides
    @SingleIn(AppScope::class)
    fun provideAppDatabases(): AppDatabases = AppDatabases(getUserSettingsDatabasePath())

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

    /**
     * The app's search engine: lexical (BM25 + MagicDictionary) over the text index, fused with dense semantic search
     * in the official builds ([withSemanticSearch], open core).
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
        return withSemanticSearch(lexical, lexical.index, repository)
    }

    // What the reader (appkit) asks of the desktop around it
    @Provides
    fun provideTabsResolver(desktopManager: DesktopManager): TabsResolver = desktopManager

    @Provides
    fun provideSessionRestoreState(sessionManager: SessionManager): SessionRestoreState = sessionManager

    // DesktopManager is built from the saved session: the shell only reaches it when saving
    @Provides
    @SingleIn(AppScope::class)
    fun provideSessionShell(
        desktopManager: Lazy<DesktopManager>,
        thumbnails: TabThumbnailStore,
    ): SessionShell = DesktopSessionShell(desktopManager, thumbnails)

    // What the search screens (searchkit) ask of the desktop around them
    @Provides
    @SingleIn(AppScope::class)
    fun provideSearchTabs(desktopManager: DesktopManager): SearchTabs = DesktopSearchTabs(desktopManager)

    @Provides
    @SingleIn(AppScope::class)
    fun provideSearchSessionStore(store: TabPersistedStateStore): SearchSessionStore = TabSearchSessionStore(store)

    @Provides
    @SingleIn(AppScope::class)
    fun provideAcronymFrequencyCache(databasePathProvider: DatabasePathProvider): AcronymFrequencyCache =
        AcronymFrequencyCache(databasePathProvider::get)

    @Provides
    @SingleIn(AppScope::class)
    fun provideLuceneLookupSearchService(
        acronymCache: AcronymFrequencyCache,
        databasePathProvider: DatabasePathProvider,
    ): LuceneLookupSearchService {
        val dbPath = databasePathProvider.get()
        val indexPath = if (dbPath.endsWith(".db")) "$dbPath.lookup.lucene" else "$dbPath.lookupindex"
        return LuceneLookupSearchService(openLuceneIndex(indexPath, cacheQueries = true), acronymCache = acronymCache)
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
                    sessionStore = TabSearchSessionStore(tabPersistedStateStore),
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
