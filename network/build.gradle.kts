plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    jvm()
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())

    sourceSets {
        jvmMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.serialization)
            implementation(libs.ktor.serialization.json)
            implementation("org.jetbrains.nativecerts:jvm-native-trusted-roots:${libs.versions.jvmNativeTrustedRoots.get()}") {
                exclude(group = "org.bouncycastle", module = "bcpkix-jdk18on")
                exclude(group = "org.bouncycastle", module = "bcprov-jdk18on")
            }
            // Re-introduce BouncyCastle via local, de-signed jars placed in this module's `libs/` folder.
            // See `network/libs/README-bouncycastle.txt` for instructions to strip signatures.
            implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
        }
    }
}
