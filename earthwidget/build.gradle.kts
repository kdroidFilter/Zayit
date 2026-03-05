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
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.components.resources)
        }

        jvmMain.dependencies {
            api(project(":jewel"))
            api(project(":hebrewcalendar"))
            implementation(compose.desktop.currentOs) {
                exclude(group = "org.jetbrains.compose.material")
            }
            implementation(libs.zmanim)
        }

        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
