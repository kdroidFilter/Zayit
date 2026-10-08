package io.github.kdroidfilter.seforimapp.backup.drive

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GoogleDriveDestinationTest {
    // A fake appDataFolder: file id to its modification time
    private val files = linkedMapOf("old" to 1_000L)
    private val deleted = mutableListOf<String>()
    private var created = 0

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when {
                    request.method == HttpMethod.Post -> {
                        val id = "new${++created}"
                        files[id] = 2_000L + created
                        respond("""{"id":"$id","modifiedTime":"${Instant.ofEpochMilli(files.getValue(id))}"}""", headers = json)
                    }
                    request.method == HttpMethod.Delete -> {
                        val id = request.url.encodedPath.substringAfterLast('/')
                        files.remove(id)
                        deleted += id
                        respond("", HttpStatusCode.NoContent)
                    }
                    else -> {
                        val limit = request.url.parameters["pageSize"]!!.toInt()
                        val listed =
                            files.entries
                                .sortedByDescending { it.value }
                                .take(limit)
                                .joinToString { """{"id":"${it.key}","modifiedTime":"${Instant.ofEpochMilli(it.value)}"}""" }
                        respond("""{"files":[$listed]}""", headers = json)
                    }
                }
            },
        )

    private fun destination(
        expectedLatestEpochMs: Long?,
        keep: Int = 10,
    ) = GoogleDriveDestination(DriveFilesApi(http), { "token" }, "backup.zip", expectedLatestEpochMs, keep)

    @Test
    fun a_newer_backup_from_another_device_is_not_overwritten() =
        runTest {
            val error = assertFailsWith<RemoteBackupChangedException> { destination(expectedLatestEpochMs = 500L).write(byteArrayOf(1)) }

            assertEquals(1_000L, error.remoteEpochMs)
            assertEquals(0, created)
        }

    @Test
    fun replacing_on_purpose_ignores_the_newer_backup() =
        runTest {
            destination(expectedLatestEpochMs = null).write(byteArrayOf(1))

            assertEquals(1, created)
        }

    @Test
    fun each_backup_is_a_new_file_and_only_the_latest_are_kept() =
        runTest {
            repeat(3) { destination(expectedLatestEpochMs = null, keep = 2).write(byteArrayOf(1)) }

            assertEquals(listOf("old", "new1"), deleted)
            assertTrue(files.keys.containsAll(listOf("new2", "new3")))
        }
}
