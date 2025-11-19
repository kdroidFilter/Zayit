package io.github.kdroidfilter.buildsrc

import org.gradle.api.DefaultTask
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

@DisableCachingByDefault(because = "Simple file rename helper task; no meaningful outputs to cache")
abstract class RenameMacPkgTask @Inject constructor() : DefaultTask() {
    @get:Inject
    abstract val layout: ProjectLayout

    @get:Input
    abstract val archSuffix: Property<String>

    @TaskAction
    fun run() {
        val binariesDir = layout.buildDirectory.dir("compose/binaries").get().asFile
        if (!binariesDir.exists()) {
            logger.lifecycle("[renameMacPkg] No compose/binaries directory found, skipping.")
            return
        }
        val suffix = archSuffix.get()

        fun renameWithExtension(ext: String) {
            val files = binariesDir.walkTopDown().filter { it.isFile && it.extension == ext }.toList()
            if (files.isEmpty()) {
                logger.lifecycle("[renameMacPkg] No .$ext files found, skipping.")
                return
            }
            files.forEach { file ->
                val nameNoExt = file.name.removeSuffix(".$ext")
                if (nameNoExt.endsWith(suffix)) return@forEach
                val newName = nameNoExt + suffix + ".$ext"
                val target = file.parentFile.resolve(newName)
                file.copyTo(target, overwrite = true)
                if (!file.delete()) {
                    logger.warn("[renameMacPkg] Could not delete original file: ${file.absolutePath}")
                }
                logger.lifecycle("[renameMacPkg] Renamed ${file.name} -> ${target.name}")
            }
        }

        // Handle both PKG and DMG artifacts on macOS
        renameWithExtension("pkg")
        renameWithExtension("dmg")
    }
}
