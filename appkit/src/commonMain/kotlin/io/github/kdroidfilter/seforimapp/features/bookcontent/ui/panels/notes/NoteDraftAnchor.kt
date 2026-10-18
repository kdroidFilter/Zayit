package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.notes

/**
 * A pending (not-yet-persisted) note anchored to a resolved character range. Created from the
 * "add note" context-menu action, or implicitly as a blank whole-line note when the selected line
 * carries none yet.
 *
 * @property quote The anchored passage, shown as a greyed preview above the editor.
 */
data class NoteDraftAnchor(
    val lineId: Long,
    val startOffset: Int,
    val endOffset: Int,
    val quote: String,
)
