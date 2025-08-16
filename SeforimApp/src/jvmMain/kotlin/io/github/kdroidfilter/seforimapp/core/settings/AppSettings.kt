package io.github.kdroidfilter.seforimapp.core.settings

import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Implementation of IAppSettings interface that manages application settings and preferences.
 * Uses Multiplatform Settings library for cross-platform storage.
 */
class AppSettingsImpl(private val settings: Settings) : IAppSettings {
    // Settings keys
    private companion object {
        const val KEY_TEXT_SIZE = "text_size"
        const val KEY_LINE_HEIGHT = "line_height"
        const val KEY_CLOSE_TREE_ON_NEW_BOOK = "close_tree_on_new_book"
    }
    
    // StateFlow to observe text size changes
    private val _textSizeFlow = MutableStateFlow(getTextSize())
    override val textSizeFlow: StateFlow<Float> = _textSizeFlow.asStateFlow()
    
    // StateFlow to observe line height changes
    private val _lineHeightFlow = MutableStateFlow(getLineHeight())
    override val lineHeightFlow: StateFlow<Float> = _lineHeightFlow.asStateFlow()

    // StateFlow for auto-close book tree setting
    private val _closeTreeOnNewBookFlow = MutableStateFlow(getCloseBookTreeOnNewBookSelected())
    override val closeBookTreeOnNewBookSelectedFlow: StateFlow<Boolean> = _closeTreeOnNewBookFlow.asStateFlow()
    
    override fun getTextSize(): Float {
        return settings[KEY_TEXT_SIZE, AppSettings.DEFAULT_TEXT_SIZE]
    }
    
    override fun setTextSize(size: Float) {
        settings[KEY_TEXT_SIZE] = size
        _textSizeFlow.value = size
    }
    
    override fun increaseTextSize(increment: Float) {
        val currentSize = getTextSize()
        val newSize = (currentSize + increment).coerceAtMost(AppSettings.MAX_TEXT_SIZE)
        setTextSize(newSize)
    }
    
    override fun decreaseTextSize(decrement: Float) {
        val currentSize = getTextSize()
        val newSize = (currentSize - decrement).coerceAtLeast(AppSettings.MIN_TEXT_SIZE)
        setTextSize(newSize)
    }
    
    override fun getLineHeight(): Float {
        return settings[KEY_LINE_HEIGHT, AppSettings.DEFAULT_LINE_HEIGHT]
    }
    
    override fun setLineHeight(height: Float) {
        settings[KEY_LINE_HEIGHT] = height
        _lineHeightFlow.value = height
    }
    
    override fun increaseLineHeight(increment: Float) {
        val currentHeight = getLineHeight()
        val newHeight = (currentHeight + increment).coerceAtMost(AppSettings.MAX_LINE_HEIGHT)
        setLineHeight(newHeight)
    }
    
    override fun decreaseLineHeight(decrement: Float) {
        val currentHeight = getLineHeight()
        val newHeight = (currentHeight - decrement).coerceAtLeast(AppSettings.MIN_LINE_HEIGHT)
        setLineHeight(newHeight)
    }

    override fun getCloseBookTreeOnNewBookSelected(): Boolean {
        return settings[KEY_CLOSE_TREE_ON_NEW_BOOK, false]
    }

    override fun setCloseBookTreeOnNewBookSelected(value: Boolean) {
        settings[KEY_CLOSE_TREE_ON_NEW_BOOK] = value
        _closeTreeOnNewBookFlow.value = value
    }
}

/**
 * Manages application settings and preferences that persist across app restarts.
 * Uses Multiplatform Settings library for cross-platform storage.
 * 
 * This object delegates to a Koin-managed implementation of IAppSettings.
 */
object AppSettings : KoinComponent {
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
    
    // Get the implementation from Koin
    private val impl: IAppSettings by inject()
    
    // Delegate to the implementation
    val textSizeFlow: StateFlow<Float> get() = impl.textSizeFlow
    val lineHeightFlow: StateFlow<Float> get() = impl.lineHeightFlow
    val closeBookTreeOnNewBookSelectedFlow: StateFlow<Boolean> get() = impl.closeBookTreeOnNewBookSelectedFlow
    
    fun getTextSize(): Float = impl.getTextSize()
    fun setTextSize(size: Float) = impl.setTextSize(size)
    fun increaseTextSize(increment: Float = TEXT_SIZE_INCREMENT) = impl.increaseTextSize(increment)
    fun decreaseTextSize(decrement: Float = TEXT_SIZE_INCREMENT) = impl.decreaseTextSize(decrement)
    
    fun getLineHeight(): Float = impl.getLineHeight()
    fun setLineHeight(height: Float) = impl.setLineHeight(height)
    fun increaseLineHeight(increment: Float = LINE_HEIGHT_INCREMENT) = impl.increaseLineHeight(increment)
    fun decreaseLineHeight(decrement: Float = LINE_HEIGHT_INCREMENT) = impl.decreaseLineHeight(decrement)

    fun getCloseBookTreeOnNewBookSelected(): Boolean = impl.getCloseBookTreeOnNewBookSelected()
    fun setCloseBookTreeOnNewBookSelected(value: Boolean) = impl.setCloseBookTreeOnNewBookSelected(value)
}