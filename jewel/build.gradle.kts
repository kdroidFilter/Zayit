plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
}


kotlin {
    jvmToolchain(25)
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
        }

        jvmMain.dependencies {
            implementation(libs.foundation.desktop)
            implementation(libs.jna)
            // Define the Jewel version once
            val jewelVersion = libs.versions.jewel.get()

            // Common exclusions for all Jewel libraries
            val jewelExclusions = Action<ExternalModuleDependency> {
                exclude(group = "org.jetbrains.compose.foundation", module = "foundation-desktop")
                exclude(group = "org.jetbrains.skiko", module = "skiko-awt")
                exclude(group = "org.jetbrains.skiko", module = "skiko-awt-runtime-all")
            }

            // Apply the exclusions to all Jewel libraries
            api("org.jetbrains.jewel:jewel-ui:$jewelVersion", jewelExclusions)
            api("org.jetbrains.jewel:jewel-decorated-window:$jewelVersion", jewelExclusions,)
            api("com.jetbrains.intellij.platform:icons:252.26830.102")
            api("org.jetbrains.runtime:jbr-api:1.9.0")
            api("org.jetbrains.jewel:jewel-markdown-core:$jewelVersion", jewelExclusions)
            api("org.jetbrains.jewel:jewel-markdown-extensions-autolink:$jewelVersion", jewelExclusions)
            api("org.jetbrains.jewel:jewel-markdown-int-ui-standalone-styling:$jewelVersion", jewelExclusions)
        }

    }
}
