import io.github.kdroidfilter.buildsrc.Versioning

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
}

val version = Versioning.resolveVersion(project)

kotlin {
    jvmToolchain(
        libs.versions.jvmToolchain
            .get()
            .toInt(),
    )

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":logger"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.components.resources)
            implementation(libs.filament.compose)
        }

        jvmMain.dependencies {
            api(project(":jewel"))
            implementation(project(":luach"))
            implementation(compose.desktop.currentOs) {
                exclude(group = "org.jetbrains.compose.material")
            }
            implementation(libs.kosherkotlin)
        }

        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
