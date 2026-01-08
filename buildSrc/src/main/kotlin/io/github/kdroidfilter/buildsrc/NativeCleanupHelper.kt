package io.github.kdroidfilter.buildsrc

import org.gradle.api.Project

object NativeCleanupHelper {
    private val hostOs: String = System.getProperty("os.name") ?: error("Unable to detect OS")
    private val hostArch: String = System.getProperty("os.arch") ?: error("Unable to detect architecture")

    val nativeFolderToKeep: String = when {
        hostOs == "Mac OS X" && hostArch == "aarch64" -> "darwin-aarch64"
        hostOs == "Mac OS X" -> "darwin-x86-64"
        hostOs.startsWith("Windows") && hostArch == "amd64" -> "win32-x86-64"
        hostOs.startsWith("Windows") && hostArch == "aarch64" -> "win32-arm64"
        hostOs.startsWith("Windows") -> "win32-x86"
        hostOs == "Linux" && hostArch == "aarch64" -> "linux-arm64"
        hostOs == "Linux" -> "linux-x86-64"
        else -> error("Unsupported platform: $hostOs / $hostArch")
    }

    val allNativeFolders: List<String> = listOf(
        "darwin-aarch64", "darwin-x86-64",
        "win32-x86-64", "win32-x86", "win32-arm64",
        "linux-x86-64", "linux-arm64"
    )

    val nativeFoldersToRemove: List<String> = allNativeFolders - nativeFolderToKeep

    data class BuildConfig(
        val buildType: String,
        val taskSuffix: String,
        val createTask: String,
        val packageTasks: List<String>
    )

    val buildConfigs: List<BuildConfig> = listOf(
        BuildConfig("main", "", "createDistributable", listOf("packageDmg", "packageMsi", "packageDeb")),
        BuildConfig("main-release", "Release", "createReleaseDistributable", listOf("packageReleaseDmg", "packageReleaseMsi", "packageReleaseDeb"))
    )

    fun registerCleanupTasks(project: Project) {
        buildConfigs.forEach { config ->
            val cleanTask = project.tasks.register("cleanBundleNatives${config.taskSuffix}", CleanBundleNativesTask::class.java) {
                description = "Removes native binaries for non-targeted platforms from the ${config.buildType} bundle"
                group = "build"

                folderToKeep.set(nativeFolderToKeep)
                foldersToRemove.set(nativeFoldersToRemove)
                appDir.set(project.layout.buildDirectory.dir("compose/binaries/${config.buildType}/app"))
                tempDir.set(project.layout.buildDirectory.dir("tmp/native-cleanup-${config.buildType}"))
            }

            project.afterEvaluate {
                tasks.findByName(config.createTask)?.let {
                    cleanTask.configure { dependsOn(it) }
                }
                config.packageTasks.forEach { taskName ->
                    tasks.findByName(taskName)?.dependsOn(cleanTask)
                }
            }
        }
    }
}
