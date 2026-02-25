package io.github.kdroidfilter.seforimapp.network

import io.github.kdroidfilter.nucleus.nativehttp.ktor.installNativeSsl
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Provides a configured Ktor HttpClient that uses native OS certificate stores.
 */
object KtorConfig {
    /**
     * Creates a Ktor HttpClient configured with native trusted roots via Nucleus.
     *
     * @param json Custom JSON configuration (default: ignoreUnknownKeys + isLenient)
     * @return Configured HttpClient instance
     */
    fun createHttpClient(
        json: Json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            },
    ): HttpClient =
        HttpClient(CIO) {
            installNativeSsl()
            install(ContentNegotiation) {
                json(json)
            }
        }
}
