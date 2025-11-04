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
    alias(libs.plugins.metro)
    alias(libs.plugins.linux.deps)
}

val ref = System.getenv("GITHUB_REF") ?: ""
val version = if (ref.startsWith("refs/tags/")) {
    val tag = ref.removePrefix("refs/tags/")
    if (tag.startsWith("v")) tag.substring(1) else tag
} else "0.3.0"

// Turn 0.x[.y] into 1.x[.y] for macOS (DMG/PKG require MAJOR > 0)
fun macSafeVersion(ver: String): String {
    // Strip prerelease/build metadata for packaging (e.g., 0.1.0-beta -> 0.1.0)
    val core = ver.substringBefore('-').substringBefore('+')
    val parts = core.split('.')

    return if (parts.isNotEmpty() && parts[0] == "0") {
        when (parts.size) {
            1 -> "1.0"                 // "0"      -> "1.0"
            2 -> "1.${parts[1]}"       // "0.1"    -> "1.1"
            else -> "1.${parts[1]}.${parts[2]}" // "0.1.2" -> "1.1.2"
        }
    } else {
        core // already >= 1.x or something else; leave as-is
    }
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
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)

            // Ktor
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
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

            // Settings & platform utils
            implementation(libs.multiplatformSettings)
            implementation(libs.platformtools.core)
            implementation(libs.platformtools.darkmodedetector)
            implementation(libs.platformtools.appmanager)
            implementation(libs.platformtools.releasefetcher)

            //FileKit
            implementation(libs.filekit.core)
            implementation(libs.filekit.dialogs)
            implementation(libs.filekit.dialogs.compose)

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
            implementation(project(":network"))

            // Paging (AndroidX Paging 3)
            implementation(libs.androidx.paging.common)
            implementation(libs.androidx.paging.compose)

            //Oshi
            implementation(libs.oshi.core)


            implementation(libs.koalaplot.core)

            implementation(libs.confettikit)

            implementation(libs.compose.sonner)
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
            implementation("org.apache.lucene:lucene-core:10.3.1")
            implementation("org.apache.lucene:lucene-analysis-common:10.3.1")
            implementation("org.apache.lucene:lucene-queryparser:10.3.1")
            implementation("org.apache.lucene:lucene-highlighter:10.3.1")
            implementation("org.apache.commons:commons-compress:1.26.2")

            // HTML sanitization for search snippets
            implementation(libs.jsoup)
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
        mainClass = "io.github.kdroidfilter.seforimapp.MainKt"
        nativeDistributions {
            // Package-time resources root; include files under OS-specific subfolders (common, macos, windows, linux)
            appResourcesRootDir.set(layout.projectDirectory.dir("src/jvmMain/assets"))
            // Show splash image from the packaged resources directory
            jvmArgs += listOf(
                "-splash:\$APPDIR/resources/splash.png",
                "--enable-native-access=ALL-UNNAMED",
                "--add-modules=jdk.incubator.vector"
            )

            modules("java.sql", "jdk.unsupported", "jdk.security.auth", "jdk.accessibility", "jdk.incubator.vector")
            targetFormats(TargetFormat.Pkg, TargetFormat.Msi, TargetFormat.Deb)
            vendor = "KDroidFilter"

            linux {
                packageName = "zayit"
                iconFile.set(project.file("desktopAppIcons/LinuxIcon.png"))
                packageVersion = version
            }
            windows {
                iconFile.set(project.file("desktopAppIcons/WindowsIcon.ico"))
                packageVersion = version
                packageName = "Zayit"
                dirChooser = true
                menuGroup = "start-menu-group"
                shortcut = true
                upgradeUuid = "d9f21975-4359-4818-a623-6e9a3f0a07ca"
                perUserInstall = true
            }
            macOS {
                iconFile.set(project.file("desktopAppIcons/MacosIcon.icns"))
                bundleID = "io.github.kdroidfilter.seforimapp.desktopApp"
                packageVersion = macSafeVersion(version)
                packageName = "זית"
            }
            buildTypes.release.proguard {
                isEnabled = true
                obfuscate.set(false)
                optimize.set(true)
                configurationFiles.from(project.file("proguard-rules.pro"))
            }
        }
    }
}

linuxDebConfig {
    // set StartupWMClass to fix dock/taskbar icon
    startupWMClass.set("io.github.kdroidfilter.seforimapp.MainKt")

    //for Ubuntu 24 t64 dependencies compatibility with older OSes, see below Under Known jpackage issue: Ubuntu t64 transition
    enableT64AlternativeDeps.set(true)
}

tasks.withType<ComposeHotRun>().configureEach {
    mainClass.set("io.github.kdroidfilter.seforimapp.MainKt")
}

buildConfig {
    // https://github.com/gmazzo/gradle-buildconfig-plugin#usage-in-kts
}

tasks.withType<Jar> {
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
    exclude("META-INF/*.EC")
}
