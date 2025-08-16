package io.github.kdroidfilter.seforimapp.core.settings

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for managing application settings and preferences that persist across app restarts.
 * Uses Multiplatform Settings library for cross-platform storage.
 */
interface IAppSettings {
    // StateFlow to observe text size changes
    val textSizeFlow: StateFlow<Float>

    // StateFlow to observe line height changes
    val lineHeightFlow: StateFlow<Float>

    // StateFlow to observe auto-close book tree setting changes
    val closeBookTreeOnNewBookSelectedFlow: StateFlow<Boolean>

    /**
     * Gets the current text size from settings
     * @return The text size in sp
     */
    fun getTextSize(): Float

    /**
     * Sets the text size and updates the flow
     * @param size The new text size in sp
     */
    fun setTextSize(size: Float)

    /**
     * Increases the text size by the specified increment
     * @param increment The amount to increase (default is TEXT_SIZE_INCREMENT)
     */
    fun increaseTextSize(increment: Float = AppSettings.TEXT_SIZE_INCREMENT)

    /**
     * Decreases the text size by the specified decrement
     * @param decrement The amount to decrease (default is TEXT_SIZE_INCREMENT)
     */
    fun decreaseTextSize(decrement: Float = AppSettings.TEXT_SIZE_INCREMENT)

    /**
     * Gets the current line height from settings
     * @return The line height multiplier
     */
    fun getLineHeight(): Float

    /**
     * Sets the line height and updates the flow
     * @param height The new line height multiplier
     */
    fun setLineHeight(height: Float)

    /**
     * Increases the line height by the specified increment
     * @param increment The amount to increase (default is LINE_HEIGHT_INCREMENT)
     */
    fun increaseLineHeight(increment: Float = AppSettings.LINE_HEIGHT_INCREMENT)

    /**
     * Decreases the line height by the specified decrement
     * @param decrement The amount to decrease (default is LINE_HEIGHT_INCREMENT)
     */
    fun decreaseLineHeight(decrement: Float = AppSettings.LINE_HEIGHT_INCREMENT)

    /**
     * Gets/sets whether to close the Book Tree pane automatically when selecting a new book
     */
    fun getCloseBookTreeOnNewBookSelected(): Boolean
    fun setCloseBookTreeOnNewBookSelected(value: Boolean)
}