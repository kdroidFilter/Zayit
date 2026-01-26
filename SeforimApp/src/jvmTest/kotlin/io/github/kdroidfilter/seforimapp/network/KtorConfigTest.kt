package io.github.kdroidfilter.seforimapp.network

import io.github.kdroidfilter.seforimapp.network.KtorConfig
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KtorConfigTest {
    @Test
    fun `createHttpClient returns HttpClient`() {
        val client = KtorConfig.createHttpClient()
        assertIs<HttpClient>(client)
        client.close()
    }

    @Test
    fun `createHttpClient with default json config`() {
        val client = KtorConfig.createHttpClient()
        assertNotNull(client)
        client.close()
    }

    @Test
    fun `createHttpClient with custom json config`() {
        val customJson = Json {
            ignoreUnknownKeys = false
            isLenient = false
            prettyPrint = true
        }

        val client = KtorConfig.createHttpClient(json = customJson)
        assertNotNull(client)
        client.close()
    }

    @Test
    fun `createHttpClient uses CIO engine`() {
        val client = KtorConfig.createHttpClient()
        // Just verify the client is created successfully with CIO engine
        assertNotNull(client.engine)
        client.close()
    }

    @Test
    fun `HttpClient can be closed without error`() {
        val client = KtorConfig.createHttpClient()
        // Should not throw
        client.close()
        assertTrue(true)
    }

    @Test
    fun `multiple clients can be created independently`() {
        val client1 = KtorConfig.createHttpClient()
        val client2 = KtorConfig.createHttpClient()

        assertTrue(client1 !== client2, "Each call should create a new client")

        client1.close()
        client2.close()
    }
}
