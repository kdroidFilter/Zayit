package io.github.kdroidfilter.seforimapp.backup.drive

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class DriveAccountStoreTest {
    private val directory = Files.createTempDirectory("drive-store").toFile().apply { deleteOnExit() }
    private val account = DriveAccount(accessToken = "access", refreshToken = "refresh-secret", expiresAtEpochMs = 42, email = "a@b.c")

    @Test
    fun account_round_trips_encrypted() {
        val store = DriveAccountStore(directory)
        store.save(account)

        assertEquals(account, DriveAccountStore(directory).load())
        val onDisk = directory.listFiles().orEmpty().joinToString { it.readBytes().decodeToString() }
        assertFalse("refresh-secret" in onDisk)
    }

    @Test
    fun clear_forgets_the_account() {
        val store = DriveAccountStore(directory)
        store.save(account)
        store.clear()

        assertNull(store.load())
    }
}
