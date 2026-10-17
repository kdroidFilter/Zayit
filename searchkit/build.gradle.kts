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
// GraalVM native image (search/build/nja/nativeImage/<target>/nja.o, which NJA builds) with graalvm-mobile's static
// libraries (search/build/nja/graalvmMobile/<target>/lib, which NJA downloads): nothing to bundle
val seforimLibrary = gradle.includedBuild("SeforimLibrary")
val searchBuild = seforimLibrary.projectDir.resolve("search/build")

fun searchTask(name: String) = seforimLibrary.task(":search:$name")

// ONNX Runtime's xcframework, for the semantic search in the iOS tests: -Pseforim.ortXcframework=<dir>
val ortXcframework: File? = providers.gradleProperty("seforim.ortXcframework").orNull?.let(::file)
// Substrate VM's and the JDK's natives, from graalvm-mobile's kit
val nativeImageLibs = listOf("jvm", "libchelper", "darwin", "java", "nio", "net", "zip", "extnet", "jimage", "verify", "management_ext", "management", "prefs")

fun searchStage(runtime: String) = runtime.split("-").joinToString("") { it.replaceFirstChar(Char::uppercase) }

// What an iOS binary links for the search: its native image (entry points exported: NJA's client finds them at run
// time), the kit's libraries, and ONNX Runtime for the semantic search
fun org.jetbrains.kotlin.gradle.plugin.mpp.NativeBinary.linkJavaRuntime(
    runtime: String,
    ortSlice: File?,
) {
    val lib = searchBuild.resolve("nja/graalvmMobile/$runtime/lib")
    linkerOpts(searchBuild.resolve("nja/nativeImage/$runtime/nja.o").path)
    linkerOpts(nativeImageLibs.map { lib.resolve("lib$it.a").path })
    linkerOpts("-export_dynamic", "-lz", "-lc++", "-framework", "Foundation", "-framework", "CoreFoundation")
    if (ortSlice != null) linkerOpts("-F", ortSlice.path, "-framework", "onnxruntime")
    linkTaskProvider.configure { dependsOn(searchTask("njaNativeImage${searchStage(runtime)}")) }
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

// For the Xcode project: build/iosJavaRuntime/<target>/static: what the app links (see linkJavaRuntime), the native
// image (nja.o) and the kit's libraries; nothing to copy into App.app
listOf("simulator", "device").forEach { kind ->
    tasks.register<Sync>("assembleIosJavaRuntime${kind.replaceFirstChar { it.uppercase() }}") {
        group = "build"
        description = "Assembles what the iOS app links for the search's native image ($kind)."
        val runtime = "ios-$kind-aarch64"
        dependsOn(searchTask("njaNativeImage${searchStage(runtime)}"))
        from(searchBuild.resolve("nja/nativeImage/$runtime/nja.o")) { into("static") }
        from(searchBuild.resolve("nja/graalvmMobile/$runtime/lib")) {
            include(nativeImageLibs.map { "lib$it.a" })
            into("static")
        }
        into(layout.buildDirectory.dir("iosJavaRuntime/$kind"))
        filePermissions { user { write = true } }
    }
}

run {
    tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
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
