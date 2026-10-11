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
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Patches Compose's `SelectionManager.updatePinnedSelectables()` so collapsed selections are pinned too
 * (Zayit #48, JetBrains/compose-multiplatform-core#3480, refused upstream as it touches commonMain).
 *
 * A plain click leaves a collapsed selection whose lazy item is not pinned, so it is disposed once scrolled
 * away; a Shift+click then extends from an unregistered selectable id and crashes in `getSelectionLayout`.
 * The patch drops the `start.offset == end.offset` early return, exactly like the upstream PR.
 */
object SelectionPinPatch {
    private val patched = Attribute.of("zayit.selectionPinPatched", Boolean::class.javaObjectType)

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
            if (!input.name.startsWith("foundation-desktop-")) {
                outputs.file(input)
                return
            }
            val output = outputs.file(input.nameWithoutExtension + "-selection-pin.jar")
            ZipFile(input).use { zip ->
                ZipOutputStream(output.outputStream()).use { out ->
                    for (entry in zip.entries()) {
                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        out.putNextEntry(ZipEntry(entry.name))
                        out.write(if (entry.name == SELECTION_MANAGER) patchSelectionManager(bytes) else bytes)
                        out.closeEntry()
                    }
                }
            }
        }
    }

    private const val SELECTION_MANAGER = "androidx/compose/foundation/text/selection/SelectionManager.class"
    private const val ANCHOR_INFO = "androidx/compose/foundation/text/selection/Selection\$AnchorInfo"

    fun patchSelectionManager(bytes: ByteArray): ByteArray {
        var patches = 0
        val writer = ClassWriter(0)
        val visitor =
            object : ClassVisitor(Opcodes.ASM9, writer) {
                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor {
                    val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                    if (name != "updatePinnedSelectables") return mv
                    return object : MethodVisitor(Opcodes.ASM9, mv) {
                        private var afterOffsetRead = false

                        override fun visitMethodInsn(
                            opcode: Int,
                            owner: String,
                            name: String,
                            descriptor: String,
                            isInterface: Boolean,
                        ) {
                            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                            afterOffsetRead = owner == ANCHOR_INFO && name == "getOffset"
                        }

                        override fun visitJumpInsn(
                            opcode: Int,
                            label: Label,
                        ) {
                            if (afterOffsetRead && opcode == Opcodes.IF_ICMPEQ) {
                                // `if (start.offset == end.offset) skip` -> drop both offsets, never skip
                                super.visitInsn(Opcodes.POP2)
                                patches++
                            } else {
                                super.visitJumpInsn(opcode, label)
                            }
                            afterOffsetRead = false
                        }

                        override fun visitInsn(opcode: Int) {
                            super.visitInsn(opcode)
                            afterOffsetRead = false
                        }

                        override fun visitVarInsn(
                            opcode: Int,
                            varIndex: Int,
                        ) {
                            super.visitVarInsn(opcode, varIndex)
                            afterOffsetRead = false
                        }
                    }
                }
            }
        ClassReader(bytes).accept(visitor, 0)
        check(patches == 1) {
            "SelectionPinPatch: expected 1 collapsed-selection check in SelectionManager.updatePinnedSelectables, " +
                "found $patches. Compose changed: check whether JetBrains/compose-multiplatform-core#3480 " +
                "is fixed upstream and drop this patch, or update it."
        }
        return writer.toByteArray()
    }
}
