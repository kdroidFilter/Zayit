package io.github.kdroidfilter.seforimapp.backup.drive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLDecoder

/**
 * Receives the OAuth redirect of the system browser on a loopback port. A bare [ServerSocket]
 * rather than `com.sun.net.httpserver`: one request to read, no extra JDK module for the
 * jlink image nor reachability metadata for the native one.
 */
internal class LoopbackCodeReceiver(
    private val page: (success: Boolean) -> String,
) : Closeable {
    // Bound to the loopback address only, which also avoids the Windows firewall prompt.
    private val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).apply { soTimeout = ACCEPT_POLL_MS }

    val redirectUri: String = "http://127.0.0.1:${server.localPort}/callback"

    /** Waits for the redirect and returns its authorization code; cancellable between polls. */
    suspend fun awaitCode(expectedState: String): String =
        withContext(Dispatchers.IO) {
            var outcome: Result<String>? = null
            while (outcome == null) {
                ensureActive()
                outcome =
                    try {
                        server.accept().use { socket ->
                            // A browser may open a connection ahead and send nothing on it: give up on it
                            // instead of blocking the real redirect, and the cancellation, behind it.
                            socket.soTimeout = READ_TIMEOUT_MS
                            handle(socket, expectedState)
                        }
                    } catch (_: SocketTimeoutException) {
                        // Nothing to accept yet, or an idle connection
                        null
                    }
            }
            outcome.getOrThrow()
        }

    /** The outcome of the redirect, or null for any other request (the browser's favicon…). */
    private fun handle(
        socket: Socket,
        expectedState: String,
    ): Result<String>? {
        // "GET /callback?code=…&state=… HTTP/1.1"
        val target =
            socket
                .getInputStream()
                .bufferedReader()
                .readLine()
                ?.split(' ')
                ?.getOrNull(1)
        if (target == null || !target.startsWith("/callback")) {
            respond(socket, "404 Not Found", "")
            return null
        }
        val params = parseQuery(target.substringAfter('?', ""))
        val code = params["code"]
        val result =
            when {
                params["error"] != null -> Result.failure(IllegalStateException("OAuth error: ${params["error"]}"))
                params["state"] != expectedState -> Result.failure(IllegalStateException("OAuth state mismatch"))
                code.isNullOrEmpty() -> Result.failure(IllegalStateException("Missing authorization code"))
                else -> Result.success(code)
            }
        respond(socket, "200 OK", page(result.isSuccess))
        return result
    }

    private fun respond(
        socket: Socket,
        status: String,
        html: String,
    ) {
        val body = html.toByteArray()
        val head = "HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply {
            write(head.toByteArray())
            write(body)
            flush()
        }
    }

    private fun parseQuery(query: String): Map<String, String> =
        query
            .split('&')
            .filter { '=' in it }
            .associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8) }

    override fun close() = server.close()

    private companion object {
        const val ACCEPT_POLL_MS = 500
        const val READ_TIMEOUT_MS = 2_000
    }
}
