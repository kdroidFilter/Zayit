package io.github.kdroidfilter.seforimapp.framework.di.modules

import com.russhwolf.settings.Settings
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.database.update.DatabaseCleanupUseCase
import io.github.kdroidfilter.seforimapp.features.onboarding.data.OnboardingProcessRepository
import io.github.kdroidfilter.seforimapp.features.onboarding.data.databaseFetcher
import io.github.kdroidfilter.seforimapp.features.onboarding.diskspace.AvailableDiskSpaceUseCase
import io.github.kdroidfilter.seforimapp.features.onboarding.download.DownloadUseCase
import io.github.kdroidfilter.seforimapp.features.onboarding.extract.ExtractUseCase
import io.github.kdroidfilter.seforimapp.features.onboarding.region.RegionConfigUseCase
import io.github.kdroidfilter.seforimapp.features.onboarding.userprofile.UserProfileUseCase
import io.github.kdroidfilter.seforimapp.framework.di.AppScope

@ContributesTo(AppScope::class)
@BindingContainer
object OnboardingBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun provideOnboardingProcessRepository(): OnboardingProcessRepository = OnboardingProcessRepository()

    @Provides
    @SingleIn(AppScope::class)
    fun provideDownloadUseCase(): DownloadUseCase =
        DownloadUseCase(
            gitHubReleaseFetcher = databaseFetcher,
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideExtractUseCase(settings: Settings): ExtractUseCase {
        AppSettings.initialize(settings)
        return ExtractUseCase()
    }

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
    fun provideDatabaseCleanupUseCase(): DatabaseCleanupUseCase = DatabaseCleanupUseCase()
}
