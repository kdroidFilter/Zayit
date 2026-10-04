rootProject.name = "SeforimApp"

pluginManagement {
    repositories {
        mavenLocal()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("android.*")
            }
        }
        gradlePluginPortal()
        mavenCentral()
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
    }
}

/**
 * The token that reads the smart siddur's private package: siddur.token, SEFORIM_SIDDUR_TOKEN (CI), or the gh CLI's
 * when it can read that package (gh auth refresh -s read:packages); none in a community build.
 */
val ghPath: String? =
    providers
        .environmentVariable("PATH")
        .getOrElse("")
        .split(File.pathSeparator)
        .flatMap { listOf(File(it, "gh"), File(it, "gh.exe")) }
        .firstOrNull { it.canExecute() }
        ?.path

fun ghOutput(vararg args: String): String? =
    if (ghPath == null) {
        null
    } else {
        val gh =
            providers.exec {
                commandLine(ghPath, *args)
                isIgnoreExitValue = true
            }
        gh.standardOutput.asText
            .get()
            .trim()
            .takeIf { gh.result.get().exitValue == 0 }
    }

val siddurToken: String? =
    providers.gradleProperty("siddur.token").orNull
        ?: providers.environmentVariable("SEFORIM_SIDDUR_TOKEN").orNull
        ?: ghOutput("api", "users/kdroidFilter/packages/maven/io.github.kdroidfilter.seforim-siddur", "--jq", ".name")?.let {
            ghOutput("auth", "token")
        }
gradle.extensions.extraProperties["siddurEnabled"] = siddurToken != null || providers.gradleProperty("siddur.local").orNull == "true"

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("android.*")
            }
        }
        mavenCentral()
        maven("https://packages.jetbrains.team/maven/p/kpm/public/")
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/")
        maven("https://www.jetbrains.com/intellij-repository/releases")
        maven("https://www.jetbrains.com/intellij-repository/snapshots")
        // The smart siddur, for the official builds (open core): a private package, read with a token
        siddurToken?.let { token ->
            maven("https://maven.pkg.github.com/kdroidFilter/SeforimSiddur") {
                credentials {
                    username = "token"
                    password = token
                }
                content { includeGroupAndSubgroups("io.github.kdroidfilter") }
            }
        }
    }
}
plugins {
    // https://github.com/JetBrains/compose-hot-reload?tab=readme-ov-file#set-up-automatic-provisioning-of-the-jetbrains-runtime-jbr-via-gradle
    id("org.gradle.toolchains.foojay-resolver-convention").version("1.0.0")
}

include(":SeforimApp")
include(":cataloggen")
include(":widgetprocessor")

include(":jewel")
include((":htmlparser"))
include(":navigation")
include(":icons")
include(":earthwidget")
include(":luach")
include(":pagination")
include(":logger")
include(":texteffects")
include(":network")
include(":releasefetcher")
includeBuild("SeforimLibrary")
// The smart siddur's sources beside Zayit's (../SeforimSiddur), to work on both at once
if (providers.gradleProperty("siddur.local").orNull == "true") includeBuild("../SeforimSiddur")
