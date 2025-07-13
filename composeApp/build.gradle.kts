import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.reload.ComposeHotRun
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.android.application)
    alias(libs.plugins.hotReload)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.buildConfig)
}

kotlin {
    androidTarget {
        //https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-test.html
        instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(compose.material3AdaptiveNavigationSuite)
            implementation(libs.navigation.compose)
            implementation(libs.kermit)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.serialization)
            implementation(libs.ktor.serialization.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.multiplatformSettings)
            implementation(libs.kotlinx.datetime)
            implementation(libs.materialKolor)
            implementation(libs.platformtools.core)
            implementation(libs.platformtools.darkmodedetector)
            implementation("io.github.kdroidfilter.seforimlibrary:core")
            implementation("io.github.kdroidfilter.seforimlibrary:dao")
            implementation("com.github.luben:zstd-jni:1.5.7-4")


        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            @OptIn(ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }

        androidMain.dependencies {
            implementation(compose.uiTooling)
            implementation(libs.androidx.activityCompose)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.kotlinx.coroutines.android)
            implementation("com.github.luben:zstd-jni:1.5.7-4@aar")

        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs) {
                exclude(group = "org.jetbrains.compose.material")
            }

            implementation(libs.ktor.client.okhttp)
            implementation(libs.platformtools.rtlwindows)
            implementation(libs.composenativetray)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.slf4j.simple)
            implementation("org.jetbrains.compose.components:components-splitpane-desktop:1.8.2")
            api(project(":jewel"))
            implementation("app.cash.sqldelight:sqlite-driver:2.1.0")
            implementation("app.cash.sqldelight:jdbc-driver:2.1.0")
        }

    }
}

android {
    namespace = "io.github.kdroidfilter.seforimapp"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
        targetSdk = 35

        applicationId = "io.github.kdroidfilter.seforimapp.androidApp"
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

//https://developer.android.com/develop/ui/compose/testing#setup
dependencies {
    androidTestImplementation(libs.androidx.uitest.junit4)
    debugImplementation(libs.androidx.uitest.testManifest)
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
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

//https://github.com/JetBrains/compose-hot-reload
composeCompiler {
    featureFlags.add(ComposeFeatureFlag.OptimizeNonSkippingGroups)
}
tasks.withType<ComposeHotRun>().configureEach {
    mainClass.set("MainKt")
}

buildConfig {
    // BuildConfig configuration here.
    // https://github.com/gmazzo/gradle-buildconfig-plugin#usage-in-kts
}
