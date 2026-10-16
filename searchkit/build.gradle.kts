plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlinx.serialization)
    // The view models contribute their factories to the app's graph (the desktop's AppGraph)
    alias(libs.plugins.metro)
}

// The semantic search is in the official builds only (open core): with its package's token or its sources beside
// (settings.gradle.kts); a community build compiles addons/semantic/disabled and the search stays lexical
val withSemantic = gradle.extensions.extraProperties["semanticEnabled"] == true

/**
 * Zayit's search, without its UI: the engines (lexical, hybrid with the semantic search), the lookup, the use cases and
 * the view models of the search screens, for the desktop app and the mobile ones (Android 10+, iOS). Lucene runs in
 * process on the JVM, in the Java runtime the mobile apps embed (see SeforimLibrary's JVM_RUNTIME.md).
 */
kotlin {
    jvmToolchain(
        libs.versions.jvmToolchain
            .get()
            .toInt(),
    )

    jvm()
    android {
        namespace = "io.github.kdroidfilter.seforimapp.searchkit"
        compileSdk = 37
        // NJA's bridge runs from Android 10
        minSdk = 29
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            kotlin.srcDir(if (withSemantic) "addons/semantic/enabled/kotlin" else "addons/semantic/disabled/kotlin")
            dependencies {
                api(libs.seforimlibrary.core)
                api(libs.seforimlibrary.dao)
                api(libs.seforimlibrary.search)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.collections.immutable)
                implementation(libs.kotlinx.io.core)
                implementation(libs.androidx.lifecycle.viewmodel.core)
                implementation(libs.androidx.lifecycle.viewmodel.savedstate)
                implementation(libs.metrox.viewmodel)
                api(project(":navigation"))
                implementation(libs.compose.runtime)
                implementation(libs.hebrew.numerals)
                implementation(libs.kermit)
                implementation(libs.cachemap)
                implementation(libs.atomicfu)
                if (withSemantic) implementation(libs.seforim.semantic)
            }
        }

        jvmMain.dependencies { implementation(libs.sqlite.driver) }
        androidMain.dependencies { implementation(libs.sqldelight.android.driver) }
        iosMain.dependencies { implementation(libs.sqldelight.native.driver) }

        // The model's location differs by platform: the desktop's jar carries it, mobile apps download it
        if (withSemantic) {
            jvmMain { kotlin.srcDir("addons/semantic/enabled/jvm/kotlin") }
            androidMain { kotlin.srcDir("addons/semantic/enabled/mobile/kotlin") }
            iosMain { kotlin.srcDir("addons/semantic/enabled/mobile/kotlin") }
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// --- Tests on real data: the same dump on the JVM and in the iOS simulator (SearchKitDump) ---

// The Java runtime of the mobile apps comes with SeforimLibrary's search, through Nucleus JVM Access (see its
// JVM_RUNTIME.md): Android gets it from search's library (its native libraries and assets); an iOS binary links its
// static libraries (search/build/jdkMobile/<target>) and carries NJA's bundle (search/build/nja/ios/<target>)
val seforimLibrary = gradle.includedBuild("SeforimLibrary")
val searchBuild = seforimLibrary.projectDir.resolve("search/build")

fun searchTask(name: String) = seforimLibrary.task(":search:$name")

// ONNX Runtime's xcframework, for the semantic search in the iOS tests: -Pseforim.ortXcframework=<dir>
val ortXcframework: File? = providers.gradleProperty("seforim.ortXcframework").orNull?.let(::file)
val jdkStaticLibs = listOf("jvm", "java", "nio", "net", "zip", "jimage", "verify", "extnet", "fallbackLinker", "syslookup")

// What an iOS binary links for the search: the Java runtime (force-loaded: it finds its natives by name), its libffi,
// and ONNX Runtime for the semantic search
fun org.jetbrains.kotlin.gradle.plugin.mpp.NativeBinary.linkJavaRuntime(
    runtime: String,
    ortSlice: File?,
) {
    val static = searchBuild.resolve("jdkMobile/$runtime/static")
    linkerOpts(jdkStaticLibs.flatMap { listOf("-force_load", static.resolve("lib$it.a").path) })
    linkerOpts(static.resolve("libffi.a").path, "-export_dynamic", "-lz", "-lc++")
    listOf("Foundation", "CoreFoundation", "Security", "SystemConfiguration").forEach { linkerOpts("-framework", it) }
    if (ortSlice != null) linkerOpts("-F", ortSlice.path, "-framework", "onnxruntime")
    val stage = runtime.split("-").joinToString("") { it.replaceFirstChar(Char::uppercase) }
    linkTaskProvider.configure { dependsOn(searchTask("stageJdkMobile$stage")) }
}

kotlin {
    iosSimulatorArm64 {
        binaries.withType<org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable>().configureEach {
            linkJavaRuntime("ios-simulator-aarch64", ortXcframework?.resolve("ios-arm64_x86_64-simulator"))
        }
    }
    // A device build of the tests only checks that everything links (no device runs them here)
    iosArm64 {
        binaries.withType<org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable>().configureEach {
            linkJavaRuntime("ios-device-aarch64", ortXcframework?.resolve("ios-arm64"))
        }
    }
}

val testDb = providers.gradleProperty("seforimTestDb")
val parityDir = layout.buildDirectory.dir("parity")

tasks.named<Test>("jvmTest") {
    testDb.orNull?.let { systemProperty("seforimTestDb", it) }
    val out = parityDir.get().file("jvm.txt").asFile
    systemProperty("parityOut", out.path)
    testLogging.showStandardStreams = true
    doFirst { out.parentFile.mkdirs() }
}

// For the Xcode project: build/iosJavaRuntime/<target>: static/ (what the app links, see linkJavaRuntime) and bundle/
// (lib/, the VM's java.home, and nja/: the façades' class path and the VM's options), copied into App.app
listOf("simulator" to "IosSimulatorArm64", "device" to "IosArm64").forEach { (kind, konan) ->
    tasks.register<Sync>("assembleIosJavaRuntime${kind.replaceFirstChar { it.uppercase() }}") {
        group = "build"
        description = "Assembles what the iOS app links and bundles for the search's Java runtime ($kind)."
        dependsOn(searchTask("stageJdkMobile${if (kind == "simulator") "IosSimulatorAarch64" else "IosDeviceAarch64"}"))
        dependsOn(searchTask("njaIosBundle$konan"))
        from(searchBuild.resolve("jdkMobile/ios-$kind-aarch64/static")) { into("static") }
        from(searchBuild.resolve("nja/ios/${konan.replaceFirstChar(Char::lowercase)}")) { into("bundle") }
        into(layout.buildDirectory.dir("iosJavaRuntime/$kind"))
        filePermissions { user { write = true } }
    }
}

run {
    val testBinaryDir = layout.buildDirectory.dir("bin/iosSimulatorArm64/debugTest")

    // NJA's bundle next to the test binary: the VM's java.home is <executable dir>/lib
    val iosTestRuntime by tasks.registering(Copy::class) {
        dependsOn(searchTask("njaIosBundleIosSimulatorArm64"))
        from(searchBuild.resolve("nja/ios/iosSimulatorArm64"))
        into(testBinaryDir)
        filePermissions { user { write = true } }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
        dependsOn(iosTestRuntime)
        device.set(providers.gradleProperty("seforim.iosSimulator").orElse("iPhone 17 Pro"))
        val models = providers.gradleProperty("seforim.semanticModels")
        val db = providers.gradleProperty("seforimTestDb")
        val out =
            parityDir
                .get()
                .file("ios.txt")
                .asFile.path
        doFirst {
            // The simulator passes the SIMCTL_CHILD_ variables to the test process, without the prefix
            db.orNull?.let { environment("SIMCTL_CHILD_SEFORIM_TEST_DB", it) }
            models.orNull?.let { environment("SIMCTL_CHILD_SEFORIM_MODEL_DIR", it) }
            environment("SIMCTL_CHILD_SEFORIM_PARITY_OUT", out)
        }
    }
}
