package io.github.kdroidfilter.seforimapp.network

import io.github.kdroidfilter.nucleus.nativessl.NativeTrustManager
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * Provides SSL/TLS configuration that uses native OS certificate stores.
 * Delegates to Nucleus's NativeTrustManager which merges OS certs with JVM defaults.
 */
object TrustedRootsSSL {
    val trustManager: X509TrustManager get() = NativeTrustManager.trustManager
    val sslContext get() = NativeTrustManager.sslContext
    val socketFactory: SSLSocketFactory get() = NativeTrustManager.sslSocketFactory
}
