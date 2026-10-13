package io.github.kdroidfilter.buildsrc

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Patches Compose's `SelectionManager.updatePinnedSelectables()` so collapsed selections are pinned too
 * (Zayit #48, JetBrains/compose-multiplatform-core#3480, refused upstream as it touches commonMain).
 *
 * A plain click leaves a collapsed selection whose lazy item is not pinned, so it is disposed once scrolled
 * away; a Shift+click then extends from an unregistered selectable id and crashes in `getSelectionLayout`.
 * The patch drops the `start.offset == end.offset` early return, exactly like the upstream PR.
 */
object SelectionPinPatch {
    const val SELECTION_MANAGER = "androidx/compose/foundation/text/selection/SelectionManager.class"
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
