package io.github.kdroidfilter.seforimapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Shared app entry composable used by the Android and iOS hosts.
 *
 * ponytail: stub for now — the full desktop UI is the Jewel-based window in jvmMain/main.kt,
 * which can't run on mobile. Implementing the real mobile UI (and the search engine) per platform
 * via expect/actual is the remaining work; this keeps Android/iOS launching with a placeholder.
 */
@Composable
fun App() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BasicText("זית")
    }
}
