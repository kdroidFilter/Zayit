import dev.nucleusframework.desktop.application.dsl.CompressionLevel
import dev.nucleusframework.desktop.application.dsl.GraalvmDistribution
import dev.nucleusframework.desktop.application.dsl.NativeImageOptimization
import dev.nucleusframework.desktop.application.dsl.ReleaseChannel
import dev.nucleusframework.desktop.application.dsl.ReleaseType
import dev.nucleusframework.desktop.application.dsl.TargetFormat
import io.github.kdroidfilter.buildsrc.Versioning
import org.jetbrains.compose.reload.gradle.ComposeHotRun

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
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
    alias(libs.plugins.ksp)
}

structuredCoroutines {
    useStrictProfile()
}

val version = Versioning.resolveVersion(project)

sentry {
    includeSourceContext = true
    org = System.getenv("SENTRY_ORG") ?: "kdroidfilter"
    projectName = System.getenv("SENTRY_PROJECT") ?: "zayit"
    authToken = System.getenv("SENTRY_AUTH_TOKEN")
}

// The smart siddur is in the official builds only (open core): with its package's token or its sources beside
// (settings.gradle.kts); a community build has none and addons/siddur/disabled says so
val withSiddur = gradle.extensions.extraProperties["siddurEnabled"] == true

// The semantic search too (open core): its package (code + model) with its token or its sources beside; without
// them, addons/semantic/disabled leaves the search lexical only
val withSemantic = gradle.extensions.extraProperties["semanticEnabled"] == true

kotlin {

    jvm()
    compilerOptions {
        // Satellites, dock and tab windows (Nucleus 2.6) are experimental.
        optIn.add("dev.nucleusframework.window.ExperimentalNucleusApi")
    }
    jvmToolchain(
        libs.versions.jvmToolchain
            .get()
            .toInt(),
    )

    sourceSets {
        commonMain.dependencies {
            // Compose
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.components.ui.tooling.preview)

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
            implementation(libs.structured.coroutines.annotations)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.serialization.protobuf)

            // Settings & platform utils
            implementation(libs.multiplatformSettings)
            implementation(libs.nucleus.core.runtime)
            implementation(libs.nucleus.application)
            implementation(libs.nucleus.aot.runtime)
            implementation(libs.nucleus.darkmode.detector)
            implementation(project(":releasefetcher"))

            // FileKit
            implementation(libs.filekit.core)
            implementation(libs.filekit.dialogs)
            implementation(libs.filekit.dialogs.compose)

            // Project / domain libs
            implementation(libs.seforimlibrary.core)
            implementation(libs.seforimlibrary.dao)

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

            // System Info
            implementation(libs.nucleus.system.info)

            implementation(libs.koalaplot.core)

            implementation(libs.confettikit)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.compose.ui.test)
        }

        jvmMain {
            kotlin.srcDir(if (withSiddur) "addons/siddur/enabled/kotlin" else "addons/siddur/disabled/kotlin")
            if (withSiddur) dependencies { implementation(libs.seforim.siddur) }
        }
        if (withSiddur) jvmTest { kotlin.srcDir("addons/siddur/test/kotlin") }

        jvmMain {
            kotlin.srcDir(if (withSemantic) "addons/semantic/enabled/kotlin" else "addons/semantic/disabled/kotlin")
            if (withSemantic) dependencies { implementation(libs.seforim.semantic) }
        }

        jvmTest.dependencies {
            implementation(libs.mockk)
            implementation(libs.kotlinx.coroutines.test)
        }

        jvmMain.dependencies {
            implementation(libs.hebrew.numerals)
            api(project(":jewel"))
            implementation(project(":earthwidget"))
            implementation(project(":luach"))
            implementation(libs.nucleus.system.color)
            implementation(libs.nucleus.decorated.window.core)
            implementation(libs.nucleus.decorated.window.tao)
            implementation(libs.nucleus.decorated.window.jewel)
            implementation(libs.nucleus.graalvm.runtime)
            implementation(libs.nucleus.updater.runtime)
            implementation(libs.nucleus.native.http)
            implementation(libs.nucleus.energy.manager)
            implementation(libs.nucleus.launcher.macos)
            implementation(libs.nucleus.launcher.windows)
            implementation(libs.nucleus.launcher.linux)
            implementation(libs.nucleus.menu.macos)
            implementation(libs.nucleus.sf.symbols)
            implementation(libs.nucleus.share)
            implementation(libs.nucleus.taskbar.progress.tao)
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
            implementation(libs.kotlinx.collections.immutable)

            // SeforimLibrary search module
            implementation(libs.seforimlibrary.search)

            // SeforimLibrary CLI: lets the desktop binary run headless search commands
            // (`zayit cli search ...`) by delegating to its runCli() entry point.
            implementation(libs.seforimlibrary.cli)

            // Delta-update client (download + apply patch.db onto local seforim.db)
            implementation(libs.seforimlibrary.delta.updater)

            implementation(libs.commons.compress)

            // HTML sanitization for search snippets
            implementation(libs.jsoup)

            implementation(libs.kosherkotlin)

            implementation(libs.nucleus.notification.common)

            compileOnly(libs.graal.hotspot.library)
            // Sentry crash reporting
            implementation(libs.sentry.core)
        }
    }
}

