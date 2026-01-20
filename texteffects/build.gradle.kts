plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    androidLibrary {
        namespace = "io.github.kdroidfilter.seforimapp"
        compileSdk = 35
        minSdk = 21
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
        }

        androidMain.dependencies {
        }

        jvmMain.dependencies {
        }
    }
}
