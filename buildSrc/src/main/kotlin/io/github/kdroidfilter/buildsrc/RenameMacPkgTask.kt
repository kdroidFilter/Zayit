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
        val pkgs = binariesDir.walkTopDown().filter { it.isFile && it.extension == "pkg" }.toList()
        if (pkgs.isEmpty()) {
            logger.lifecycle("[renameMacPkg] No .pkg files found, skipping.")
            return
        }
        val suffix = archSuffix.get()
        pkgs.forEach { pkg ->
            val nameNoExt = pkg.name.removeSuffix(".pkg")
            if (nameNoExt.endsWith(suffix)) return@forEach
            val newName = nameNoExt + suffix + ".pkg"
            val target = pkg.parentFile.resolve(newName)
            pkg.copyTo(target, overwrite = true)
            if (!pkg.delete()) {
                logger.warn("[renameMacPkg] Could not delete original file: ${pkg.absolutePath}")
            }
            logger.lifecycle("[renameMacPkg] Renamed ${pkg.name} -> ${target.name}")
        }
    }
}
