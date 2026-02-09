plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {

    jvm()
    jvmToolchain(
        libs.versions.jvmToolchain
            .get()
            .toInt(),
    )

    sourceSets {
        commonMain.dependencies {
        }

        jvmMain.dependencies {
            implementation(libs.sentry.core)
        }
    }
}
