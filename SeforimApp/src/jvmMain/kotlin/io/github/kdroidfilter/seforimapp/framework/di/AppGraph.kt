package io.github.kdroidfilter.seforimapp.framework.di

import com.russhwolf.settings.Settings
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import io.github.kdroidfilter.seforim.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforimapp.backup.drive.GoogleDriveSync
import io.github.kdroidfilter.seforimapp.core.MainAppState
import io.github.kdroidfilter.seforimapp.core.annotations.HighlightStore
import io.github.kdroidfilter.seforimapp.core.annotations.NoteStore
import io.github.kdroidfilter.seforimapp.core.catalog.CatalogAccess
import io.github.kdroidfilter.seforimapp.core.favorites.FavoritesStore
import io.github.kdroidfilter.seforimapp.core.history.HistoryStore
import io.github.kdroidfilter.seforimapp.core.selection.SelectionContext
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.core.settings.CategoryDisplaySettingsStore
import io.github.kdroidfilter.seforimapp.core.shnayimmikra.ShnayimMikraStore
import io.github.kdroidfilter.seforimapp.features.database.update.DatabaseCleanupUseCase
import io.github.kdroidfilter.seforimapp.features.database.update.DatabasePreparationUseCase
import io.github.kdroidfilter.seforimapp.features.database.update.navigation.DatabaseUpdateProgressBarState
import io.github.kdroidfilter.seforimapp.features.home.widgets.ToolWindows
import io.github.kdroidfilter.seforimapp.features.onboarding.data.OnboardingProcessRepository
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.ProgressBarState
import io.github.kdroidfilter.seforimapp.framework.database.CatalogCache
import io.github.kdroidfilter.seforimapp.framework.database.DatabasePathProvider
import io.github.kdroidfilter.seforimapp.framework.database.DatabaseVersionManager
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopManager
import io.github.kdroidfilter.seforimapp.framework.session.SessionManager
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import io.github.kdroidfilter.seforimapp.framework.session.TabThumbnailStore
import io.github.kdroidfilter.seforimapp.framework.update.AppUpdateService
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine

/**
 * Metro DI graph: provider functions annotated with @Provides.
 * Singletons are scoped to [AppScope].
 */
@DependencyGraph(AppScope::class)
abstract class AppGraph : ViewModelGraph {
    // Expose strongly-typed graph entries as abstract vals for generated implementation
    abstract val mainAppState: MainAppState
    abstract val catalogAccess: CatalogAccess
    abstract val catalogCache: CatalogCache
    abstract val databasePathProvider: DatabasePathProvider
    abstract val databaseVersionManager: DatabaseVersionManager
    abstract val selectionContext: SelectionContext
    abstract val tabPersistedStateStore: TabPersistedStateStore
    abstract val tabThumbnailStore: TabThumbnailStore
    abstract val tabTitleUpdateManager: TabTitleUpdateManager
    abstract val settings: Settings
    abstract val appSettings: AppSettings
    abstract val categoryDisplaySettingsStore: CategoryDisplaySettingsStore
    abstract val highlightStore: HighlightStore
    abstract val historyStore: HistoryStore
    abstract val favoritesStore: FavoritesStore
    abstract val shnayimMikraStore: ShnayimMikraStore
    abstract val noteStore: NoteStore
    abstract val repository: SeforimRepository
    abstract val searchEngine: SearchEngine
    abstract val desktopManager: DesktopManager
    abstract val sessionManager: SessionManager
    abstract val googleDriveSync: GoogleDriveSync

    abstract val onboardingProcessRepository: OnboardingProcessRepository
    abstract val databaseCleanupUseCase: DatabaseCleanupUseCase
    abstract val databasePreparationUseCase: DatabasePreparationUseCase
    abstract val onboardingProgressBarState: ProgressBarState
    abstract val databaseUpdateProgressBarState: DatabaseUpdateProgressBarState

    /** Seforim.db delta-update facade (recoverIfNeeded + checkAndApply). */
    abstract val dbDeltaUpdateService: io.github.kdroidfilter.seforimapp.framework.update.DbDeltaUpdateService

    /** App self-update facade (Nucleus updater + native SSL). */
    abstract val appUpdateService: AppUpdateService
    abstract val toolWindows: ToolWindows
}
