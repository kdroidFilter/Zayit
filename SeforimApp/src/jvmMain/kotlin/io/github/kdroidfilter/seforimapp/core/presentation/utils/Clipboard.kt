package io.github.kdroidfilter.seforimapp.core.presentation.utils

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/** Puts [text] on the system clipboard. */
fun copyToClipboard(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}
