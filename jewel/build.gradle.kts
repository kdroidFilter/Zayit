plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
}

kotlin {
    jvmToolchain(
        libs.versions.jvmToolchain
            .get()
            .toInt(),
    )
    jvm {
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
        }

        jvmMain.dependencies {
            implementation(libs.foundation.desktop)
            api(libs.intellij.platform.icons)
            api(libs.jbr.api)

            // Jewel libraries with common exclusions
            listOf(
                libs.jewel.ui,
                libs.jewel.markdown.core,
                libs.jewel.markdown.extensions.autolink,
                libs.jewel.markdown.int.ui.standalone.styling,
            ).forEach { dep ->
                api(dep.get().toString()) {
                    exclude(group = "org.jetbrains.compose.foundation", module = "foundation-desktop")
                    exclude(group = "org.jetbrains.skiko", module = "skiko-awt")
                    exclude(group = "org.jetbrains.skiko", module = "skiko-awt-runtime-all")
                }
            }
        }
    }
}
