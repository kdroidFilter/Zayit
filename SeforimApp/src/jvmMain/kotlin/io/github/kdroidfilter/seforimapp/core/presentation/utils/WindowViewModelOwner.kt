package io.github.kdroidfilter.seforimapp.core.presentation.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

val LocalWindowViewModelStoreOwner: ProvidableCompositionLocal<ViewModelStoreOwner> =
    staticCompositionLocalOf {
        error("No Window ViewModelStoreOwner provided")
    }

@Composable
fun rememberWindowViewModelStoreOwner(): ViewModelStoreOwner {
    val owner = remember { WindowViewModelStoreOwner() }
    DisposableEffect(owner) {
        onDispose { owner.clear() }
    }
    return owner
}

private class WindowViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()

    fun clear() {
        viewModelStore.clear()
    }
}
