import io.github.kdroidfilter.buildsrc.Versioning

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
}

val version = Versioning.resolveVersion(project)

kotlin {
    jvmToolchain(
        libs.versions.jvmToolchain
            .get()
            .toInt(),
    )

    jvm()
    // Filament KMP renders on Android and iOS too: the 3D widgets of the mobile apps
    android {
        namespace = "io.github.kdroidfilter.seforimapp.earthwidget"
        compileSdk = 37
        minSdk = 29
        androidResources { enable = true }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":logger"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.components.resources)
            implementation(libs.filament.compose)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kosherkotlin)
        }

        jvmMain.dependencies {
            api(project(":jewel"))
            implementation(project(":luach"))
            implementation(compose.desktop.currentOs) {
                exclude(group = "org.jetbrains.compose.material")
            }
        }

        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
