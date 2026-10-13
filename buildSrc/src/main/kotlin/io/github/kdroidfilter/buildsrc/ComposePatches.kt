package io.github.kdroidfilter.buildsrc

import org.gradle.api.Project
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.work.DisableCachingByDefault
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Bytecode patches of Compose's jars on the runtime classpaths, for bugs fixed upstream too late or not at all:
 * [SelectionPinPatch] (foundation) and [PlacedSubtreePatch] (ui).
 */
object ComposePatches {
    private val patched = Attribute.of("zayit.composePatched", Boolean::class.javaObjectType)

    /** The patched class files by jar name prefix. */
    private val patches: Map<String, Map<String, (ByteArray) -> ByteArray>> =
        mapOf(
            "foundation-desktop-" to mapOf(SelectionPinPatch.SELECTION_MANAGER to SelectionPinPatch::patchSelectionManager),
            "ui-desktop-" to mapOf(PlacedSubtreePatch.MEASURE_PASS_DELEGATE to PlacedSubtreePatch::patchMeasurePassDelegate),
        )

    fun apply(project: Project) {
        project.dependencies.run {
            attributesSchema { attribute(patched) }
            artifactTypes.getByName("jar") { attributes.attribute(patched, false) }
            registerTransform(Transform::class.java) {
                from.attribute(patched, false).attribute(ARTIFACT_TYPE_ATTRIBUTE, "jar")
                to.attribute(patched, true).attribute(ARTIFACT_TYPE_ATTRIBUTE, "jar")
            }
        }
        project.configurations
            .matching { it.isCanBeResolved && it.name.endsWith("RuntimeClasspath", ignoreCase = true) }
            .configureEach { attributes.attribute(patched, true) }
    }

    @DisableCachingByDefault(because = "Cheap jar rewrite")
    abstract class Transform : TransformAction<TransformParameters.None> {
        @get:InputArtifact
        abstract val inputArtifact: Provider<FileSystemLocation>

        override fun transform(outputs: TransformOutputs) {
            val input = inputArtifact.get().asFile
            val classPatches = patches.entries.firstOrNull { input.name.startsWith(it.key) }?.value
            if (classPatches == null) {
                outputs.file(input)
                return
            }
            val output = outputs.file(input.nameWithoutExtension + "-zayit-patched.jar")
            ZipFile(input).use { zip ->
                ZipOutputStream(output.outputStream()).use { out ->
                    for (entry in zip.entries()) {
                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        out.putNextEntry(ZipEntry(entry.name))
                        out.write(classPatches[entry.name]?.invoke(bytes) ?: bytes)
                        out.closeEntry()
                    }
                }
            }
        }
    }
}
