import io.github.kdroidfilter.buildsrc.NativeCleanupTransformHelper
import io.github.kdroidfilter.buildsrc.RenameMacPkgTask
import io.github.kdroidfilter.buildsrc.RenameMsiTask
import io.github.kdroidfilter.buildsrc.Versioning
import org.gradle.api.GradleException
import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.reload.gradle.ComposeHotRun
import java.io.File as JFile

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
    alias(libs.plugins.sqlDelight)
    alias(libs.plugins.kover)
}

val version = Versioning.resolveVersion(project)

// Turn 0.x[.y] into 1.x[.y] for macOS (DMG/PKG require MAJOR > 0)
fun macSafeVersion(ver: String): String {
    // Strip prerelease/build metadata for packaging (e.g., 0.1.0-beta -> 0.1.0)
    val core = ver.substringBefore('-').substringBefore('+')
    val parts = core.split('.')

    return if (parts.isNotEmpty() && parts[0] == "0") {
        when (parts.size) {
            1 -> "1.0" // "0"      -> "1.0"
            2 -> "1.${parts[1]}" // "0.1"    -> "1.1"
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
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.serialization.protobuf)

            // Settings & platform utils
            implementation(libs.multiplatformSettings)
            implementation(libs.platformtools.core)
            implementation(libs.platformtools.darkmodedetector)
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

            // SeforimLibrary search module
            implementation("io.github.kdroidfilter.seforimlibrary:search")

            implementation(libs.commons.compress)

            // HTML sanitization for search snippets
            implementation(libs.jsoup)

            implementation(libs.zmanim)

            implementation(libs.knotify)
            implementation(libs.knotify.compose)
        }
    }
}

