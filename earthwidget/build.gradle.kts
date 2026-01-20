import io.github.kdroidfilter.buildsrc.Versioning

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    id("com.android.kotlin.multiplatform.library")
}

val version = Versioning.resolveVersion(project)

kotlin {
    jvmToolchain(
        libs.versions.jvmToolchain
            .get()
            .toInt(),
    )

    androidLibrary {
        namespace = "io.github.kdroidfilter.seforimapp"
        compileSdk = 35
        minSdk = 21
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.components.resources)
        }

        androidMain.dependencies {
        }

        jvmMain.dependencies {
            api(project(":jewel"))
            api(project(":hebrewcalendar"))
            implementation(compose.desktop.currentOs) {
                exclude(group = "org.jetbrains.compose.material")
            }
            implementation(libs.zmanim)
        }

        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
