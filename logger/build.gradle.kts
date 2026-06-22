plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    applyDefaultHierarchyTemplate()

    jvmToolchain(
        libs.versions.jvmToolchain
            .get()
            .toInt(),
    )

    androidLibrary {
        namespace = "io.github.kdroidfilter.seforimapp.logger"
        compileSdk = 36
        minSdk = 21
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val androidJvmMain by creating { dependsOn(commonMain.get()) }
        jvmMain.get().dependsOn(androidJvmMain)
        androidMain.get().dependsOn(androidJvmMain)

        commonMain.dependencies {
        }

        jvmMain.dependencies {
            implementation(libs.sentry.core)
        }
    }
}
