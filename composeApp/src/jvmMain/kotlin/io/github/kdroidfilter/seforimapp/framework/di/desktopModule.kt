package io.github.kdroidfilter.seforimapp.framework.di

import androidx.compose.runtime.Composable
import androidx.lifecycle.SavedStateHandle
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimapp.core.presentation.navigation.DefaultNavigator
import io.github.kdroidfilter.seforimapp.core.presentation.navigation.Navigator
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabStateManager
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.BookContentViewModel
import io.github.kdroidfilter.seforimapp.framework.database.getDatabasePath
import io.github.kdroidfilter.seforimapp.framework.database.getRepository
import org.koin.dsl.module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.singleOf
import java.util.UUID

val desktopModule = module {
    single<Navigator> {
        DefaultNavigator(startDestination = TabsDestination.Home(UUID.randomUUID().toString()))
    }

    // Register TabStateManager as a singleton
    single { TabStateManager() }

    // Register SeforimRepository as a singleton
    single<SeforimRepository> {
        // Use a fixed database path as specified in the requirements
        val dbPath = getDatabasePath()
        val driver = app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver("jdbc:sqlite:$dbPath")
        SeforimRepository(dbPath, driver)
    }

    viewModel {
        TabsViewModel(navigator = get())
    }

    viewModel { (savedStateHandle: SavedStateHandle) ->
        BookContentViewModel(
            savedStateHandle = savedStateHandle,
            stateManager = get(),
            repository = get()
        )
    }
}
