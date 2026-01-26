package io.github.kdroidfilter.seforimapp.network

import io.github.kdroidfilter.seforimapp.network.TrustedRootsSSL
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TrustedRootsSSLTest {
    @Test
    fun `trustManager is not null`() {
        assertNotNull(TrustedRootsSSL.trustManager)
    }

    @Test
    fun `trustManager is X509TrustManager`() {
        assertIs<X509TrustManager>(TrustedRootsSSL.trustManager)
    }

    @Test
    fun `trustManager has accepted issuers`() {
        val issuers = TrustedRootsSSL.trustManager.acceptedIssuers
        assertNotNull(issuers)
        assertTrue(issuers.isNotEmpty(), "TrustManager should have accepted issuers")
    }

    @Test
    fun `sslContext is not null`() {
        assertNotNull(TrustedRootsSSL.sslContext)
    }

    @Test
    fun `sslContext is SSLContext`() {
        assertIs<SSLContext>(TrustedRootsSSL.sslContext)
    }

    @Test
    fun `sslContext protocol is TLS`() {
        assertTrue(
            TrustedRootsSSL.sslContext.protocol.contains("TLS", ignoreCase = true),
            "SSL context protocol should be TLS",
        )
    }

    @Test
    fun `socketFactory is not null`() {
        assertNotNull(TrustedRootsSSL.socketFactory)
    }

    @Test
    fun `socketFactory is SSLSocketFactory`() {
        assertIs<SSLSocketFactory>(TrustedRootsSSL.socketFactory)
    }

    @Test
    fun `socketFactory has supported cipher suites`() {
        val cipherSuites = TrustedRootsSSL.socketFactory.supportedCipherSuites
        assertNotNull(cipherSuites)
        assertTrue(cipherSuites.isNotEmpty(), "Socket factory should have supported cipher suites")
    }

    @Test
    fun `trustManager is lazy initialized and reused`() {
        val tm1 = TrustedRootsSSL.trustManager
        val tm2 = TrustedRootsSSL.trustManager
        assertTrue(tm1 === tm2, "TrustManager should be the same instance")
    }

    @Test
    fun `sslContext is lazy initialized and reused`() {
        val ctx1 = TrustedRootsSSL.sslContext
        val ctx2 = TrustedRootsSSL.sslContext
        assertTrue(ctx1 === ctx2, "SSLContext should be the same instance")
    }

    @Test
    fun `socketFactory is lazy initialized and reused`() {
        val sf1 = TrustedRootsSSL.socketFactory
        val sf2 = TrustedRootsSSL.socketFactory
        assertTrue(sf1 === sf2, "SocketFactory should be the same instance")
    }

    @Test
    fun `accepted issuers contain known root CAs`() {
        val issuers = TrustedRootsSSL.trustManager.acceptedIssuers
        val issuerNames = issuers.map { it.subjectX500Principal.name.lowercase() }

        // Check that at least some well-known root CAs are present
        val hasKnownCA = issuerNames.any { name ->
            name.contains("digicert") ||
                name.contains("globalsign") ||
                name.contains("comodo") ||
                name.contains("entrust") ||
                name.contains("verisign") ||
                name.contains("geotrust") ||
                name.contains("godaddy") ||
                name.contains("amazon") ||
                name.contains("google") ||
                name.contains("microsoft") ||
                name.contains("let's encrypt") ||
                name.contains("isrg") ||
                name.contains("baltimore")
        }

        assertTrue(hasKnownCA, "Should contain at least one known root CA")
    }
}
