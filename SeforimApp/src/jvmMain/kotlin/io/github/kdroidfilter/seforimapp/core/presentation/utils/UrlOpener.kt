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

    // On macOS and Windows, `Desktop.browse` hands any URI to the default browser, so a mailto goes through
    // `Desktop.mail`, and on Windows another deep link (ms-windows-store:, …) through the shell's protocol handler
    private fun browseOrFallback(url: String) {
        val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
        val uri = URI.create(url)
        val isWeb = uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)
        when {
            isWindows && !isWeb && !uri.scheme.equals("mailto", ignoreCase = true) -> fallback(url)
            uri.scheme.equals("mailto", ignoreCase = true) && desktop?.isSupported(Desktop.Action.MAIL) == true ->
                desktop.mail(uri)
            desktop?.isSupported(Desktop.Action.BROWSE) == true -> desktop.browse(uri)
            else -> fallback(url)
        }
    }

    private val os = System.getProperty("os.name").orEmpty().lowercase()
    private val isWindows = os.contains("win")

    private fun fallback(url: String) {
        val command =
            when {
                os.contains("mac") -> listOf("open", url)
                isWindows -> listOf("rundll32", "url.dll,FileProtocolHandler", url)
                else -> listOf("xdg-open", url)
            }
        ProcessBuilder(command).start()
    }
}
