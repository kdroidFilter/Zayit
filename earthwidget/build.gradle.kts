import org.jetbrains.compose.reload.gradle.ComposeHotRun
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())

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
            implementation(compose.material)
        }

        androidMain.dependencies {
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs) {
                exclude(group = "org.jetbrains.compose.material")
            }
            implementation("com.kosherjava:zmanim:2.5.0")

        }

        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

tasks.withType<ComposeHotRun>().configureEach {
    mainClass.set("MainKt")
}
