package io.github.kdroidfilter.seforimapp.framework.update

import com.sun.net.httpserver.HttpServer
import dev.nucleusframework.core.runtime.Platform
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * N4 — exercises the real Nucleus pipeline (checkForUpdates + downloadUpdate + SHA-512 verification
 * via the native SSL client) against a local HTTP feed, with a dry-run install. No GitHub, no installer.
 */
class UpdateFeedIntegrationTest {
    private data class PlatformBits(
        val ymlName: String,
        val installerName: String,
        val executableType: String,
    )

    private fun platformBits(): PlatformBits {
        val arch =
            if (System
                    .getProperty(
                        "os.arch",
                    ).lowercase()
                    .let { it.contains("aarch64") || it.contains("arm64") }
            ) {
                "arm64"
            } else {
                "amd64"
            }
        return when (Platform.Current) {
            Platform.MacOS -> PlatformBits("latest-mac.yml", "zayit-1.0.1-mac-$arch.zip", "zip")
            Platform.Windows -> PlatformBits("latest.yml", "zayit-1.0.1-windows-$arch.exe", "exe")
            else -> PlatformBits("latest-linux.yml", "zayit-1.0.1-linux-$arch.deb", "deb")
        }
    }

    private fun sha512Base64(file: File): String =
        Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-512").digest(file.readBytes()))

    private fun startServer(dir: File): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val file = File(dir, exchange.requestURI.path.removePrefix("/"))
            if (file.exists()) {
                val bytes = file.readBytes()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            } else {
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
            }
        }
        server.start()
        return server
    }

    private fun writeFeed(
        dir: File,
        bits: PlatformBits,
        sha512: String,
        size: Long,
    ) {
        File(dir, bits.ymlName).writeText(
            """
            version: 1.0.1
            files:
              - url: ${bits.installerName}
                sha512: $sha512
                size: $size
            releaseDate: '2026-06-04T10:00:00.000Z'
            """.trimIndent(),
        )
    }

    @Test
    fun `real pipeline downloads and verifies a patch update`() {
        val dir = Files.createTempDirectory("zayit-feed").toFile()
        val bits = platformBits()
        val installer = File(dir, bits.installerName).apply { writeBytes(ByteArray(4096) { (it % 251).toByte() }) }
        writeFeed(dir, bits, sha512Base64(installer), installer.length())

        val server = startServer(dir)
        try {
            val port = server.address.port
            val service =
                AppUpdateService.create(
                    config =
                        AppUpdateConfig(
                            forceVersion = "1.0.0", // → 1.0.1 is a PATCH bump → pre-downloaded on all platforms
                            feedUrl = "http://127.0.0.1:$port",
                            dryRun = true,
                            executableType = bits.executableType,
                        ),
                    os = Platform.Current,
                )

            runBlocking { service.checkOnStartup() }

            val state = service.state.value
            assertIs<UpdateUiState.ReadyToInstall>(state, "expected ReadyToInstall but was $state")
            assertEquals("1.0.1", state.version)
            assertTrue(state.file.exists(), "downloaded installer should exist on disk")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `checksum mismatch yields error state`() {
        val dir = Files.createTempDirectory("zayit-feed-bad").toFile()
        val bits = platformBits()
        File(dir, bits.installerName).writeBytes(ByteArray(2048) { 1 })
        writeFeed(dir, bits, sha512 = "AAAAINVALIDCHECKSUMAAAA==", size = 2048)

        val server = startServer(dir)
        try {
            val port = server.address.port
            val service =
                AppUpdateService.create(
                    config =
                        AppUpdateConfig(
                            forceVersion = "1.0.0",
                            feedUrl = "http://127.0.0.1:$port",
                            dryRun = true,
                            executableType = bits.executableType,
                        ),
                    os = Platform.Current,
                )

            runBlocking { service.checkOnStartup() }

            assertIs<UpdateUiState.Error>(service.state.value)
        } finally {
            server.stop(0)
        }
    }
}
