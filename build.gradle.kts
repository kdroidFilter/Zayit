plugins {
    alias(libs.plugins.multiplatform).apply(false)
    alias(libs.plugins.compose.compiler).apply(false)
    alias(libs.plugins.compose).apply(false)
    alias(libs.plugins.android.application).apply(false)
    alias(libs.plugins.hotReload).apply(false)
    alias(libs.plugins.kotlinx.serialization).apply(false)
    alias(libs.plugins.buildConfig).apply(false)
    alias(libs.plugins.maven.publish).apply(false)
    alias(libs.plugins.sqlDelight).apply(false)
    alias(libs.plugins.metro).apply(false)
    alias(libs.plugins.caupain)
    alias(libs.plugins.stability.analyzer) apply false
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover).apply(false)
    alias(libs.plugins.structured.coroutines).apply(false)
    alias(libs.plugins.sentryJvmGradle).apply(false)
    alias(libs.plugins.detekt)
}

allprojects {
    // Exclude jewel module from ktlint (JetBrains fork with its own style)
    if (project.name != "jewel") {
        apply(plugin = "org.jlleitschuh.gradle.ktlint")
    }
    apply(plugin = "dev.detekt")

    if (project.name != "jewel") {
        ktlint {
            version.set("1.5.0")
            android.set(true)
            outputToConsole.set(true)
            ignoreFailures.set(false)
            filter {
                exclude("**/generated/**")
                exclude("**/build/**")
            }
        }

        dependencies {
            add("ktlintRuleset", "io.nlopez.compose.rules:ktlint:0.5.3")
        }
    }

    detekt {
        buildUponDefaultConfig = true
        config.setFrom(files("${rootProject.projectDir}/detekt.yml"))
        parallel = true
    }
}
