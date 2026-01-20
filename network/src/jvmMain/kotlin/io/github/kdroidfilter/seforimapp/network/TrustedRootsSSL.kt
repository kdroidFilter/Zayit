package io.github.kdroidfilter.seforimapp.network

import org.jetbrains.nativecerts.NativeTrustedCertificates
import java.security.KeyStore
import java.security.SecureRandom
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Provides SSL/TLS configuration that uses native OS certificate stores.
 * This ensures all network calls respect system root certificates.
 *
 * This is particularly useful in enterprise environments with custom root certificates
 * or when using corporate proxies with HTTPS inspection.
 */
object TrustedRootsSSL {
    /**
     * Trust manager that uses both JVM default trusted roots and OS native trusted roots
     */
    val trustManager: X509TrustManager by lazy {
        // Start with JVM default trust store
        val defaultTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        defaultTmf.init(null as KeyStore?) // null loads the default JVM trust store

        val defaultTrustManager =
            defaultTmf.trustManagers
                .filterIsInstance<X509TrustManager>()
                .firstOrNull()

        // Create a new keystore that combines both JVM and OS certificates
        val combinedKeyStore =
            KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)

                // Add JVM default certificates
                defaultTrustManager?.acceptedIssuers?.forEachIndexed { index, cert ->
                    setCertificateEntry("jvm-default-$index", cert)
                }

                // Add OS native certificates
                NativeTrustedCertificates.getCustomOsSpecificTrustedCertificates().forEach { cert ->
                    setCertificateEntry(cert.subjectX500Principal.name, cert)
                }
            }

        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(combinedKeyStore)

        tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    /**
     * SSLContext configured with native trusted roots
     */
    val sslContext: SSLContext by lazy {
        SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), SecureRandom())
        }
    }

    /**
     * SSLSocketFactory configured with native trusted roots
     */
    val socketFactory: SSLSocketFactory by lazy {
        sslContext.socketFactory
    }
}
