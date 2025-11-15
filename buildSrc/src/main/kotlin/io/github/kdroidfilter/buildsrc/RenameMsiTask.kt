package io.github.kdroidfilter.buildsrc

import org.gradle.api.DefaultTask
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

@DisableCachingByDefault(because = "Simple file rename helper task for MSI; no meaningful outputs to cache")
abstract class RenameMsiTask @Inject constructor() : DefaultTask() {
    @get:Inject
    abstract val layout: ProjectLayout

    @get:Input
    abstract val archSuffix: Property<String>

    @TaskAction
    fun run() {
        val binariesDir = layout.buildDirectory.dir("compose/binaries").get().asFile
        if (!binariesDir.exists()) {
            logger.lifecycle("[renameMsi] No compose/binaries directory found, skipping.")
            return
        }
        val msis = binariesDir.walkTopDown().filter { it.isFile && it.extension == "msi" }.toList()
        if (msis.isEmpty()) {
            logger.lifecycle("[renameMsi] No .msi files found, skipping.")
            return
        }
        val suffix = archSuffix.get()
        msis.forEach { msi ->
            val nameNoExt = msi.name.removeSuffix(".msi")
            if (nameNoExt.endsWith(suffix)) return@forEach
            val newName = nameNoExt + suffix + ".msi"
            val target = msi.parentFile.resolve(newName)
            msi.copyTo(target, overwrite = true)
            if (!msi.delete()) {
                logger.warn("[renameMsi] Could not delete original file: ${msi.absolutePath}")
            }
            logger.lifecycle("[renameMsi] Renamed ${msi.name} -> ${target.name}")
        }
    }
}

