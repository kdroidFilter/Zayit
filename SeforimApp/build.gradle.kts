import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.reload.gradle.ComposeHotRun
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
//    alias(libs.plugins.android.application)
    alias(libs.plugins.hotReload)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.buildConfig)
    // Metro DI codegen plugin
    alias(libs.plugins.metro)
}

kotlin {
//    androidTarget {
//        // https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-test.html
//        instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
//    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            // Compose
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)

            // Ktor
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.serialization)
            implementation(libs.ktor.serialization.json)

            // AndroidX (multiplatform-friendly artifacts)
            implementation(libs.androidx.lifecycle.runtime)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.navigation.compose)

            // KotlinX
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)

            // DI - Metro
            implementation(libs.metro.runtime)

            // Settings & platform utils
            implementation(libs.multiplatformSettings)
            implementation(libs.platformtools.core)
            implementation(libs.platformtools.darkmodedetector)

            // UI & theme utils
            implementation(libs.materialKolor)

            // Project / domain libs
            implementation("io.github.kdroidfilter.seforimlibrary:core")
            implementation("io.github.kdroidfilter.seforimlibrary:dao")

            // Local projects
            implementation(project(":htmlparser"))
            implementation(project(":icons"))
            implementation(project(":logger"))
            implementation(project(":navigation"))
            implementation(project(":pagination"))
            implementation(project(":texteffects"))

            // Paging (AndroidX Paging 3)
            implementation(libs.androidx.paging.common)
            implementation(libs.androidx.paging.compose)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            @OptIn(ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
//
//        androidMain.dependencies {
//            implementation(compose.uiTooling)
//            implementation(libs.androidx.activityCompose)
//            implementation(libs.kotlinx.coroutines.android)
//            implementation(libs.ktor.client.okhttp)
//        }

        jvmMain.dependencies {
            api(project(":jewel"))
            implementation(compose.desktop.currentOs) {
                exclude(group = "org.jetbrains.compose.material")
            }

            implementation(libs.composenativetray)
            implementation(libs.jdbc.driver)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.platformtools.rtlwindows)
            implementation(libs.slf4j.simple)
            implementation(libs.split.pane.desktop)
            implementation(libs.sqlite.driver)
            implementation(libs.zstd.jni)
            implementation(libs.ktor.client.okhttp)
        }
    }
}

//android {
//    namespace = "io.github.kdroidfilter.seforimapp"
//    compileSdk = 35
//
//    defaultConfig {
//        applicationId = "io.github.kdroidfilter.seforimapp.androidApp"
//        minSdk = 21
//        targetSdk = 35
//        versionCode = 1
//        versionName = "1.0.0"
//
//        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
//    }
//}
//
//// https://developer.android.com/develop/ui/compose/testing#setup
//dependencies {
//    androidTestImplementation(libs.androidx.uitest.junit4)
//    debugImplementation(libs.androidx.uitest.testManifest)
//}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            modules("java.sql", "jdk.unsupported")
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "SeforimApp"
            packageVersion = "1.0.0"

            linux {
                iconFile.set(project.file("desktopAppIcons/LinuxIcon.png"))
            }
            windows {
                iconFile.set(project.file("desktopAppIcons/WindowsIcon.ico"))
            }
            macOS {
                iconFile.set(project.file("desktopAppIcons/MacosIcon.icns"))
                bundleID = "io.github.kdroidfilter.seforimapp.desktopApp"
            }
        }
    }
}



tasks.withType<ComposeHotRun>().configureEach {
    mainClass.set("MainKt")
}

buildConfig {
    // https://github.com/gmazzo/gradle-buildconfig-plugin#usage-in-kts
}
