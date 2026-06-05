package io.github.kdroidfilter.seforimapp.core.presentation.utils

import io.github.kdroidfilter.seforimapp.logger.errorln
import java.awt.Desktop
import java.net.URI

/**
 * Opens an external URL without blocking the UI thread.
 *
 * `Desktop.browse` is synchronous and, on Linux, blocks the calling thread until the system
 * handler (`xdg-open`) responds — calling it from the Compose/Skiko render thread freezes the app.
 * This runs the open on a short-lived daemon thread and falls back to the platform opener command
 * when AWT Desktop is unavailable (common on Linux).
 */
object UrlOpener {
    fun open(url: String) {
        Thread {
            runCatching { browseOrFallback(url) }
                .onFailure { error -> errorln { "[url] failed to open $url: ${error.message}" } }
        }.apply {
            isDaemon = true
            name = "url-opener"
        }.start()
    }

    private fun browseOrFallback(url: String) {
        val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
        if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
            desktop.browse(URI.create(url))
        } else {
            fallback(url)
        }
    }

    private fun fallback(url: String) {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val command =
            when {
                os.contains("mac") -> listOf("open", url)
                os.contains("win") -> listOf("rundll32", "url.dll,FileProtocolHandler", url)
                else -> listOf("xdg-open", url)
            }
        ProcessBuilder(command).start()
    }
}
