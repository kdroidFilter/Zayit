rootProject.name = "SeforimApp"

pluginManagement {
    repositories {
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
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            content { 
              	includeGroupByRegex("com\\.android.*")
              	includeGroupByRegex("com\\.google.*")
              	includeGroupByRegex("androidx.*")
              	includeGroupByRegex("android.*")
            }
        }
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://packages.jetbrains.team/maven/p/kpm/public/")
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/")
        maven("https://www.jetbrains.com/intellij-repository/releases")
        maven("https://www.jetbrains.com/intellij-repository/snapshots")
    }
}
plugins {
    //https://github.com/JetBrains/compose-hot-reload?tab=readme-ov-file#set-up-automatic-provisioning-of-the-jetbrains-runtime-jbr-via-gradle
    id("org.gradle.toolchains.foojay-resolver-convention").version("0.10.0")
}

include(":SeforimApp")
include(":cataloggen")

include(":jewel")
include((":htmlparser"))
include(":navigation")
include(":icons")
include(":earthwidget")
include(":pagination")
include(":logger")
include(":texteffects")
include(":network")
includeBuild("SeforimLibrary")
