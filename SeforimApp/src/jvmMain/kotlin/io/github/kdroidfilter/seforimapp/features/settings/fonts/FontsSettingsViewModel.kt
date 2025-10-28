package io.github.kdroidfilter.seforimapp.features.settings.fonts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class FontsSettingsViewModel : ViewModel() {

    private val bookFont = MutableStateFlow(AppSettings.getBookFontCode())
    private val commentaryFont = MutableStateFlow(AppSettings.getCommentaryFontCode())
    private val targumFont = MutableStateFlow(AppSettings.getTargumFontCode())

    val state = combine(
        bookFont,
        commentaryFont,
        targumFont
    ) { b, c, t ->
        FontsSettingsState(
            bookFontCode = b,
            commentaryFontCode = c,
            targumFontCode = t
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        FontsSettingsState(
            bookFontCode = bookFont.value,
            commentaryFontCode = commentaryFont.value,
            targumFontCode = targumFont.value,
        )
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
        }
    }
}

