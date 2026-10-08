package io.github.kdroidfilter.seforimapp.backup.drive

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

/** The few Drive v3 calls the backup needs, all inside the app's hidden `appDataFolder`. */
internal class DriveFilesApi(
    private val http: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    data class RemoteFile(
        val id: String,
        val modifiedEpochMs: Long,
    )

    @Serializable
    private data class FileJson(
        val id: String,
        val modifiedTime: String? = null,
    )

    @Serializable
    private data class FileListJson(
        val files: List<FileJson> = emptyList(),
    )

    /** Uploads a new file with its metadata in one multipart request and returns it. */
    suspend fun create(
        accessToken: String,
        name: String,
        content: ByteArray,
    ): RemoteFile {
        val boundary = "zayit-${System.nanoTime()}"
        val metadata = """{"name":"$name","mimeType":"$MIME_TYPE","parents":["appDataFolder"]}"""
        val body =
            "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n--$boundary\r\nContent-Type: $MIME_TYPE\r\n\r\n"
                .toByteArray() + content + "\r\n--$boundary--\r\n".toByteArray()
        val response =
            http.post(UPLOAD_URL) {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                parameter("uploadType", "multipart")
                parameter("fields", FILE_FIELDS)
                contentType(ContentType.parse("multipart/related; boundary=$boundary"))
                setBody(body)
            }
        return response.requireSuccess("create").toRemoteFile()
    }

    /** The files named [name], the most recently modified first. */
    suspend fun listLatestFirst(
        accessToken: String,
        name: String,
        limit: Int = MAX_PAGE_SIZE,
    ): List<RemoteFile> {
        val response =
            http.get(FILES_URL) {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                parameter("spaces", "appDataFolder")
                parameter("q", "name = '$name' and trashed = false")
                parameter("fields", "files($FILE_FIELDS)")
                parameter("orderBy", "modifiedTime desc")
                parameter("pageSize", limit)
            }
        val files = json.decodeFromString(FileListJson.serializer(), response.requireSuccess("list").bodyAsText()).files
        return files.map { it.toRemoteFile() }
    }

    /** The most recently modified file named [name], or null when there is none. */
    suspend fun findLatest(
        accessToken: String,
        name: String,
    ): RemoteFile? = listLatestFirst(accessToken, name, limit = 1).firstOrNull()

    suspend fun delete(
        accessToken: String,
        fileId: String,
    ) {
        http.delete("$FILES_URL/$fileId") { header(HttpHeaders.Authorization, "Bearer $accessToken") }.requireSuccess("delete")
    }

    suspend fun download(
        accessToken: String,
        fileId: String,
    ): ByteArray =
        http
            .get("$FILES_URL/$fileId") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                parameter("alt", "media")
            }.requireSuccess("download")
            .readRawBytes()

    private suspend fun HttpResponse.requireSuccess(call: String): HttpResponse {
        if (status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden) {
            throw DriveAccessDeniedException("Drive $call failed: $status ${bodyAsText()}")
        }
        check(status.isSuccess()) { "Drive $call failed: $status ${bodyAsText()}" }
        return this
    }

    private suspend fun HttpResponse.toRemoteFile(): RemoteFile = json.decodeFromString(FileJson.serializer(), bodyAsText()).toRemoteFile()

    private fun FileJson.toRemoteFile(): RemoteFile =
        RemoteFile(
            id = id,
            modifiedEpochMs =
                modifiedTime?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                    ?: System.currentTimeMillis(),
        )

    private companion object {
        const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
        const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
        const val FILE_FIELDS = "id,modifiedTime"
        const val MIME_TYPE = "application/zip"
        const val MAX_PAGE_SIZE = 100
    }
}

/** Drive refused a valid sign-in: the access was not granted, or Drive is off for the account (a managed Workspace one). */
internal class DriveAccessDeniedException(
    message: String,
) : IllegalStateException(message)