// Exclude Skiko runtimes for non-current platforms to reduce package size
configurations.all {
    val os = System.getProperty("os.name").lowercase()
    when {
        os.contains("mac") -> {
            exclude(group = "org.jetbrains.skiko", module = "skiko-awt-runtime-windows-x64")
            exclude(group = "org.jetbrains.skiko", module = "skiko-awt-runtime-windows-arm64")
            exclude(group = "org.jetbrains.skiko", module = "skiko-awt-runtime-linux-x64")
            exclude(group = "org.jetbrains.skiko", module = "skiko-awt-runtime-linux-arm64")
        }
        os.contains("windows") -> {
            exclude(group = "org.jetbrains.skiko", module = "skiko-awt-runtime-macos-arm64")
            exclude(group = "org.jetbrains.skiko", module = "skiko-awt-runtime-macos-x64")
            exclude(group = "org.jetbrains.skiko", module = "skiko-awt-runtime-linux-x64")
            exclude(group = "org.jetbrains.skiko", module = "skiko-awt-runtime-linux-arm64")
        }
        else -> { // Linux
            exclude(group = "org.jetbrains.skiko", module = "skiko-awt-runtime-macos-arm64")
            exclude(group = "org.jetbrains.skiko", module = "skiko-awt-runtime-macos-x64")
            exclude(group = "org.jetbrains.skiko", module = "skiko-awt-runtime-windows-x64")
            exclude(group = "org.jetbrains.skiko", module = "skiko-awt-runtime-windows-arm64")
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

compose.desktop {
    application {
        mainClass = "io.github.kdroidfilter.seforimapp.MainKt"
        nativeDistributions {
            // Package-time resources root; include files under OS-specific subfolders (common, macos, windows, linux)
            appResourcesRootDir.set(layout.projectDirectory.dir("src/jvmMain/assets"))
            // Show splash image from the packaged resources directory
            // Note: -XX:AOTCache=$APPDIR/zayit.aot is injected by generateDistributableAotCache
            // into the .cfg file (not here, because nativeDistributions.jvmArgs leak into the run task)
            jvmArgs +=
                listOf(
                    "-splash:\$APPDIR/resources/splash.png",
                    "--enable-native-access=ALL-UNNAMED",
                    "--add-modules=jdk.incubator.vector",
                )
            jvmArgs +=
                listOf(
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

sqldelight {
    databases {
        create("UserSettingsDb") {
            packageName.set("io.github.kdroidfilter.seforimapp.db")
            dialect("app.cash.sqldelight:sqlite-3-24-dialect:${libs.versions.sqlDelight.get()}")
        }
    }
}

linuxDebConfig {
    // set StartupWMClass to fix dock/taskbar icon
    startupWMClass.set("io.github.kdroidfilter.seforimapp.MainKt")

    // for Ubuntu 24 t64 dependencies compatibility with older OSes, see below Under Known jpackage issue: Ubuntu t64 transition
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
val macArchSuffix: String =
    run {
        val arch = System.getProperty("os.arch").lowercase()
        if (arch.contains("aarch64") || arch.contains("arm")) "_arm64" else "_x64"
    }

// Finds all .pkg files under build/compose/binaries and appends arch suffix if missing
val renameMacPkg =
    tasks.register<RenameMacPkgTask>("renameMacPkg") {
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
val windowsArchSuffix: String =
    run {
        val arch = System.getProperty("os.arch").lowercase()
        if (arch.contains("aarch64") || arch.contains("arm")) "_arm64" else "_x64"
    }

// Finds all .msi files under build/compose/binaries and appends arch suffix if missing
val renameMsi =
    tasks.register<RenameMsiTask>("renameMsi") {
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
NativeCleanupTransformHelper.registerTransform(project)

// --- Project Leyden AOT cache for dev mode (./gradlew :SeforimApp:run)
// Configured directly on the run task to avoid leaking into nativeDistributions .cfg
// Usage: -Paot=train|on|auto|off (default: auto)
afterEvaluate {
    tasks.named<JavaExec>("run") {
        val aotMode = project.findProperty("aot")?.toString() ?: "auto"
        val aotCacheDir = layout.buildDirectory.dir("aot-cache").get().asFile
        val aotCacheFile = JFile(aotCacheDir, "zayit-dev.aot")

        when (aotMode) {
            "train" -> {
                aotCacheDir.mkdirs()
                jvmArgs = (jvmArgs ?: emptyList()) + "-XX:AOTCacheOutput=${aotCacheFile.absolutePath}"
            }
            "on" -> {
                if (aotCacheFile.exists()) {
                    jvmArgs = (jvmArgs ?: emptyList()) + "-XX:AOTCache=${aotCacheFile.absolutePath}"
                }
            }
            "auto" -> {
                if (aotCacheFile.exists()) {
                    jvmArgs = (jvmArgs ?: emptyList()) + "-XX:AOTCache=${aotCacheFile.absolutePath}"
                } else {
                    aotCacheDir.mkdirs()
                    jvmArgs = (jvmArgs ?: emptyList()) + "-XX:AOTCacheOutput=${aotCacheFile.absolutePath}"
                }
            }
            "off" -> { /* AOT disabled */ }
        }
    }
}

// --- Project Leyden: AOT cache generation for distributable ---
// Shared logic: trains the app ~20s to record class loading, then creates an AOT cache
fun Project.registerAotCacheTask(taskName: String, dependsOnTask: String, binariesSubdir: String) {
    tasks.register(taskName) {
        group = "compose desktop"
        description = "Generate an AOT cache for the $binariesSubdir distributable (trains by launching the app ~20s)"

        dependsOn(tasks.named(dependsOnTask))

        // Resolve at configuration time (configuration cache compatible)
        val capturedBuildDir: JFile = project.layout.buildDirectory.asFile.get()
        val javaToolchain = project.extensions.getByType<JavaPluginExtension>().toolchain
        val javaLauncherProvider = project.extensions.getByType<JavaToolchainService>()
            .launcherFor(javaToolchain)
        val javaExePath = javaLauncherProvider.map { it.executablePath.asFile.absolutePath }

        doLast {
            val toolchainJavaExe = javaExePath.get()

            // Locate the distributable app directory
            val baseDir = JFile(capturedBuildDir, "compose/binaries/$binariesSubdir/app")
            val appDir = baseDir.listFiles()?.firstOrNull { it.isDirectory }
                ?: throw GradleException("Distributable app directory not found under $baseDir. Run $dependsOnTask first.")

            // Find the app/ subdirectory that contains JARs (this is $APPDIR at runtime)
            // macOS:   AppName.app/Contents/app/
            // Windows: AppName/app/
            // Linux:   AppName/lib/app/
            val appJarDir = listOf(
                JFile(appDir, "Contents/app"),  // macOS
                JFile(appDir, "app"),            // Windows
                JFile(appDir, "lib/app"),         // Linux
            ).firstOrNull { it.exists() }
                ?: throw GradleException("app/ subdirectory not found in $appDir")

            // Locate the distributable's bundled runtime and provision a java launcher there.
            // jpackage creates a jlink'd custom runtime whose lib/modules differs from the full JDK.
            // The AOT cache records a checksum of lib/modules, so training MUST use a java binary
            // that resolves to the same runtime (and thus the same modules file).
            // The jlink'd runtime has lib/ but no bin/java, so we copy the toolchain java there
            // temporarily and use it for both the record and create phases.
            val runtimeHome: JFile = listOf(
                JFile(appDir, "Contents/runtime/Contents/Home"), // macOS
                JFile(appDir, "runtime"),                         // Windows
                JFile(appDir, "lib/runtime"),                     // Linux
            ).firstOrNull { it.exists() }
                ?: throw GradleException("Bundled runtime not found in $appDir")
            val runtimeBinDir = JFile(runtimeHome, "bin")
            val provisionedJava: JFile
            val cleanUpProvisionedJava: Boolean
            if (JFile(runtimeBinDir, "java").exists() || JFile(runtimeBinDir, "java.exe").exists()) {
                // Runtime already has a java launcher (unlikely but handle it)
                val exeName = if (System.getProperty("os.name").lowercase().contains("windows")) "java.exe" else "java"
                provisionedJava = JFile(runtimeBinDir, exeName)
                cleanUpProvisionedJava = false
            } else if (runtimeHome.exists()) {
                runtimeBinDir.mkdirs()
                val isWindows = System.getProperty("os.name").lowercase().contains("windows")
                val exeName = if (isWindows) "java.exe" else "java"
                provisionedJava = JFile(runtimeBinDir, exeName)
                val toolchainBinDir = JFile(toolchainJavaExe).parentFile
                // Copy the java launcher
                JFile(toolchainJavaExe).copyTo(provisionedJava, overwrite = true)
                provisionedJava.setExecutable(true)
                // On Windows, java.exe needs only a few essential DLLs to run
                // We copy only the minimal set required, not the entire JDK bin/ directory
                if (isWindows) {
                    val essentialDlls = setOf(
                        "jli.dll",           // Java Launcher Infrastructure - always required
                        "vcruntime140.dll",  // Visual C++ Runtime
                        "msvcp140.dll",      // Visual C++ Runtime
                        "ucrtbase.dll"       // Universal C Runtime (may not exist in all JDKs)
                    )
                    toolchainBinDir.listFiles()
                        ?.filter { it.extension.lowercase() == "dll" && it.name.lowercase() in essentialDlls }
                        ?.forEach { dll ->
                            val target = JFile(runtimeBinDir, dll.name)
                            if (!target.exists()) {
                                dll.copyTo(target, overwrite = false)
                            }
                        }
                }
                cleanUpProvisionedJava = true
                logger.lifecycle("AOT training: provisioned java launcher at ${provisionedJava.absolutePath}")
            } else {
                // Fallback: no bundled runtime found (e.g. non-jpackage build); use toolchain java
                logger.warn("AOT training: bundled runtime not found at $runtimeHome, falling back to toolchain java")
                provisionedJava = JFile(toolchainJavaExe)
                cleanUpProvisionedJava = false
            }
            val javaExe = provisionedJava.absolutePath

            // Parse the .cfg file to reconstruct classpath and java-options
            val cfgFile = appJarDir.listFiles()?.firstOrNull { it.extension == "cfg" }
                ?: throw GradleException("No .cfg file found in $appJarDir")
            val cfgLines = cfgFile.readLines()

            // Extract classpath entries and java options
            val cpEntries = mutableListOf<String>()
            var inClasspath = false
            val javaOptions = mutableListOf<String>()
            var inJavaOptions = false
            var mainClass = ""

            for (line in cfgLines) {
                val trimmed = line.trim()
                when {
                    trimmed == "[JavaOptions]" -> { inJavaOptions = true; inClasspath = false }
                    trimmed == "[ClassPath]" -> { inClasspath = true; inJavaOptions = false }
                    trimmed == "[Application]" || trimmed == "[ArgOptions]" -> { inClasspath = false; inJavaOptions = false }
                    trimmed.startsWith("app.mainclass=") -> mainClass = trimmed.substringAfter("app.mainclass=").trim()
                    trimmed.startsWith("app.classpath=") -> {
                        cpEntries += trimmed.substringAfter("app.classpath=").trim()
                    }
                    trimmed.startsWith("[") -> { inClasspath = false; inJavaOptions = false }
                    inClasspath && trimmed.isNotEmpty() -> cpEntries += trimmed
                    inJavaOptions && trimmed.isNotEmpty() -> {
                        val opt = if (trimmed.startsWith("java-options=")) trimmed.substringAfter("java-options=") else trimmed
                        // Skip AOTCache/AOTCacheOutput args (we'll provide our own)
                        if (!opt.contains("AOTCache")) {
                            // Resolve $APPDIR to the actual app jar directory
                            javaOptions += opt.replace("\$APPDIR", appJarDir.absolutePath)
                        }
                    }
                }
            }

            // Resolve classpath: entries are relative to $APPDIR
            val classpath = cpEntries.joinToString(JFile.pathSeparator) { entry ->
                val resolved = entry.replace("\$APPDIR", appJarDir.absolutePath)
                JFile(resolved).absolutePath
            }

            val aotConfigFile = JFile.createTempFile("zayit-aot-", ".aotconf")
            val aotCacheFile = JFile(appJarDir, "zayit.aot")
            val trainDurationSeconds = 20L

            val os = System.getProperty("os.name").lowercase()
            val isLinux = os.contains("linux")
            val needsXvfb = isLinux && System.getenv("DISPLAY").isNullOrEmpty()
            // Tell the app to self-terminate after the training period.
            // This ensures JVM shutdown hooks run reliably on all platforms.
            javaOptions += "-Daot.training.autoExit=$trainDurationSeconds"
            // Step 1: Record AOT configuration (launch app, wait, then terminate)
            logger.lifecycle("AOT training: recording class loading profile (~${trainDurationSeconds}s)...")
            val recordArgs = mutableListOf<String>()
            recordArgs += listOf(javaExe)
            recordArgs += listOf(
                "-XX:AOTMode=record",
                "-XX:AOTConfiguration=${aotConfigFile.absolutePath}",
                "-cp", classpath
            )
            recordArgs += javaOptions
            recordArgs += mainClass

            logger.lifecycle("AOT training: command = ${recordArgs.joinToString(" ")}")
            val recordLogFile = JFile.createTempFile("zayit-aot-record-", ".log")
            val recordProcessBuilder = ProcessBuilder(recordArgs)
                .directory(appDir)
                .redirectErrorStream(true)
                .redirectOutput(recordLogFile)
            // On headless Linux, start Xvfb and set DISPLAY in the process environment
            var xvfbProcess: Process? = null
            if (needsXvfb) {
                val display = ":99"
                xvfbProcess = ProcessBuilder("Xvfb", display, "-screen", "0", "1280x1024x24")
                    .redirectErrorStream(true)
                    .start()
                Thread.sleep(1000) // let Xvfb initialize
                recordProcessBuilder.environment()["DISPLAY"] = display
                logger.lifecycle("AOT training: started Xvfb on $display")
            }
            val recordProcess = recordProcessBuilder.start()

            // The app will self-terminate via System.exit(0) after trainDurationSeconds
            // (triggered by -Daot.training.autoExit). Wait with a generous safety margin.
            val deadline = System.currentTimeMillis() + (trainDurationSeconds + 30) * 1000
            while (recordProcess.isAlive && System.currentTimeMillis() < deadline) {
                Thread.sleep(500)
            }
            if (recordProcess.isAlive) {
                logger.warn("AOT training: app did not self-terminate within ${trainDurationSeconds + 30}s, forcing kill")
                recordProcess.destroyForcibly()
            }
            val recordExitCode = recordProcess.waitFor()
            xvfbProcess?.destroyForcibly()

            // Always log output for debugging
            val recordOutput = recordLogFile.readText().takeLast(3000)
            if (recordOutput.isNotBlank()) {
                logger.lifecycle("AOT record output (exit $recordExitCode):\n$recordOutput")
            } else {
                logger.lifecycle("AOT record phase exited with code $recordExitCode (no output)")
            }
            recordLogFile.delete()

            // Check for JVM crash dump files (hs_err_pid*.log) for debugging
            appDir.listFiles()?.filter { it.name.startsWith("hs_err_pid") }?.forEach { hsErr ->
                logger.lifecycle("AOT training: JVM crash dump found: ${hsErr.name}")
                logger.lifecycle(hsErr.readText().take(2000))
                hsErr.delete()
            }

            if (!aotConfigFile.exists() || aotConfigFile.length() == 0L) {
                throw GradleException(
                    "AOT configuration file was not created at ${aotConfigFile.absolutePath}. " +
                    "The app may have failed to start (exit code $recordExitCode)."
                )
            }
            logger.lifecycle("AOT training: recorded ${aotConfigFile.length() / 1024}KB of class profile data")

            // Step 2: Create AOT cache from the recorded configuration
            logger.lifecycle("AOT training: creating cache...")
            val createArgs = mutableListOf(
                javaExe,
                "-XX:AOTMode=create",
                "-XX:AOTConfiguration=${aotConfigFile.absolutePath}",
                "-XX:AOTCache=${aotCacheFile.absolutePath}",
                "-cp", classpath
            )
            createArgs += javaOptions
            createArgs += mainClass

            val createProcess = ProcessBuilder(createArgs)
                .directory(appDir)
                .inheritIO()
                .start()
            val createExitCode = createProcess.waitFor()
            if (createExitCode != 0) {
                throw GradleException("AOT cache creation failed with exit code $createExitCode")
            }

            // Clean up temp files
            aotConfigFile.delete()
            // NOTE: We intentionally do NOT clean up the provisioned java launcher and DLLs.
            // On Windows/Linux, when using --app-image for packaging (to preserve the AOT cache),
            // jpackage expects the runtime to be functional. The jlink'd runtime doesn't have
            // bin/java by default, so we must keep the provisioned launcher for the MSI/DEB to work.
            if (cleanUpProvisionedJava) {
                logger.lifecycle("AOT training: keeping provisioned java launcher for --app-image packaging")
            }

            if (!aotCacheFile.exists()) {
                throw GradleException("AOT cache file was not created at ${aotCacheFile.absolutePath}")
            }
            logger.lifecycle("AOT training: cache created at ${aotCacheFile.absolutePath} (${aotCacheFile.length() / 1024}KB)")

            // Inject -XX:AOTCache=$APPDIR/zayit.aot into the .cfg file
            val cfgContent = cfgFile.readText()
            if (!cfgContent.contains("AOTCache")) {
                val updatedContent = cfgContent.replace(
                    "[JavaOptions]",
                    "[JavaOptions]\njava-options=-XX:AOTCache=\$APPDIR/zayit.aot"
                )
                cfgFile.writeText(updatedContent)
                logger.lifecycle("AOT training: injected java-options=-XX:AOTCache=\$APPDIR/zayit.aot into ${cfgFile.name}")
            }
        }
    }
}

registerAotCacheTask("generateDistributableAotCache", "createDistributable", "main")
registerAotCacheTask("generateReleaseDistributableAotCache", "createReleaseDistributable", "main-release")

// Wire AOT cache generation into the release packaging pipeline automatically.
// This ensures packageReleaseDmg/Msi/Deb etc. always include the AOT cache.
//
// On macOS, the Compose plugin already uses --app-image (reuses the distributable).
// On Windows/Linux, the plugin recreates everything from scratch by default.
// We force Windows/Linux to also use --app-image by setting the appImage property,
// so the AOT cache injected into the distributable is preserved in the final installer.
afterEvaluate {
    val releaseAotTask = tasks.named("generateReleaseDistributableAotCache")
    val mainAotTask = tasks.named("generateDistributableAotCache")

    // Configure release packaging tasks
    // The appImage path must point to the app directory containing .jpackage.xml
    // Structure: build/compose/binaries/main-release/app/Zayit/ (Windows/Linux)
    val releaseAppImageDir = layout.buildDirectory.dir("compose/binaries/main-release/app/Zayit")
    tasks.matching { it.name.matches(Regex("packageRelease(Msi|Deb|Rpm)")) }.configureEach {
        dependsOn(releaseAotTask)
        // Force jpackage to use --app-image instead of rebuilding from scratch
        if (this is org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask) {
            appImage.set(releaseAppImageDir)
        }
    }
    tasks.matching { it.name.matches(Regex("packageRelease(Dmg|Pkg|DistributionForCurrentOS)")) }.configureEach {
        dependsOn(releaseAotTask)
        // macOS already uses --app-image by default, no need to set appImage
    }

    // Configure non-release packaging tasks
    val mainAppImageDir = layout.buildDirectory.dir("compose/binaries/main/app/Zayit")
    tasks.matching { it.name.matches(Regex("package(Msi|Deb|Rpm)")) && !it.name.contains("Release") }.configureEach {
        dependsOn(mainAotTask)
        if (this is org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask) {
            appImage.set(mainAppImageDir)
        }
    }
    tasks.matching { it.name.matches(Regex("package(Dmg|Pkg|DistributionForCurrentOS)")) && !it.name.contains("Release") }.configureEach {
        dependsOn(mainAotTask)
    }
}

tasks.register<Delete>("aotClean") {
    group = "compose desktop"
    description = "Delete the AOT cache to force re-training"
    delete(layout.buildDirectory.dir("aot-cache"))
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
