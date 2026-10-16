// A test harness, no UI: an Android app that holds searchkit as a real app does (the Java runtime NJA brings with
// SeforimLibrary's search: its native libraries and assets) and runs the search on a device or an emulator through its
// instrumented test.
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

    sourceSets {
        // The dump compared across platforms
        getByName("androidTest").kotlin.directories.add("../searchkit/src/commonTest/kotlin")
    }
}

dependencies {
    implementation(project(":searchkit"))
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(kotlin("test"))
}
