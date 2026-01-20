package io.github.kdroidfilter.seforimapp.features.onboarding.data

import io.github.kdroidfilter.platformtools.releasefetcher.github.GitHubReleaseFetcher
import io.github.kdroidfilter.seforimapp.network.KtorConfig

val databaseFetcher =
    GitHubReleaseFetcher(
        owner = "kdroidFilter",
        repo = "SeforimLibrary",
        httpClient = KtorConfig.createHttpClient(),
    )
