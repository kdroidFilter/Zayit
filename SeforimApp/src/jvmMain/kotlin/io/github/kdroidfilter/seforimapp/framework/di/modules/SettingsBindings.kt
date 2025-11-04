package io.github.kdroidfilter.seforimapp.framework.di.modules

import com.russhwolf.settings.Settings
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.settings.SettingsWindowViewModel
import io.github.kdroidfilter.seforimapp.features.settings.fonts.FontsSettingsViewModel
import io.github.kdroidfilter.seforimapp.features.settings.general.GeneralSettingsViewModel
import io.github.kdroidfilter.seforimapp.framework.di.AppScope

@ContributesTo(AppScope::class)
@BindingContainer
object SettingsBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun provideSettingsWindowViewModel(settings: Settings): SettingsWindowViewModel {
        // Ensure AppSettings uses the same Settings instance as provided by DI
        AppSettings.initialize(settings)
        return SettingsWindowViewModel()
    }

    @Provides
    fun provideGeneralSettingsViewModel(): GeneralSettingsViewModel = GeneralSettingsViewModel()

    @Provides
    fun provideFontsSettingsViewModel(): FontsSettingsViewModel = FontsSettingsViewModel()
}