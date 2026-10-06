package io.github.kdroidfilter.seforimapp.framework.di.modules

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.database.update.DatabaseCleanupUseCase
import io.github.kdroidfilter.seforimapp.features.database.update.DatabasePreparationUseCase
import io.github.kdroidfilter.seforimapp.features.database.update.navigation.DatabaseUpdateProgressBarState
import io.github.kdroidfilter.seforimapp.features.onboarding.data.OnboardingProcessRepository
import io.github.kdroidfilter.seforimapp.features.onboarding.diskspace.AvailableDiskSpaceUseCase
import io.github.kdroidfilter.seforimapp.features.onboarding.download.DownloadUseCase
import io.github.kdroidfilter.seforimapp.features.onboarding.extract.ExtractUseCase
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.ProgressBarState
import io.github.kdroidfilter.seforimapp.features.onboarding.region.RegionConfigUseCase
import io.github.kdroidfilter.seforimapp.features.onboarding.userprofile.UserProfileUseCase
import io.github.kdroidfilter.seforimapp.framework.database.DatabasePathProvider
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.kdroidfilter.seforimapp.releasefetcher.github.GitHubReleaseFetcher
import io.ktor.client.HttpClient

@ContributesTo(AppScope::class)
@BindingContainer
object OnboardingBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun provideOnboardingProcessRepository(): OnboardingProcessRepository = OnboardingProcessRepository()

    @Provides
    @SingleIn(AppScope::class)
    fun provideDownloadUseCase(httpClient: HttpClient): DownloadUseCase =
        DownloadUseCase(
            gitHubReleaseFetcher = GitHubReleaseFetcher(owner = "kdroidFilter", repo = "SeforimLibrary", httpClient = httpClient),
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideExtractUseCase(appSettings: AppSettings): ExtractUseCase = ExtractUseCase(appSettings)

    @Provides
    @SingleIn(AppScope::class)
    fun provideAvailableDiskSpaceUseCase(): AvailableDiskSpaceUseCase = AvailableDiskSpaceUseCase()

    @Provides
    @SingleIn(AppScope::class)
    fun provideRegionConfigUseCase(): RegionConfigUseCase = RegionConfigUseCase()

    @Provides
    @SingleIn(AppScope::class)
    fun provideUserProfileUseCase(): UserProfileUseCase = UserProfileUseCase()

    @Provides
    @SingleIn(AppScope::class)
    fun provideOnboardingProgressBarState(): ProgressBarState = ProgressBarState()

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabaseUpdateProgressBarState(): DatabaseUpdateProgressBarState = DatabaseUpdateProgressBarState()

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabaseCleanupUseCase(
        databasePathProvider: DatabasePathProvider,
        appSettings: AppSettings,
    ): DatabaseCleanupUseCase = DatabaseCleanupUseCase(databasePathProvider, appSettings)

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabasePreparationUseCase(
        cleanupUseCase: DatabaseCleanupUseCase,
        diskSpaceUseCase: AvailableDiskSpaceUseCase,
    ): DatabasePreparationUseCase = DatabasePreparationUseCase(cleanupUseCase, diskSpaceUseCase)
}
