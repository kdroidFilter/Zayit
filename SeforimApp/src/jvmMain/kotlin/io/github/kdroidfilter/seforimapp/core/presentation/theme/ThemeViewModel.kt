package io.github.kdroidfilter.seforimapp.core.presentation.theme

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object ThemeViewModel {

    private val _theme = MutableStateFlow(IntUiThemes.System)
    val theme = _theme.asStateFlow()

    fun setTheme(theme: IntUiThemes) {
        _theme.value = theme
    }

}