package io.github.kdroidfilter.seforimapp.backup.drive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class LoopbackCodeReceiverTest {
    private val browser = HttpClient.newHttpClient()

    private suspend fun visit(url: String): HttpResponse<String> =
        withContext(Dispatchers.IO) {
            browser.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString())
        }

    @Test
    fun returns_the_code_and_ignores_other_requests() =
        runBlocking<Unit> {
            LoopbackCodeReceiver { success -> "ok=$success" }.use { receiver ->
                val code = async { receiver.awaitCode("s1") }
                val base = receiver.redirectUri.removeSuffix("/callback")

                assertEquals(404, visit("$base/favicon.ico").statusCode())
                assertEquals("ok=true", visit("${receiver.redirectUri}?state=s1&code=4%2Fabc").body())
                assertEquals("4/abc", code.await())
            }
        }

    @Test
    fun rejects_a_foreign_state() =
        runBlocking<Unit> {
            LoopbackCodeReceiver { success -> "ok=$success" }.use { receiver ->
                val code = async { runCatching { receiver.awaitCode("expected") } }

                assertEquals("ok=false", visit("${receiver.redirectUri}?state=forged&code=x").body())
                assertFailsWith<IllegalStateException> { code.await().getOrThrow() }
            }
        }

    @Test
    fun an_idle_preconnection_does_not_block_the_redirect() =
        runBlocking<Unit> {
            LoopbackCodeReceiver { success -> "ok=$success" }.use { receiver ->
                val code = async { receiver.awaitCode("s1") }
                val port = URI.create(receiver.redirectUri).port
                // Like a browser preconnecting: a connection that never sends a request.
                Socket("127.0.0.1", port).use {
                    assertEquals("ok=true", visit("${receiver.redirectUri}?state=s1&code=abc").body())
                    assertEquals("abc", withTimeout(10.seconds) { code.await() })
                }
            }
        }
}
