plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlinx.serialization)
    // The view models and stores contribute their bindings to the app's graph (the desktop's AppGraph)
    alias(libs.plugins.metro)
    alias(libs.plugins.sqlDelight)
    alias(libs.plugins.structured.coroutines)
}

structuredCoroutines {
    useStrictProfile()
}

val withQuoteLinks = gradle.extensions.extraProperties["quoteLinksEnabled"] == true

/**
 * Zayit without its desktop shell: the reader's state, use cases and view models, the user's data (highlights, notes,
 * favorites, history), the settings and the onboarding's logic, for the desktop app and the mobile ones (Android 10+,
 * iOS). The screens stay in each app (Jewel on the desktop).
 */
kotlin {
    jvmToolchain(
        libs.versions.jvmToolchain
            .get()
            .toInt(),
    )

    jvm()
    android {
        namespace = "io.github.kdroidfilter.seforimapp.appkit"
        compileSdk = 37
        // As searchkit, whose search runs from Android 10
        minSdk = 29
        // The Compose resources (composeResources) packed in the library
        androidResources { enable = true }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.seforimlibrary.core)
            api(libs.seforimlibrary.dao)
            api(project(":searchkit"))
            api(project(":navigation"))
            implementation(project(":htmlparser"))
            implementation(project(":logger"))
            api(project(":pagination"))
            // The 3D views' settings (anti-aliasing), the Earth's and sky's locations and opinions
            api(project(":earthwidget"))

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            // The app's strings, fonts and pictures (composeResources), shared with the mobile apps
            api(libs.compose.components.resources)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodel.savedstate)
            implementation(libs.metrox.viewmodel)
            implementation(libs.androidx.paging.common)
            implementation(libs.navigation.compose)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.serialization.protobuf)
            implementation(libs.structured.coroutines.annotations)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.kotlinx.io.core)
            // The database bundle's extraction: zstd (native on every platform) and SHA-256 over Okio
            implementation(libs.okio)
            implementation(libs.zstd.kmp.okio)
            implementation(libs.multiplatformSettings)
            implementation(libs.filekit.core)
            implementation(libs.hebrew.numerals)
            implementation(libs.seforimlibrary.cache)
            implementation(libs.atomicfu)
        }

        // The verse quote links of the Talmud (open core, temporary until the DB carries them): a JVM package, in the
        // official builds only; a community build compiles addons/quotelinks/disabled, and mobile has none
        jvmMain {
            kotlin.srcDir(if (withQuoteLinks) "addons/quotelinks/enabled/kotlin" else "addons/quotelinks/disabled/kotlin")
            if (withQuoteLinks) dependencies { implementation(libs.seforim.quotelinks) }
        }

        jvmMain.dependencies { implementation(libs.sqlite.driver) }
        androidMain.dependencies { implementation(libs.sqldelight.android.driver) }
        iosMain.dependencies { implementation(libs.sqldelight.native.driver) }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.okio.fakefilesystem)
            implementation(libs.multiplatformSettings.test)
        }

        jvmTest.dependencies {
            implementation(libs.mockk)
            // The archives the database bundle's packager writes, to read them back
            implementation(libs.commons.compress)
            implementation(libs.zstd.jni)
        }
    }
}

// The app's resources keep the Res class they had in SeforimApp, public for the apps' screens
compose.resources {
    publicResClass = true
    packageOfResClass = "seforimapp.seforimapp.generated.resources"
}

sqldelight {
    databases {
        create("UserSettingsDb") {
            packageName.set("io.github.kdroidfilter.seforimapp.db")
            dialect("app.cash.sqldelight:sqlite-3-24-dialect:${libs.versions.sqlDelight.get()}")
        }
    }
}

// A real database bundle extracted in the iOS simulator (IosExtractBundleTest): -PseforimTestBundle=<.tar.zst or .part01>
// and -PseforimTestBundleSha256=<the extracted database's>; the simulator sees the host's files
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
    device.set(providers.gradleProperty("seforim.iosSimulator").orElse("iPhone 17 Pro"))
    val bundle = providers.gradleProperty("seforimTestBundle")
    val sha256 = providers.gradleProperty("seforimTestBundleSha256")
    doFirst {
        // The simulator passes the SIMCTL_CHILD_ variables to the test process, without the prefix
        bundle.orNull?.let { environment("SIMCTL_CHILD_SEFORIM_TEST_BUNDLE", it) }
        sha256.orNull?.let { environment("SIMCTL_CHILD_SEFORIM_TEST_BUNDLE_SHA256", it) }
    }
}