nucleus.application {

    mainClass = "io.github.kdroidfilter.seforimapp.MainKt"
    nucleusOptimization = true
    graalvm {
        isEnabled = true
        imageName = "zayit"
        optimization = NativeImageOptimization.LEVEL_3
        nativeImageConfigBaseDir.set(layout.projectDirectory.dir("src/graalvm"))
        buildArgs.add("--initialize-at-build-time=org.slf4j")
        toolchain {
            distribution = GraalvmDistribution.ORACLE
        }
    }
    nativeDistributions {
        appName = "זית"
        packageName = "zayit"
        description = "ספריית הלימוד שמובילה ישר לטקסט"
        compressionLevel = CompressionLevel.Ultra

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
        enableAotCache = true
        homepage = "https://zayitapp.com"
        licenseFile.set(File(project.rootDir, "LICENSE"))
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
            TargetFormat.Deb,
            TargetFormat.Rpm,
            TargetFormat.Dmg,
            TargetFormat.Zip,
            TargetFormat.Nsis,
            TargetFormat.Pacman,
        )
        vendor = "KDroidFilter"
        cleanupNativeLibs = true

        // Register the custom URL scheme so shareable deep links (zayit://book/...,
        // zayit://search/...) are routed to the app by the OS on macOS, Windows and Linux.
        protocol("זית", "zayit")

        linux {
            iconFile.set(project.file("desktopAppIcons/LinuxIcon.png"))
            packageVersion = version
            debMaintainer = "elyahou.hadass@gmail.com"
            menuGroup = "Education"
        }
        windows {
            iconFile.set(project.file("desktopAppIcons/WindowsIcon.ico"))
            packageVersion = version
            dirChooser = false
            shortcut = true
            upgradeUuid = "d9f21975-4359-4818-a623-6e9a3f0a07ca"
            msi { perMachine = false }

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
                installerLanguages = listOf("he_IL")
            }
        }
        macOS {
            iconFile.set(project.file("desktopAppIcons/MacosIcon.icns"))
            bundleID = "io.github.kdroidfilter.seforimapp.desktopApp"
            packageVersion = version
            packageName = "זית"
        }
        buildTypes.release.proguard {
            isEnabled = true
            obfuscate.set(false)
            optimize.set(true)
            consumerRules.set(true)
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
    packageName("io.github.kdroidfilter.seforimapp")
    // Official builds get the DSN from the SENTRY_DSN CI secret; without it crash reporting is disabled
    val sentryDsn = providers.environmentVariable("SENTRY_DSN").orElse(providers.gradleProperty("sentry.dsn")).orElse("")
    buildConfigField("SENTRY_DSN", sentryDsn)
}

// Jewel's icons-api pulls IntelliJ's coroutines fork, which duplicates kotlinx-coroutines-core-jvm classes
configurations.configureEach {
    exclude(group = "org.jetbrains.intellij.deps.kotlinx", module = "kotlinx-coroutines-core-jvm")
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

dependencies {
    // Generates availableHomeWidgets from every HomeWidget object (see :widgetprocessor).
    add("kspJvm", project(":widgetprocessor"))
}
