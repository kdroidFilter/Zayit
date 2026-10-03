package io.github.kdroidfilter.seforimapp.features.siddur

import androidx.compose.runtime.Composable
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.e2e.E2eScenario

/**
 * The smart siddur, in Zayit's official builds only (open core): its screen is in src/jvmSiddur, built with the
 * SeforimSiddur package; a community build compiles src/jvmNoSiddur instead, where [installedSiddur] is null.
 */
interface SiddurFeature {
    @Composable
    fun TabContent(
        tabId: String,
        destination: TabsDestination.Siddur,
    )

    suspend fun runE2e(sc: E2eScenario)
}
