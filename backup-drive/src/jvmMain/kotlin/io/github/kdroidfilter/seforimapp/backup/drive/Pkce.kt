package io.github.kdroidfilter.seforimapp.backup.drive

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Proof Key for Code Exchange (RFC 7636), which lets a desktop app use OAuth without a real secret. */
internal object Pkce {
    private val random = SecureRandom()
    private val urlEncoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

    fun newVerifier(): String = urlEncoder.encodeToString(ByteArray(64).also(random::nextBytes))

    fun challenge(verifier: String): String =
        urlEncoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
}
