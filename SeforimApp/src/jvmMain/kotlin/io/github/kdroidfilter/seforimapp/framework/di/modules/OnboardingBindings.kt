package io.github.kdroidfilter.seforimapp.framework.di.modules

import com.russhwolf.settings.Settings
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.onboarding.data.OnboardingProcessRepository
import io.github.kdroidfilter.seforimapp.features.onboarding.data.databaseFetcher
import io.github.kdroidfilter.seforimapp.features.onboarding.diskspace.AvailableDiskSpaceUseCase
import io.github.kdroidfilter.seforimapp.features.onboarding.diskspace.AvailableDiskSpaceViewModel
import io.github.kdroidfilter.seforimapp.features.onboarding.download.DownloadUseCase
import io.github.kdroidfilter.seforimapp.features.onboarding.download.DownloadViewModel
import io.github.kdroidfilter.seforimapp.features.onboarding.extract.ExtractUseCase
import io.github.kdroidfilter.seforimapp.features.onboarding.extract.ExtractViewModel
import io.github.kdroidfilter.seforimapp.features.onboarding.region.RegionConfigUseCase
import io.github.kdroidfilter.seforimapp.features.onboarding.region.RegionConfigViewModel
import io.github.kdroidfilter.seforimapp.features.onboarding.typeofinstall.TypeOfInstallationViewModel
import io.github.kdroidfilter.seforimapp.features.onboarding.userprofile.UserProfileUseCase
import io.github.kdroidfilter.seforimapp.features.onboarding.userprofile.UserProfileViewModel
import io.github.kdroidfilter.seforimapp.framework.di.AppScope

@ContributesTo(AppScope::class)
@BindingContainer
object OnboardingBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun provideOnboardingProcessRepository(): OnboardingProcessRepository = OnboardingProcessRepository()

    @Provides
    @SingleIn(AppScope::class)
    fun provideDownloadUseCase(): DownloadUseCase = DownloadUseCase(
        gitHubReleaseFetcher = databaseFetcher
    )

    @Provides
    @SingleIn(AppScope::class)
    fun provideExtractUseCase(settings: Settings): ExtractUseCase {
        AppSettings.initialize(settings)
        return ExtractUseCase()
    }

    @Provides
    fun provideTypeOfInstallationViewModel(
        processRepository: OnboardingProcessRepository
    ): TypeOfInstallationViewModel = TypeOfInstallationViewModel(processRepository)

    @Provides
    @SingleIn(AppScope::class)
    fun provideDownloadViewModel(
        downloadUseCase: DownloadUseCase,
        processRepository: OnboardingProcessRepository
    ): DownloadViewModel = DownloadViewModel(downloadUseCase, processRepository)

    @Provides
    @SingleIn(AppScope::class)
    fun provideExtractViewModel(
        extractUseCase: ExtractUseCase,
        processRepository: OnboardingProcessRepository
    ): ExtractViewModel = ExtractViewModel(extractUseCase, processRepository)

    @Provides
    @SingleIn(AppScope::class)
    fun provideAvailableDiskSpaceUseCase(): AvailableDiskSpaceUseCase = AvailableDiskSpaceUseCase()

    @Provides
    @SingleIn(AppScope::class)
    fun provideAvailableDiskSpaceViewModel(
        useCase: AvailableDiskSpaceUseCase
    ): AvailableDiskSpaceViewModel = AvailableDiskSpaceViewModel(useCase)

    @Provides
    @SingleIn(AppScope::class)
    fun provideRegionConfigUseCase(): RegionConfigUseCase = RegionConfigUseCase()

    @Provides
    @SingleIn(AppScope::class)
    fun provideRegionConfigViewModel(
        useCase: RegionConfigUseCase
    ): RegionConfigViewModel = RegionConfigViewModel(useCase)

    @Provides
    @SingleIn(AppScope::class)
    fun provideUserProfileUseCase(): UserProfileUseCase = UserProfileUseCase()

    @Provides
    @SingleIn(AppScope::class)
    fun provideUserProfileViewModel(
        useCase: UserProfileUseCase
    ): UserProfileViewModel = UserProfileViewModel(useCase)
}