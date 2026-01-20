package io.github.kdroidfilter.seforimapp.core.presentation.utils

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import java.awt.Cursor

fun Modifier.cursorForHorizontalResize(): Modifier = pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))

fun Modifier.cursorForVerticalResize(): Modifier = pointerHoverIcon(PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR)))
