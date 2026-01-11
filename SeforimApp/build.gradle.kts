import io.github.kdroidfilter.buildsrc.NativeCleanupHelper
import io.github.kdroidfilter.buildsrc.RenameMacPkgTask
import io.github.kdroidfilter.buildsrc.RenameMsiTask
import io.github.kdroidfilter.buildsrc.Versioning
import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.reload.gradle.ComposeHotRun

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
    alias(libs.plugins.stability.analyzer)
}

val version = Versioning.resolveVersion(project)

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
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())

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
            implementation(libs.navigation.compose)

            // MetroX (ViewModel integration)
            implementation(libs.metrox.viewmodel.compose)

            // KotlinX
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.serialization.protobuf)

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
            implementation(project(":earthwidget"))
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
            implementation(libs.lucene.core)
            implementation(libs.reorderable)

            implementation(libs.commons.compress)

            // HTML sanitization for search snippets
            implementation(libs.jsoup)

            implementation(libs.zmanim)

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
            jvmArgs += listOf(
                "-XX:+UseCompactObjectHeaders",
                "-XX:+UseStringDeduplication",
                "-XX:MaxGCPauseMillis=50",
                )



            modules("java.sql", "jdk.unsupported", "jdk.security.auth", "jdk.accessibility", "jdk.incubator.vector")
            targetFormats(TargetFormat.Pkg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Dmg)
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
                dirChooser = false
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
                version.set("7.8.1")
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



// --- macOS: rename generated .pkg to include architecture suffix (_arm64 or _x64)
val isMacHost: Boolean = System.getProperty("os.name").lowercase().contains("mac")
val macArchSuffix: String = run {
    val arch = System.getProperty("os.arch").lowercase()
    if (arch.contains("aarch64") || arch.contains("arm")) "_arm64" else "_x64"
}

// Finds all .pkg files under build/compose/binaries and appends arch suffix if missing
val renameMacPkg = tasks.register<RenameMacPkgTask>("renameMacPkg") {
    enabled = isMacHost
    group = "distribution"
    description = "Rename generated macOS .pkg files to include architecture suffix (e.g., _arm64 or _x64)."
    archSuffix.set(macArchSuffix)
}

// Ensure the rename runs after any Compose Desktop task that produces a PKG or DMG
// This covers tasks like `packageReleasePkg`, `packageDebugPkg`, `packageReleaseDmg`, etc.
// Exclude the renamer itself to avoid circular finalizer
tasks.matching { it.name.endsWith("Pkg") && it.name != "renameMacPkg" }.configureEach {
    finalizedBy(renameMacPkg)
}
tasks.matching { it.name.endsWith("Dmg") && it.name != "renameMacPkg" }.configureEach {
    finalizedBy(renameMacPkg)
}

// --- Windows: rename generated .msi to include architecture suffix (_arm64 or _x64)
val isWindowsHost: Boolean = System.getProperty("os.name").lowercase().contains("windows")
val windowsArchSuffix: String = run {
    val arch = System.getProperty("os.arch").lowercase()
    if (arch.contains("aarch64") || arch.contains("arm")) "_arm64" else "_x64"
}

// Finds all .msi files under build/compose/binaries and appends arch suffix if missing
val renameMsi = tasks.register<RenameMsiTask>("renameMsi") {
    enabled = isWindowsHost
    group = "distribution"
    description = "Rename generated Windows .msi files to include architecture suffix (e.g., _arm64 or _x64)."
    archSuffix.set(windowsArchSuffix)
}

// Ensure the rename runs after any Compose Desktop task that produces an MSI
// This covers tasks like `packageReleaseMsi`, `packageDebugMsi`, etc.
// Exclude the renamer itself to avoid circular finalizer
tasks.matching { it.name.endsWith("Msi") && it.name != "renameMsi" }.configureEach {
    finalizedBy(renameMsi)
}

// --- Clean unused native binaries from JARs for smaller distribution size
NativeCleanupHelper.registerCleanupTasks(project)
