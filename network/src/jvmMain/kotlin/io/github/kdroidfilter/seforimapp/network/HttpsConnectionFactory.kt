package io.github.kdroidfilter.seforimapp.network

import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Factory for creating HttpURLConnection instances configured with native trusted roots.
 */
object HttpsConnectionFactory {
    /**
     * Opens a connection to the given URL and configures it with native trusted roots
     * if it's an HTTPS connection.
     *
     * @param url The URL to connect to
     * @param configure Optional lambda to configure the connection before it's returned
     * @return Configured HttpURLConnection
     */
    fun openConnection(
        url: URL,
        configure: (HttpURLConnection.() -> Unit)? = null,
    ): HttpURLConnection {
        val connection = url.openConnection() as HttpURLConnection

        // Configure HTTPS with native trusted roots
        if (connection is HttpsURLConnection) {
            connection.sslSocketFactory = TrustedRootsSSL.socketFactory
        }

        // Apply custom configuration if provided
        configure?.invoke(connection)

        return connection
    }

    /**
     * Opens a connection to the given URL string and configures it with native trusted roots
     * if it's an HTTPS connection.
     *
     * @param urlString The URL string to connect to
     * @param configure Optional lambda to configure the connection before it's returned
     * @return Configured HttpURLConnection
     */
    fun openConnection(
        urlString: String,
        configure: (HttpURLConnection.() -> Unit)? = null,
    ): HttpURLConnection = openConnection(URL(urlString), configure)
}
