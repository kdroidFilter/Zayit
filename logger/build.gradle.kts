plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.android.kmp.library)
}

// The app's logs: the console in development, Sentry once the app initializes it (the desktop: main.kt), on every
// platform. On iOS the app links Sentry's Cocoa SDK, which Sentry KMP calls.
kotlin {
    jvm()
    android {
        namespace = "io.github.kdroidfilter.seforimapp.logger"
        compileSdk = 37
        minSdk = 21
    }
    iosArm64()
    iosSimulatorArm64()
    jvmToolchain(
        libs.versions.jvmToolchain
            .get()
            .toInt(),
    )

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.datetime)
            implementation(libs.sentry.kmp)
        }
    }
}
