import io.github.kdroidfilter.buildsrc.Versioning
import io.github.kdroidfilter.nucleus.desktop.application.dsl.ReleaseChannel
import io.github.kdroidfilter.nucleus.desktop.application.dsl.ReleaseType
import io.github.kdroidfilter.nucleus.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.ExperimentalComposeLibrary
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
    alias(libs.plugins.stability.analyzer)
    alias(libs.plugins.sqlDelight)
    alias(libs.plugins.kover)
    alias(libs.plugins.nucleus)
    alias(libs.plugins.structured.coroutines)
    alias(libs.plugins.sentryJvmGradle)
}

structuredCoroutines {
    useStrictProfile()
}

val version = Versioning.resolveVersion(project)

// macOS jpackage requires the major version to be >= 1; bump 0.x.y to 1.x.y
fun macSafeVersion(ver: String): String {
    val core = ver.substringBefore('-').substringBefore('+')
    val parts = core.split('.')
    return if (parts.isNotEmpty() && parts[0] == "0") {
        when (parts.size) {
            1 -> "1.0"
            2 -> "1.${parts[1]}"
            else -> "1.${parts[1]}.${parts[2]}"
        }
    } else {
        core
    }
}

sentry {
    includeSourceContext = true
    org = System.getenv("SENTRY_ORG") ?: "kdroidfilter"
    projectName = System.getenv("SENTRY_PROJECT") ?: "zayit"
    authToken = System.getenv("SENTRY_AUTH_TOKEN")
}

kotlin {
//    androidTarget {
//        // https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-test.html
//        instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
//    }

    jvm()
    jvmToolchain(
        libs.versions.jvmToolchain
            .get()
            .toInt(),
    )

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
            implementation("io.github.santimattius:structured-coroutines-annotations:0.3.0")
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.serialization.protobuf)

            // Settings & platform utils
            implementation(libs.multiplatformSettings)
            implementation(libs.platformtools.core)
            implementation(libs.nucleus.core.runtime)
            implementation(libs.nucleus.aot.runtime)
            implementation(libs.nucleus.darkmode.detector)
            implementation(libs.platformtools.appmanager)
            implementation(libs.platformtools.releasefetcher)

            // FileKit
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

            // Oshi
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

        jvmTest.dependencies {
            implementation(libs.mockk)
            implementation(libs.kotlinx.coroutines.test)
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
            implementation(libs.nucleus.decorated.window)
            implementation(libs.nucleus.updater.runtime)
            implementation(compose.desktop.currentOs) {
                exclude(group = "org.jetbrains.compose.material")
            }

            implementation(libs.jdbc.driver)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.slf4j.simple)
            implementation(libs.split.pane.desktop)
            implementation(libs.sqlite.driver)
            implementation(libs.zstd.jni)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.lucene.core)
            implementation(libs.reorderable)

            // SeforimLibrary search module
            implementation("io.github.kdroidfilter.seforimlibrary:search")

            implementation(libs.commons.compress)

            // HTML sanitization for search snippets
            implementation(libs.jsoup)

            implementation(libs.zmanim)

            implementation(libs.knotify)
            implementation(libs.knotify.compose)

            // Sentry crash reporting
            implementation(libs.sentry.core)
        }
    }
}

// android {
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
// }
//
// // https://developer.android.com/develop/ui/compose/testing#setup
// dependencies {
//    androidTestImplementation(libs.androidx.uitest.junit4)
//    debugImplementation(libs.androidx.uitest.testManifest)
// }

