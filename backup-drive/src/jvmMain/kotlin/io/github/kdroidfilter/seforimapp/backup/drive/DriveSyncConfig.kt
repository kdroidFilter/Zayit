package io.github.kdroidfilter.seforimapp.backup.drive

import java.io.File

class DriveSyncConfig(
    val clientId: String,
    val clientSecret: String,
    // The name of the backup in the app's hidden Drive folder
    val backupFileName: String,
    // Where the connected account is kept; resolved on first use
    val storageDirectory: () -> File,
    // Opens the Google sign-in page in the system browser
    val openUrl: (String) -> Unit,
    // Applies a staged restore, typically by restarting the app
    val onRestoreStaged: () -> Unit,
    // The page the browser shows once the sign-in returns to the app
    val signInPage: (success: Boolean) -> String = ::defaultSignInPage,
)

private fun defaultSignInPage(success: Boolean): String {
    val message = if (success) "Connected to Google Drive." else "Could not connect to Google Drive."
    return """
        <!doctype html><html><head><meta charset="utf-8"><title>Google Drive</title>
        <style>body{font-family:system-ui,sans-serif;display:flex;align-items:center;justify-content:center;
        min-height:100vh;margin:0;color-scheme:light dark}main{text-align:center}</style></head>
        <body><main><h1>$message</h1><p>You can close this tab.</p></main></body></html>
        """.trimIndent()
}
