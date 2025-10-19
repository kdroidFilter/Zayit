package io.github.kdroidfilter.seforimapp.core.settings

import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages application settings and preferences that persist across app restarts.
 * Uses Multiplatform Settings library for cross-platform storage.
 * Single, global settings instance (no interface, no delegation).
 */
object AppSettings {
    // Text size constants
    const val DEFAULT_TEXT_SIZE = 16f
    const val MIN_TEXT_SIZE = 8f
    const val MAX_TEXT_SIZE = 32f
    const val TEXT_SIZE_INCREMENT = 2f

    // Line height constants
    const val DEFAULT_LINE_HEIGHT = 1.5f
    const val MIN_LINE_HEIGHT = 1.0f
    const val MAX_LINE_HEIGHT = 2.5f
    const val LINE_HEIGHT_INCREMENT = 0.1f

    // Tab display constants
    const val MAX_TAB_TITLE_LENGTH = 20

    // Settings keys
    private const val KEY_TEXT_SIZE = "text_size"
    private const val KEY_LINE_HEIGHT = "line_height"
    private const val KEY_CLOSE_TREE_ON_NEW_BOOK = "close_tree_on_new_book"

    // Backing Settings storage (can be replaced at startup if needed)
    @Volatile
    private var settings: Settings = Settings()

    // Allow optional initialization with an externally provided Settings instance
    fun initialize(settings: Settings) {
        this.settings = settings
        // Refresh flows with current values from provided settings
        _textSizeFlow.value = getTextSize()
        _lineHeightFlow.value = getLineHeight()
        _closeTreeOnNewBookFlow.value = getCloseBookTreeOnNewBookSelected()
    }

    // StateFlow to observe text size changes
    private val _textSizeFlow = MutableStateFlow(getTextSize())
    val textSizeFlow: StateFlow<Float> = _textSizeFlow.asStateFlow()

    // StateFlow to observe line height changes
    private val _lineHeightFlow = MutableStateFlow(getLineHeight())
    val lineHeightFlow: StateFlow<Float> = _lineHeightFlow.asStateFlow()

    // StateFlow for auto-close book tree setting
    private val _closeTreeOnNewBookFlow = MutableStateFlow(getCloseBookTreeOnNewBookSelected())
    val closeBookTreeOnNewBookSelectedFlow: StateFlow<Boolean> = _closeTreeOnNewBookFlow.asStateFlow()

    fun getTextSize(): Float {
        return settings[KEY_TEXT_SIZE, DEFAULT_TEXT_SIZE]
    }

    fun setTextSize(size: Float) {
        settings[KEY_TEXT_SIZE] = size
        _textSizeFlow.value = size
    }

    fun increaseTextSize(increment: Float = TEXT_SIZE_INCREMENT) {
        val currentSize = getTextSize()
        val newSize = (currentSize + increment).coerceAtMost(MAX_TEXT_SIZE)
        setTextSize(newSize)
    }

    fun decreaseTextSize(decrement: Float = TEXT_SIZE_INCREMENT) {
        val currentSize = getTextSize()
        val newSize = (currentSize - decrement).coerceAtLeast(MIN_TEXT_SIZE)
        setTextSize(newSize)
    }

    fun getLineHeight(): Float {
        return settings[KEY_LINE_HEIGHT, DEFAULT_LINE_HEIGHT]
    }

    fun setLineHeight(height: Float) {
        settings[KEY_LINE_HEIGHT] = height
        _lineHeightFlow.value = height
    }

    fun increaseLineHeight(increment: Float = LINE_HEIGHT_INCREMENT) {
        val currentHeight = getLineHeight()
        val newHeight = (currentHeight + increment).coerceAtMost(MAX_LINE_HEIGHT)
        setLineHeight(newHeight)
    }

    fun decreaseLineHeight(decrement: Float = LINE_HEIGHT_INCREMENT) {
        val currentHeight = getLineHeight()
        val newHeight = (currentHeight - decrement).coerceAtLeast(MIN_LINE_HEIGHT)
        setLineHeight(newHeight)
    }

    fun getCloseBookTreeOnNewBookSelected(): Boolean {
        return settings[KEY_CLOSE_TREE_ON_NEW_BOOK, false]
    }

    fun setCloseBookTreeOnNewBookSelected(value: Boolean) {
        settings[KEY_CLOSE_TREE_ON_NEW_BOOK] = value
        _closeTreeOnNewBookFlow.value = value
    }
}
