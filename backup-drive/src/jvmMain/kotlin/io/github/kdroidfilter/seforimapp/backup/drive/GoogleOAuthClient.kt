package io.github.kdroidfilter.seforimapp.backup.drive

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("refresh_token") val refreshToken: String? = null,
    // The scopes the user actually granted: Google lets them uncheck each one on the consent screen
    val scope: String? = null,
) {
    val grantsDriveAppData: Boolean get() = scope == null || DRIVE_APPDATA_SCOPE in scope.split(' ')
}

internal const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

@Serializable
private data class UserInfo(
    val email: String? = null,
)

/** Google refused the refresh token: the user revoked the access or it expired. */
internal class AuthorizationRevokedException : IllegalStateException("Google Drive access was revoked")

/** The OAuth 2.0 "installed app" flow of Google: authorization URL, code exchange and refresh. */
internal class GoogleOAuthClient(
    private val http: HttpClient,
    private val clientId: String,
    private val clientSecret: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun authorizationUrl(
        redirectUri: String,
        state: String,
        codeChallenge: String,
    ): String =
        URLBuilder(AUTH_URL)
            .apply {
                parameters.append("client_id", clientId)
                parameters.append("redirect_uri", redirectUri)
                parameters.append("response_type", "code")
                parameters.append("scope", SCOPES)
                parameters.append("code_challenge", codeChallenge)
                parameters.append("code_challenge_method", "S256")
                parameters.append("state", state)
                // Offline access with consent, so Google returns a refresh token every time.
                parameters.append("access_type", "offline")
                parameters.append("prompt", "consent")
            }.buildString()

    suspend fun exchangeCode(
        code: String,
        codeVerifier: String,
        redirectUri: String,
    ): TokenResponse =
        requestToken(
            Parameters.build {
                append("code", code)
                append("code_verifier", codeVerifier)
                append("grant_type", "authorization_code")
                append("redirect_uri", redirectUri)
            },
        )

    suspend fun refresh(refreshToken: String): TokenResponse =
        requestToken(
            Parameters.build {
                append("refresh_token", refreshToken)
                append("grant_type", "refresh_token")
            },
        )

    suspend fun fetchEmail(accessToken: String): String? =
        runCatching {
            val response = http.get(USERINFO_URL) { header(HttpHeaders.Authorization, "Bearer $accessToken") }
            if (response.status.isSuccess()) json.decodeFromString(UserInfo.serializer(), response.bodyAsText()).email else null
        }.getOrNull()

    /** Best effort: the local tokens are dropped whatever Google answers. */
    suspend fun revoke(token: String) {
        runCatching { http.submitForm(REVOKE_URL, Parameters.build { append("token", token) }) }
    }

    private suspend fun requestToken(parameters: Parameters): TokenResponse {
        val response =
            http.submitForm(
                TOKEN_URL,
                Parameters.build {
                    append("client_id", clientId)
                    append("client_secret", clientSecret)
                    appendAll(parameters)
                },
            )
        val text = response.bodyAsText()
        if (response.status == HttpStatusCode.BadRequest && "invalid_grant" in text) throw AuthorizationRevokedException()
        check(response.status.isSuccess()) { "Token request failed: ${response.status} $text" }
        return json.decodeFromString(TokenResponse.serializer(), text)
    }

    private companion object {
        const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        const val REVOKE_URL = "https://oauth2.googleapis.com/revoke"
        const val USERINFO_URL = "https://openidconnect.googleapis.com/v1/userinfo"

        // The hidden app folder only: the app never sees the user's own files.
        const val SCOPES = "$DRIVE_APPDATA_SCOPE openid email"
    }
}