nucleus.application {

    mainClass = "io.github.kdroidfilter.seforimapp.MainKt"


    graalvm {
        isEnabled = true
        javaLanguageVersion = 25
        jvmVendor = JvmVendorSpec.BELLSOFT
        imageName = "zayit"
        march = providers.gradleProperty("nativeMarch").getOrElse("native")
        buildArgs.addAll(
            "-H:+AddAllCharsets",
            "-Djava.awt.headless=false",
            "-Os",
            "-H:-IncludeMethodData",
            // Enable shared arenas for Lucene's memory-mapped I/O (MemorySegmentIndexInput)
            "-H:+SharedArenaSupport",
            // Parallel GC for better throughput with large heaps
            "--gc=parallel",
            // Default max heap 2 GB
            "-R:MaxHeapSize=2147483648",
            // Exclude sqlite-jdbc's native-image.properties which references
            // SqliteJdbcFeature (lives in META-INF/versions/9/ of the multi-release
            // JAR, stripped by ProGuard shrinking). Equivalent metadata is already
            // in the project's reachability-metadata.json.
            "--exclude-config", ".*\\.jar", "META-INF/native-image/org\\.xerial/.*",
            // Lucene classes initialize at runtime (GraalVM default).
            // MethodHandle-based code paths are handled by substitution classes
            // in io.github.kdroidfilter.seforimapp.graalvm.
            )
        nativeImageConfigBaseDir.set(
            layout.projectDirectory.dir(
                when {
                    org.gradle.internal.os.OperatingSystem.current().isMacOsX -> "src/main/resources-macos/META-INF/native-image"
                    org.gradle.internal.os.OperatingSystem.current().isWindows -> "src/main/resources-windows/META-INF/native-image"
                    org.gradle.internal.os.OperatingSystem.current().isLinux -> "src/main/resources-linux/META-INF/native-image"
                    else -> throw GradleException("Unsupported OS")
                }
            )
        )
        macOS {
            cStubsSrc.set(layout.projectDirectory.file("src/main/c/cursor_stub.c"))
        }
    }
    nativeDistributions {

        publish {
            github {
                enabled = true
                owner = "kdroidFilter"
                repo = "Zayit"
                channel = ReleaseChannel.Latest
                releaseType = ReleaseType.Release
            }
        }

        // Package-time resources root; include files under OS-specific subfolders (common, macos, windows, linux)
        appResourcesRootDir.set(layout.projectDirectory.dir("src/jvmMain/assets"))
        splashImage = "splash.png"
        enableAotCache = true
        homepage = "https://zayitapp.com"
        licenseFile.set(File(project.rootDir, "LICENSE"))
        jvmArgs +=
            listOf(
                "--enable-native-access=ALL-UNNAMED",
                "--add-modules=jdk.incubator.vector",
            )
        jvmArgs +=
            listOf(
                "-XX:+UseCompactObjectHeaders",
                "-XX:+UseStringDeduplication",
                "-XX:MaxGCPauseMillis=50",
            )

        modules(
            "java.sql",
            "java.management",
            "jdk.management",
            "jdk.unsupported",
            "jdk.security.auth",
            "jdk.accessibility",
            "jdk.incubator.vector",
        )
        targetFormats(
            TargetFormat.Msi,
            TargetFormat.Deb,
            TargetFormat.Rpm,
            TargetFormat.Dmg,
            TargetFormat.Pkg,
            TargetFormat.Zip,
        )
        vendor = "KDroidFilter"
        cleanupNativeLibs = true

        linux {
            packageName = "zayit"
            iconFile.set(project.file("desktopAppIcons/LinuxIcon.png"))
            packageVersion = version
            debMaintainer = "elyahou.hadass@gmail.com"
        }
        windows {
            iconFile.set(project.file("desktopAppIcons/WindowsIcon.ico"))
            packageVersion = version
            packageName = "Zayit"
            dirChooser = false
            shortcut = true
            upgradeUuid = "d9f21975-4359-4818-a623-6e9a3f0a07ca"
            perUserInstall = true

            nsis {
                oneClick = true // Default: true
                allowElevation = false // Default: false
                perMachine = false // Default: false (current user)
                allowToChangeInstallationDirectory = false // Default: false
                createDesktopShortcut = true
                createStartMenuShortcut = true
                runAfterFinish = true
                deleteAppDataOnUninstall = false // Default: false
                multiLanguageInstaller = true // Default: false
                // Languages: "en_US", "fr_FR", "de_DE", "es_ES", "ja_JP", "zh_CN", etc.
                installerLanguages = listOf("he_IL",)
            }
        }
        macOS {
            iconFile.set(project.file("desktopAppIcons/MacosIcon.icns"))
            bundleID = "io.github.kdroidfilter.seforimapp.desktopApp"
            packageVersion = macSafeVersion(version)
            packageName = "זית"
            appStore = true
        }
        buildTypes.release.proguard {
            version.set("7.8.1")
            isEnabled = true
            obfuscate.set(false)
            optimize.set(false)
            configurationFiles.from(project.file("proguard-rules.pro"))
        }
    }
}

sqldelight {
    databases {
        create("UserSettingsDb") {
            packageName.set("io.github.kdroidfilter.seforimapp.db")
            dialect("app.cash.sqldelight:sqlite-3-24-dialect:${libs.versions.sqlDelight.get()}")
        }
    }
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

// --- Kover code coverage configuration
kover {
    reports {
        filters {
            excludes {
                // Exclude generated code
                packages("*.generated.*", "*.sqldelight.*", "io.github.kdroidfilter.seforimapp.db")
                classes("*_Factory", "*_MembersInjector", "*Hilt*", "*_Impl", "*\$\$serializer")
                // Exclude Compose previews
                annotatedBy("androidx.compose.ui.tooling.preview.Preview")
            }
        }
    }
}
