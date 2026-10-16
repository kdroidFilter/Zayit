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
 * process on the JVM, in the Java runtime the mobile apps embed (see SeforimLibrary's IOS_RUNTIME.md).
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
        // Lucene runs in a Java process of the app's own (AndroidJavaRuntime), from Android 10
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

// The Java runtimes of the mobile apps (OpenJDK's mobile port), downloaded by SeforimLibrary's search (see its
// IOS_RUNTIME.md):
// build/javaRuntime/<target> there
val seforimLibrary = gradle.includedBuild("SeforimLibrary")

fun javaRuntime(target: String): File = seforimLibrary.projectDir.resolve("search/build/javaRuntime/$target")

fun javaRuntimeTask(target: String) =
    seforimLibrary.task(":search:javaRuntime${target.split("-").joinToString("") { it.replaceFirstChar(Char::uppercase) }}")

// ONNX Runtime's xcframework, for the semantic search in the iOS tests: -Pseforim.ortXcframework=<dir>
val ortXcframework: File? = providers.gradleProperty("seforim.ortXcframework").orNull?.let(::file)
val jdkStaticLibs =
    listOf(
        "libjvm.a",
        "libjava.a",
        "libnio.a",
        "libnet.a",
        "libzip.a",
        "libjimage.a",
        "libverify.a",
        "libextnet.a",
        "libfallbackLinker.a",
        "libsyslookup.a",
    )

// What an iOS binary links for the search: the Java runtime (force-loaded: it finds its natives by name), its libffi,
// and ONNX Runtime for the semantic search
fun org.jetbrains.kotlin.gradle.plugin.mpp.NativeBinary.linkJavaRuntime(
    target: String,
    ortSlice: File?,
) {
    val static = javaRuntime(target).resolve("static")
    linkerOpts(jdkStaticLibs.flatMap { listOf("-force_load", static.resolve(it).path) })
    linkerOpts(static.resolve("libffi.a").path, "-export_dynamic", "-lz", "-lc++")
    listOf("Foundation", "CoreFoundation", "Security", "SystemConfiguration").forEach { linkerOpts("-framework", it) }
    if (ortSlice != null) linkerOpts("-F", ortSlice.path, "-framework", "onnxruntime")
    linkTaskProvider.configure { dependsOn(javaRuntimeTask(target)) }
}

kotlin {
    iosSimulatorArm64 {
        binaries.withType<org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable>().configureEach {
            linkJavaRuntime("ios-simulator", ortXcframework?.resolve("ios-arm64_x86_64-simulator"))
        }
    }
    // A device build of the tests only checks that everything links (no device runs them here)
    iosArm64 {
        binaries.withType<org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable>().configureEach {
            linkJavaRuntime("ios-device", ortXcframework?.resolve("ios-arm64"))
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

// SeforimLibrary's jars for the embedded runtime (its Lucene side, see IOS_RUNTIME.md)
val searchJars = seforimLibrary.projectDir.resolve("search/build/mobileJvmClasspath")
val searchJarsTask = seforimLibrary.task(":search:mobileJvmClasspath")

// The Java runtime an iOS app bundles as App.app/lib (its java.home): the runtime's classes, conf and data, and the
// search's jars in lib/app (IosJavaRuntime's default class path)
fun Sync.iosJavaRuntime(target: String) {
    dependsOn(searchJarsTask, javaRuntimeTask(target))
    from(javaRuntime(target).resolve("lib"))
    from(searchJars) { into("app") }
    // Some of the JDK's files are read-only: the next sync couldn't replace them
    filePermissions { user { write = true } }
}

// For the Xcode project: copy build/iosJavaRuntime/<target>/lib into the app bundle, link build/iosJavaRuntime/<target>/
// static (see IOS_RUNTIME.md)
listOf("simulator", "device").forEach { kind ->
    tasks.register<Sync>("assembleIosJavaRuntime${kind.replaceFirstChar { it.uppercase() }}") {
        group = "build"
        description = "Assembles the Java runtime the iOS app bundles as App.app/lib, and links ($kind)."
        dependsOn(searchJarsTask, javaRuntimeTask("ios-$kind"))
        from(javaRuntime("ios-$kind").resolve("static")) { into("static") }
        from(javaRuntime("ios-$kind").resolve("lib")) { into("lib") }
        from(searchJars) { into("lib/app") }
        into(layout.buildDirectory.dir("iosJavaRuntime/$kind"))
        filePermissions { user { write = true } }
    }
}

// For an Android app (Lucene in a Java process, see AndroidJavaRuntime): build/androidJavaRuntime/jniLibs (the launcher,
// among the app's native libraries), jdk (the JDK's classes and data, its java.home) and jdk-app (the search's jars),
// both delivered into the app's files
tasks.register<Sync>("assembleAndroidJavaRuntime") {
    group = "build"
    description = "Assembles the Java runtime an Android app delivers for Lucene."
    dependsOn(searchJarsTask, javaRuntimeTask("android"))
    from(javaRuntime("android").resolve("jniLibs")) { into("jniLibs") }
    from(javaRuntime("android").resolve("lib")) { into("jdk") }
    from(searchJars) { into("jdk-app") }
    into(layout.buildDirectory.dir("androidJavaRuntime"))
    filePermissions { user { write = true } }
}

run {
    val testBinaryDir = layout.buildDirectory.dir("bin/iosSimulatorArm64/debugTest")

    val iosTestRuntime by tasks.registering(Sync::class) {
        iosJavaRuntime("ios-simulator")
        into(testBinaryDir.map { it.dir("lib") })
    }

    tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
        dependsOn(iosTestRuntime)
        device.set(providers.gradleProperty("seforim.iosSimulator").orElse("iPhone 17 Pro"))
        val binaryDir = testBinaryDir.get().asFile
        val jarsDir = searchJars
        val jars = providers.provider { jarsDir.listFiles().orEmpty().map { binaryDir.resolve("lib/app/${it.name}").path } }
        val models = providers.gradleProperty("seforim.semanticModels")
        val db = providers.gradleProperty("seforimTestDb")
        val out =
            parityDir
                .get()
                .file("ios.txt")
                .asFile.path
        doFirst {
            // The simulator passes the SIMCTL_CHILD_ variables to the test process, without the prefix
            environment("SIMCTL_CHILD_SEFORIM_JVM_CLASSPATH", jars.get().joinToString(":"))
            db.orNull?.let { environment("SIMCTL_CHILD_SEFORIM_TEST_DB", it) }
            models.orNull?.let { environment("SIMCTL_CHILD_SEFORIM_MODEL_DIR", it) }
            environment("SIMCTL_CHILD_SEFORIM_PARITY_OUT", out)
        }
    }
}
