package io.github.kdroidfilter.seforimapp.core.presentation.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import io.github.kdroidfilter.seforimapp.core.e2e.E2e
import kotlinx.coroutines.launch
import java.awt.datatransfer.StringSelection

/** Puts [text] on this clipboard: the window's own (Nucleus's native one), which other apps paste from. */
@OptIn(ExperimentalComposeUiApi::class)
suspend fun Clipboard.setText(text: String) {
    if (E2e.enabled) E2e.copiedText = text
    setClipEntry(ClipEntry(StringSelection(text)))
}

/** Copies a text to the window's clipboard ([LocalClipboard]), from any callback. */
@Composable
fun rememberCopyToClipboard(): (String) -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return remember(clipboard, scope) { { text -> scope.launch { clipboard.setText(text) } } }
}
