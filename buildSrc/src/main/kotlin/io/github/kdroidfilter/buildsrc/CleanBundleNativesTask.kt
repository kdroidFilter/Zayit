package io.github.kdroidfilter.buildsrc

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@DisableCachingByDefault(because = "Modifies JARs in-place; no meaningful outputs to cache")
abstract class CleanBundleNativesTask : DefaultTask() {
    @get:Input
    abstract val folderToKeep: Property<String>

    @get:Input
    abstract val foldersToRemove: ListProperty<String>

    @get:InputDirectory
    @get:Optional
    abstract val appDir: DirectoryProperty

    @get:Internal
    abstract val tempDir: DirectoryProperty

    @TaskAction
    fun execute() {
        val appDirFile = appDir.get().asFile
        if (!appDirFile.exists()) {
            logger.warn("App directory does not exist: $appDirFile")
            return
        }

        val tempDirFile = tempDir.get().asFile
        val folders = foldersToRemove.get()

        logger.lifecycle("Native folder kept: ${folderToKeep.get()}")
        logger.lifecycle("Native folders removed: $folders")

        appDirFile.walkTopDown()
            .filter { it.isFile && it.extension == "jar" }
            .forEach { jarFile ->
                cleanJar(jarFile, tempDirFile, folders)
            }
    }

    private fun cleanJar(jarFile: File, tempDirFile: File, folders: List<String>) {
        var hasNativeFolders = false
        ZipFile(jarFile).use { zip ->
            hasNativeFolders = zip.entries().asSequence().any { entry: ZipEntry ->
                folders.any { folder -> entry.name.startsWith("$folder/") }
            }
        }

        if (!hasNativeFolders) return

        logger.lifecycle("Cleaning: ${jarFile.name}")

        tempDirFile.deleteRecursively()
        tempDirFile.mkdirs()

        var removedCount = 0

        ZipFile(jarFile).use { zip ->
            zip.entries().asSequence().forEach { entry: ZipEntry ->
                val shouldRemove = folders.any { folder -> entry.name.startsWith("$folder/") }
                if (shouldRemove) {
                    removedCount++
                    logger.info("  - Removed: ${entry.name}")
                } else {
                    val destFile = File(tempDirFile, entry.name)
                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            destFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
        }

        if (removedCount > 0) {
            val originalSize = jarFile.length()
            jarFile.delete()
            ZipOutputStream(jarFile.outputStream()).use { zos ->
                tempDirFile.walkTopDown().filter { it.isFile }.forEach { file ->
                    val entryName = file.relativeTo(tempDirFile).path.replace("\\", "/")
                    zos.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
            val newSize = jarFile.length()
            logger.lifecycle("  → $removedCount files removed, ${(originalSize - newSize) / 1024} KB saved")
        }

        tempDirFile.deleteRecursively()
    }
}
