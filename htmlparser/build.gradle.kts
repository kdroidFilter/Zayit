import org.jetbrains.compose.reload.gradle.ComposeHotRun

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(
        libs.versions.jvmToolchain
            .get()
            .toInt(),
    )

    jvm()
    android {
        namespace = "io.github.kdroidfilter.seforim.htmlparser"
        compileSdk = 37
        minSdk = 21
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        // The DOM: jsoup on the JVM and Android, its Kotlin port Ksoup on iOS (see HtmlDom.kt)
        val androidJvmMain by creating {
            dependsOn(commonMain.get())
            dependencies { implementation(libs.jsoup) }
        }
        jvmMain { dependsOn(androidJvmMain) }
        androidMain { dependsOn(androidJvmMain) }

        // The manual source set above turns off the default hierarchy: iOS shares its own one by hand
        val iosMain by creating {
            dependsOn(commonMain.get())
            dependencies { implementation(libs.ksoup) }
        }
        iosArm64Main { dependsOn(iosMain) }
        iosSimulatorArm64Main { dependsOn(iosMain) }
    }
}

tasks.withType<ComposeHotRun>().configureEach {
    mainClass.set("MainKt")
}
