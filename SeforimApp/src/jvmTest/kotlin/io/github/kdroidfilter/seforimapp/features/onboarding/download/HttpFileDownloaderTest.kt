package io.github.kdroidfilter.seforimapp.features.onboarding.download

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class HttpFileDownloaderTest {
    private val content = Random(42).nextBytes(300_000)
    private val sha = MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }
    private val dir = Files.createTempDirectory("http-download").toFile()
    private val dest = dir.resolve("seforim_bundle.tar.zst.part01")
    private val requests = AtomicInteger()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val downloader = HttpFileDownloader(retryDelayMs = 1)

    private val url get() = "http://127.0.0.1:${server.address.port}/file"

    /** Serves [content]; the first [drops] responses are cut after 100 kB. */
    private fun serve(
        honorRange: Boolean,
        drops: Int = 1,
    ) {
        server.createContext("/file") { exchange ->
            val attempt = requests.incrementAndGet()
            val range = exchange.requestHeaders.getFirst("Range")
            val from = if (honorRange && range != null) range.removePrefix("bytes=").removeSuffix("-").toInt() else 0
            val body = content.copyOfRange(from, content.size)
            exchange.sendResponseHeaders(if (from > 0) 206 else 200, body.size.toLong())
            exchange.responseBody.use { out ->
                if (attempt <= drops) out.write(body, 0, minOf(100_000, body.size)) else out.write(body)
            }
            exchange.close()
        }
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.stop(0)
        dir.deleteRecursively()
    }

    @Test
    fun `resumes from the last byte after a dropped connection`() =
        runBlocking {
            serve(honorRange = true, drops = 2)

            downloader.download(url, dest, content.size.toLong(), sha)

            assertContentEquals(content, dest.readBytes())
            assertEquals(3, requests.get())
        }

    @Test
    fun `restarts cleanly when the server ignores Range`() =
        runBlocking {
            serve(honorRange = false)

            downloader.download(url, dest, content.size.toLong(), sha)

            assertContentEquals(content, dest.readBytes())
        }

    @Test
    fun `rejects a sha256 mismatch`() {
        serve(honorRange = true, drops = 0)

        assertFailsWith<IllegalStateException> {
            runBlocking { downloader.download(url, dest, content.size.toLong(), "00") }
        }
        assertFalse(dest.exists())
    }

    @Test
    fun `gives up after repeated failures`() {
        serve(honorRange = true, drops = Int.MAX_VALUE)
        server.removeContext("/file")
        server.createContext("/file") { exchange ->
            requests.incrementAndGet()
            exchange.sendResponseHeaders(503, -1)
            exchange.close()
        }

        assertFailsWith<java.io.IOException> {
            runBlocking { downloader.download(url, dest, content.size.toLong(), sha) }
        }
        assertEquals(6, requests.get())
    }
}
