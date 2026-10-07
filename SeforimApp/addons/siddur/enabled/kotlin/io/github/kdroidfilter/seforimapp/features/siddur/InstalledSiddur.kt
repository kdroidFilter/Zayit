package io.github.kdroidfilter.seforimapp.features.siddur

import androidx.compose.runtime.Composable
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.e2e.E2eScenario
import io.github.kdroidfilter.seforimapp.core.e2e.E2eSiddurScenario

/** An official build: the smart siddur. */
val installedSiddur: SiddurFeature? =
    object : SiddurFeature {
        @Composable
        override fun TabContent(
            tabId: String,
            destination: TabsDestination.Siddur,
        ) = SiddurTabContent(
            tabId = tabId,
            initialPart = destination.part,
            initialEpochDay = destination.epochDay,
            initialHeading = destination.heading,
        )

        override suspend fun runE2e(sc: E2eScenario) = E2eSiddurScenario.run(sc)
    }
