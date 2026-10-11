plugins {
    alias(libs.plugins.multiplatform)
}

kotlin {
    jvm()
    jvmToolchain(
        libs.versions.jvmToolchain
            .get()
            .toInt(),
    )

    sourceSets {
        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }

        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
