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
@ViewModelKey(FontsSettingsViewModel::class)
@Inject
class FontsSettingsViewModel : ViewModel() {
    private val bookFont = MutableStateFlow(AppSettings.getBookFontCode())
    private val commentaryFont = MutableStateFlow(AppSettings.getCommentaryFontCode())
    private val targumFont = MutableStateFlow(AppSettings.getTargumFontCode())
    private val sourceFont = MutableStateFlow(AppSettings.getSourceFontCode())

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
                AppSettings.setBookFontCode(event.code)
                bookFont.value = event.code
            }
            is FontsSettingsEvents.SetCommentaryFont -> {
                AppSettings.setCommentaryFontCode(event.code)
                commentaryFont.value = event.code
            }
            is FontsSettingsEvents.SetTargumFont -> {
                AppSettings.setTargumFontCode(event.code)
                targumFont.value = event.code
            }
            is FontsSettingsEvents.SetSourceFont -> {
                AppSettings.setSourceFontCode(event.code)
                sourceFont.value = event.code
            }
            is FontsSettingsEvents.ResetToDefaults -> {
                AppSettings.setBookFontCode(AppSettings.DEFAULT_BOOK_FONT)
                AppSettings.setCommentaryFontCode(AppSettings.DEFAULT_COMMENTARY_FONT)
                AppSettings.setTargumFontCode(AppSettings.DEFAULT_TARGUM_FONT)
                AppSettings.setSourceFontCode(AppSettings.DEFAULT_SOURCE_FONT)
                bookFont.value = AppSettings.DEFAULT_BOOK_FONT
                commentaryFont.value = AppSettings.DEFAULT_COMMENTARY_FONT
                targumFont.value = AppSettings.DEFAULT_TARGUM_FONT
                sourceFont.value = AppSettings.DEFAULT_SOURCE_FONT
            }
        }
    }
}
