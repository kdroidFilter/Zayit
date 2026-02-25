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
        jvmMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.serialization)
            implementation(libs.ktor.serialization.json)
            implementation(libs.nucleus.native.ssl)
            implementation(libs.nucleus.native.http.ktor)
        }
    }
}
