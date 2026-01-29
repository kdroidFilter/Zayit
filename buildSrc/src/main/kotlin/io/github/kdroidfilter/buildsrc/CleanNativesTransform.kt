package io.github.kdroidfilter.buildsrc

import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

abstract class CleanNativesTransform : TransformAction<CleanNativesTransform.Parameters> {

    interface Parameters : TransformParameters {
        @get:Input
        val foldersToRemove: SetProperty<String>
    }

    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val inputFile = inputArtifact.get().asFile
        val foldersToRemove = parameters.foldersToRemove.get()

        if (!inputFile.name.endsWith(".jar")) {
            outputs.file(inputFile)
            return
        }

        // Check if this JAR contains any folders we want to remove
        val hasTargetFolders = ZipFile(inputFile).use { zip ->
            zip.entries().asSequence().any { entry ->
                foldersToRemove.any { folder -> entry.name.startsWith("$folder/") }
            }
        }

        if (!hasTargetFolders) {
            outputs.file(inputFile)
            return
        }

        // Create cleaned JAR
        val outputFile = outputs.file("cleaned-${inputFile.name}")
        var removedCount = 0

        ZipFile(inputFile).use { zip ->
            ZipOutputStream(outputFile.outputStream()).use { zos ->
                zip.entries().asSequence().forEach { entry ->
                    val shouldRemove = foldersToRemove.any { folder ->
                        entry.name.startsWith("$folder/")
                    }

                    if (shouldRemove) {
                        removedCount++
                    } else {
                        zos.putNextEntry(ZipEntry(entry.name))
                        if (!entry.isDirectory) {
                            zip.getInputStream(entry).use { it.copyTo(zos) }
                        }
                        zos.closeEntry()
                    }
                }
            }
        }

        if (removedCount > 0) {
            println("  Cleaned ${inputFile.name}: removed $removedCount native entries")
        }
    }
}
