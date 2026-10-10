package io.github.kdroidfilter.seforimapp.backup.drive

import io.github.kdroidfilter.seforimapp.backup.BackupManager
import io.github.kdroidfilter.seforimapp.backup.BackupSnapshot
import io.github.kdroidfilter.seforimapp.backup.BackupSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The backup on quit doesn't hold the app's windows: [GoogleDriveSync.backupOnQuit] returns at once and the upload
 * finishes while the process exits. Run in a child JVM, as the app exits through System.exit.
 */
class QuitBackupTest {
    @Test
    fun the_quit_backup_returns_at_once_and_uploads_while_the_process_exits() {
        val dir = Files.createTempDirectory("quit-backup").toFile()
        val java = File(System.getProperty("java.home"), "bin/java").path
        val process =
            ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), QuitBackupProbe::class.java.name, dir.path)
                .redirectErrorStream(true)
                .start()
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "the probe exits")
        val output = process.inputStream.bufferedReader().readText()

        val returnedInMs =
            Regex("returned in (\\d+) ms")
                .find(output)
                ?.groupValues
                ?.get(1)
                ?.toLong()
        assertTrue(returnedInMs != null && returnedInMs < QuitBackupProbe.UPLOAD_DELAY_MS / 3, output)
        assertTrue(File(dir, QuitBackupProbe.UPLOADED).exists(), "the backup was uploaded before the exit\n$output")
        dir.deleteRecursively()
    }
}

/** The child process: an app with a Drive account whose upload is slow, quitting. */
object QuitBackupProbe {
    const val UPLOAD_DELAY_MS = 3_000L
    const val UPLOADED = "uploaded"

    @JvmStatic
    fun main(args: Array<String>) {
        val dir = File(args[0])
        DriveAccountStore(dir).save(DriveAccount("token", "refresh", expiresAtEpochMs = Long.MAX_VALUE))
        val http =
            HttpClient(
                MockEngine { request ->
                    val json = headersOf("Content-Type", "application/json")
                    if (request.method == HttpMethod.Post) {
                        delay(UPLOAD_DELAY_MS)
                        File(dir, UPLOADED).createNewFile()
                        respond("""{"id":"new","modifiedTime":"${Instant.now()}"}""", headers = json)
                    } else {
                        respond("""{"files":[]}""", headers = json)
                    }
                },
            )
        val source =
            object : BackupSource {
                override fun snapshot() = BackupSnapshot("changed") { byteArrayOf(1, 2, 3) }

                override fun hasPendingRestore() = false

                override fun stageRestore(backup: ByteArray) = Unit
            }
        val sync =
            GoogleDriveSync(
                http,
                BackupManager(source),
                DriveSyncConfig("id", "secret", "backup.zip", { dir }, {}, {}),
            )
        val start = System.nanoTime()
        sync.backupOnQuit()
        println("returned in ${(System.nanoTime() - start) / 1_000_000} ms")
        exitProcess(0)
    }
}
