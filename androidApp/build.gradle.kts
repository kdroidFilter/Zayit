plugins {
    // AGP 9 ships built-in Kotlin support, so no separate kotlin.android plugin is needed.
    alias(libs.plugins.android.application)
}

// Thin Android launcher. AGP 9 forbids com.android.application in the same module as the
// kotlin.multiplatform plugin, so the launchable APK lives here and depends on the shared
// :SeforimApp module (which exposes AppActivity + the common App() composable).
android {
    namespace = "io.github.kdroidfilter.seforimapp.androidApp"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.kdroidfilter.seforimapp"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
}

dependencies {
    implementation(project(":SeforimApp"))
}
