package io.github.kdroidfilter.seforimapp.framework.di

import androidx.lifecycle.SavedStateHandle
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.russhwolf.settings.Settings
import io.github.kdroidfilter.seforim.navigation.DefaultNavigator
import io.github.kdroidfilter.seforim.navigation.Navigator
import io.github.kdroidfilter.seforim.tabs.TabStateManager
import io.github.kdroidfilter.seforim.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository

import io.github.kdroidfilter.seforimapp.core.settings.IAppSettings
import io.github.kdroidfilter.seforimapp.core.settings.AppSettingsImpl
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.BookContentViewModel
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.windows.about.SettingsViewModel
import io.github.kdroidfilter.seforimapp.framework.database.getDatabasePath
import org.koin.dsl.module
import org.koin.core.module.dsl.viewModel
import java.util.UUID

val desktopModule = module {
    single<Navigator> {
        DefaultNavigator(startDestination = TabsDestination.BookContent(bookId = -1, tabId = UUID.randomUUID().toString()))
    }

    // Register TabStateManager as a singleton
    single { TabStateManager() }
    
    // Register TabTitleUpdateManager as a singleton
    single { TabTitleUpdateManager() }

    // Register SeforimRepository as a singleton
    single<SeforimRepository> {
        // Use a fixed database path as specified in the requirements
        val dbPath = getDatabasePath()
        val driver = JdbcSqliteDriver("jdbc:sqlite:$dbPath")
        SeforimRepository(dbPath, driver)
    }
    
    // Register Settings as a singleton
    single { Settings() }
    
    // Register IAppSettings as a singleton
    single<IAppSettings> { AppSettingsImpl(get()) }

    viewModel {
        TabsViewModel(
            navigator = get(),
            titleUpdateManager = get(),
            stateManager = get()
        )
    }

    viewModel { (savedStateHandle: SavedStateHandle) ->
        BookContentViewModel(
            savedStateHandle = savedStateHandle,
            tabStateManager = get(),
            repository = get(),
            titleUpdateManager = get(),
            navigator = get()
        )
    }

    viewModel {
        SettingsViewModel()
    }
}
