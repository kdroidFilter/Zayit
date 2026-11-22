package io.github.kdroidfilter.seforimapp.features.settings.fonts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@ContributesIntoMap(AppScope::class)
@ViewModelKey
@Inject
class FontsSettingsViewModel(
    private val appSettings: AppSettings,
) : ViewModel() {
    private val bookFont = MutableStateFlow(appSettings.getBookFontCode())
    private val commentaryFont = MutableStateFlow(appSettings.getCommentaryFontCode())
    private val targumFont = MutableStateFlow(appSettings.getTargumFontCode())
    private val sourceFont = MutableStateFlow(appSettings.getSourceFontCode())

    val state =
        combine(
            bookFont,
            commentaryFont,
            targumFont,
            sourceFont,
        ) { b, c, t, s ->
            FontsSettingsState(
                bookFontCode = b,
                commentaryFontCode = c,
                targumFontCode = t,
                sourceFontCode = s,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            FontsSettingsState(
                bookFontCode = bookFont.value,
                commentaryFontCode = commentaryFont.value,
                targumFontCode = targumFont.value,
                sourceFontCode = sourceFont.value,
            ),
        )

    fun onEvent(event: FontsSettingsEvents) {
        when (event) {
            is FontsSettingsEvents.SetBookFont -> {
                appSettings.setBookFontCode(event.code)
                bookFont.value = event.code
            }
            is FontsSettingsEvents.SetCommentaryFont -> {
                appSettings.setCommentaryFontCode(event.code)
                commentaryFont.value = event.code
            }
            is FontsSettingsEvents.SetTargumFont -> {
                appSettings.setTargumFontCode(event.code)
                targumFont.value = event.code
            }
            is FontsSettingsEvents.SetSourceFont -> {
                appSettings.setSourceFontCode(event.code)
                sourceFont.value = event.code
            }
            is FontsSettingsEvents.ResetToDefaults -> {
                appSettings.setBookFontCode(AppSettings.DEFAULT_BOOK_FONT)
                appSettings.setCommentaryFontCode(AppSettings.DEFAULT_COMMENTARY_FONT)
                appSettings.setTargumFontCode(AppSettings.DEFAULT_TARGUM_FONT)
                appSettings.setSourceFontCode(AppSettings.DEFAULT_SOURCE_FONT)
                bookFont.value = AppSettings.DEFAULT_BOOK_FONT
                commentaryFont.value = AppSettings.DEFAULT_COMMENTARY_FONT
                targumFont.value = AppSettings.DEFAULT_TARGUM_FONT
                sourceFont.value = AppSettings.DEFAULT_SOURCE_FONT
            }
        }
    }
}
