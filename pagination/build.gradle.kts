import org.jetbrains.compose.reload.gradle.ComposeHotRun
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlinx.serialization)
    id("com.android.library")
}

kotlin {
    androidTarget {
        //https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-test.html
        instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
    }
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation("io.github.kdroidfilter.seforimlibrary:core")
            implementation("io.github.kdroidfilter.seforimlibrary:dao")
            implementation(compose.runtime)
            implementation(compose.foundation)
            // AndroidX Paging 3 (common)
            implementation(libs.androidx.paging.common)
            implementation(project(":logger"))

        }

        androidMain.dependencies {
        }

        jvmMain.dependencies {
        }

    }
}

android {
    namespace = "io.github.kdroidfilter.seforimapp"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // targetSdk is deprecated for library modules; configure test and lint target SDKs instead
    testOptions {
        targetSdk = 35
    }
    lint {
        targetSdk = 35
    }
}

//https://developer.android.com/develop/ui/compose/testing#setup
dependencies {
    androidTestImplementation(libs.androidx.uitest.junit4)
    debugImplementation(libs.androidx.uitest.testManifest)
}


tasks.withType<ComposeHotRun>().configureEach {
    mainClass.set("MainKt")
}
