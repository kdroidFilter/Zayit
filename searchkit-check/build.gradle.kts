// A test harness, no UI: an Android app that holds searchkit as a real app does (the launcher of the Java process among
// its native libraries, which a library's test APK can't hold) and runs the search on a device or an emulator through
// its instrumented test.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.kdroidfilter.seforimapp.searchkit.check"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.kdroidfilter.seforimapp.searchkit.check"
        minSdk = 29
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The launcher is an executable: installed as a file in the native library dir, to be run from there
    packaging { jniLibs { useLegacyPackaging = true } }

    sourceSets {
        // The launcher of the Java runtime (searchkit's assembleAndroidJavaRuntime)
        getByName("main").jniLibs.directories.add("../searchkit/build/androidJavaRuntime/jniLibs")
        // The dump compared across platforms
        getByName("androidTest").kotlin.directories.add("../searchkit/src/commonTest/kotlin")
    }
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("JniLibFolders") }.configureEach {
    dependsOn(":searchkit:assembleAndroidJavaRuntime")
}

dependencies {
    implementation(project(":searchkit"))
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(kotlin("test"))
}
