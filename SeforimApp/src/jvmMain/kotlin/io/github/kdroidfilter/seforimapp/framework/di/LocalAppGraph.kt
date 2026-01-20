package io.github.kdroidfilter.seforimapp.framework.di

import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal holder for the application dependency graph.
 */
val LocalAppGraph =
    compositionLocalOf<AppGraph> {
        error("No AppGraph provided")
    }
