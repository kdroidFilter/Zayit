package io.github.kdroidfilter.seforimapp.backup.drive

import io.github.kdroidfilter.seforimapp.backup.writeBytesAtomically
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Serializable
internal data class DriveAccount(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochMs: Long,
    val email: String? = null,
    val lastBackupEpochMs: Long? = null,
    // The last automatic check, even when nothing had changed (so an idle day is not checked hourly)
    val lastAutoCheckEpochMs: Long? = null,
    // The content of the last backup, so an unchanged one is not uploaded again
    val lastFingerprint: String? = null,
    // False while a Drive backup exists that this device has neither restored nor replaced:
    // backing up automatically would overwrite the data of another machine.
    val autoBackupArmed: Boolean = true,
    // Set once a restore is staged: the automatic backup resumes only after the app applied it,
    // otherwise it would back up the very data the restore replaces.
    val armWhenRestored: Boolean = false,
    val pendingRemoteBackupEpochMs: Long? = null,
)

/**
 * The connected account, encrypted with AES-GCM in owner-only files. The key sits next to the
 * data, so this keeps the tokens out of plain sight rather than safe from the user's own session.
 */
internal class DriveAccountStore(
    directory: File,
) {
    private val file = File(directory, "drive-account.bin")
    private val keyFile = File(directory, "drive-account.key")
    private val json = Json { ignoreUnknownKeys = true }
    private val random = SecureRandom()

    fun load(): DriveAccount? {
        if (!file.exists() || !keyFile.exists()) return null
        return runCatching {
            json.decodeFromString(DriveAccount.serializer(), decrypt(file.readBytes(), key()).decodeToString())
        }.getOrNull()
    }

    fun save(account: DriveAccount) {
        file.parentFile.mkdirs()
        // Atomically: a crash mid-write must not leave a damaged file, which reads as signed out.
        file.writeBytesAtomically(encrypt(json.encodeToString(DriveAccount.serializer(), account).toByteArray(), key()))
        file.restrictToOwner()
    }

    fun clear() {
        file.delete()
        keyFile.delete()
    }

    private fun key(): SecretKey {
        if (!keyFile.exists()) {
            keyFile.parentFile.mkdirs()
            keyFile.writeText(Base64.getEncoder().encodeToString(ByteArray(KEY_BYTES).also(random::nextBytes)))
            keyFile.restrictToOwner()
        }
        return SecretKeySpec(Base64.getDecoder().decode(keyFile.readText().trim()), "AES")
    }

    private fun encrypt(
        plain: ByteArray,
        key: SecretKey,
    ): ByteArray {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv)) }
        return iv + cipher.doFinal(plain)
    }

    private fun decrypt(
        blob: ByteArray,
        key: SecretKey,
    ): ByteArray {
        val cipher =
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, blob.copyOfRange(0, IV_BYTES)))
            }
        return cipher.doFinal(blob.copyOfRange(IV_BYTES, blob.size))
    }

    private fun File.restrictToOwner() {
        setReadable(false, false)
        setReadable(true, true)
        setWritable(false, false)
        setWritable(true, true)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BYTES = 32
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
