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
    
    // Settings keys
    private const val KEY_TEXT_SIZE = "text_size"
    private const val KEY_LINE_HEIGHT = "line_height"
    
    // Settings instance
    private val settings: Settings = Settings()
    
    // StateFlow to observe text size changes
    private val _textSizeFlow = MutableStateFlow(getTextSize())
    val textSizeFlow: StateFlow<Float> = _textSizeFlow.asStateFlow()
    
    /**
     * Gets the current text size from settings
     * @return The text size in sp
     */
    fun getTextSize(): Float {
        return settings[KEY_TEXT_SIZE, DEFAULT_TEXT_SIZE]
    }
    
    /**
     * Sets the text size and updates the flow
     * @param size The new text size in sp
     */
    fun setTextSize(size: Float) {
        settings[KEY_TEXT_SIZE] = size
        _textSizeFlow.value = size
    }
    
    /**
     * Increases the text size by the specified increment
     * @param increment The amount to increase (default is TEXT_SIZE_INCREMENT)
     */
    fun increaseTextSize(increment: Float = TEXT_SIZE_INCREMENT) {
        val currentSize = getTextSize()
        val newSize = (currentSize + increment).coerceAtMost(MAX_TEXT_SIZE)
        setTextSize(newSize)
    }
    
    /**
     * Decreases the text size by the specified decrement
     * @param decrement The amount to decrease (default is TEXT_SIZE_INCREMENT)
     */
    fun decreaseTextSize(decrement: Float = TEXT_SIZE_INCREMENT) {
        val currentSize = getTextSize()
        val newSize = (currentSize - decrement).coerceAtLeast(MIN_TEXT_SIZE)
        setTextSize(newSize)
    }
    
    /**
     * Gets the current line height from settings
     * @return The line height multiplier
     */
    fun getLineHeight(): Float {
        return settings[KEY_LINE_HEIGHT, DEFAULT_LINE_HEIGHT]
    }
    
    // StateFlow to observe line height changes
    private val _lineHeightFlow = MutableStateFlow(getLineHeight())
    val lineHeightFlow: StateFlow<Float> = _lineHeightFlow.asStateFlow()
    
    /**
     * Sets the line height and updates the flow
     * @param height The new line height multiplier
     */
    fun setLineHeight(height: Float) {
        settings[KEY_LINE_HEIGHT] = height
        _lineHeightFlow.value = height
    }
    
    /**
     * Increases the line height by the specified increment
     * @param increment The amount to increase (default is LINE_HEIGHT_INCREMENT)
     */
    fun increaseLineHeight(increment: Float = LINE_HEIGHT_INCREMENT) {
        val currentHeight = getLineHeight()
        val newHeight = (currentHeight + increment).coerceAtMost(MAX_LINE_HEIGHT)
        setLineHeight(newHeight)
    }
    
    /**
     * Decreases the line height by the specified decrement
     * @param decrement The amount to decrease (default is LINE_HEIGHT_INCREMENT)
     */
    fun decreaseLineHeight(decrement: Float = LINE_HEIGHT_INCREMENT) {
        val currentHeight = getLineHeight()
        val newHeight = (currentHeight - decrement).coerceAtLeast(MIN_LINE_HEIGHT)
        setLineHeight(newHeight)
    }
}