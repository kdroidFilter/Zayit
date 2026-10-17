import org.jetbrains.compose.reload.gradle.ComposeHotRun

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    jvmToolchain(
        libs.versions.jvmToolchain
            .get()
            .toInt(),
    )

    jvm()
    // The reader's paging sources, shared with the mobile apps (appkit)
    android {
        namespace = "io.github.kdroidfilter.seforimapp.pagination"
        compileSdk = 37
        minSdk = 29
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.seforimlibrary.core)
            implementation(libs.seforimlibrary.dao)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            // AndroidX Paging 3 (common)
            implementation(libs.androidx.paging.common)
            implementation(project(":logger"))
        }

        jvmMain.dependencies {
        }
    }
}

tasks.withType<ComposeHotRun>().configureEach {
    mainClass.set("MainKt")
}
