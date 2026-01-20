package io.github.kdroidfilter.seforimapp.features.bookcontent.state

/**
 * Centralized defaults for split panes in BookContent screen.
 * - MAIN, TOC are default position percentages (0..1)
 * - MIN_MAIN, MIN_TOC are minimum widths in pixels for the first pane
 */
object SplitDefaults {
    // Default position percentages
    const val MAIN: Float = 0.05f // default for mainSplitState (Category panel)
    const val TOC: Float = 0.05f // default for tocSplitState (TOC panel)
    const val CONTENT: Float = 0.7f // default for contentSplitState (Commentaries panel)
    const val SOURCES: Float = 0.85f // default for Sources panel (smaller than Commentaries)

    // Minimum sizes (pixels) for the first pane of each split
    const val MIN_MAIN: Float = 150f
    const val MIN_TOC: Float = 120f
}
