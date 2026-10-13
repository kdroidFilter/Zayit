package io.github.kdroidfilter.buildsrc

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Patches Compose's `MeasurePassDelegate.markNodeAndSubtreeAsPlaced()` so a subtree placed again notifies
 * `onPlaced` to every coordinator of its nodes, as `markSubtreeAsNotPlaced()` notifies `onUnplaced` to all.
 *
 * Upstream only calls the inner coordinator's `onPlaced`. A layout modifier node (each selectable Text's
 * `SelectableTextAnnotatedStringNode`) then learns it was unplaced but never that it is placed again, until its
 * next relayout: since Compose 1.12 its text unsubscribes from the SelectionContainer when unplaced, so a tab
 * shown again (TabsContent unplaces hidden tabs) selected nothing until a zoom or a resize.
 */
object PlacedSubtreePatch {
    const val MEASURE_PASS_DELEGATE = "androidx/compose/ui/node/MeasurePassDelegate.class"
    private const val OWNER = "androidx/compose/ui/node/MeasurePassDelegate"
    private const val COORDINATOR = "androidx/compose/ui/node/NodeCoordinator"
    private const val LAYOUT_NODE = "androidx/compose/ui/node/LayoutNode"
    private const val HELPER = "zayit\$onPlacedChain"
    private const val HELPER_DESCRIPTOR = "(L$COORDINATOR;)V"

    fun patchMeasurePassDelegate(bytes: ByteArray): ByteArray {
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
                    if (name != "markNodeAndSubtreeAsPlaced") return mv
                    return object : MethodVisitor(Opcodes.ASM9, mv) {
                        override fun visitMethodInsn(
                            opcode: Int,
                            owner: String,
                            name: String,
                            descriptor: String,
                            isInterface: Boolean,
                        ) {
                            if (opcode == Opcodes.INVOKEVIRTUAL && owner == COORDINATOR && name == "onPlaced" && descriptor == "()V") {
                                // innerCoordinator.onPlaced() -> every coordinator of the node, outer to inner
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, OWNER, HELPER, HELPER_DESCRIPTOR, false)
                                patches++
                            } else {
                                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                            }
                        }
                    }
                }

                override fun visitEnd() {
                    writeHelper(super.visitMethod(Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC, HELPER, HELPER_DESCRIPTOR, null, null))
                    super.visitEnd()
                }
            }
        ClassReader(bytes).accept(visitor, 0)
        check(patches == 1) {
            "PlacedSubtreePatch: expected 1 onPlaced call in MeasurePassDelegate.markNodeAndSubtreeAsPlaced, found " +
                "$patches. Compose changed: check whether a subtree placed again now notifies all its coordinators " +
                "(UnplacedSelectionUiTest) and drop this patch, or update it."
        }
        return writer.toByteArray()
    }

    /**
     * `static void zayit$onPlacedChain(NodeCoordinator inner)`: calls `onPlaced()` from the node's outer coordinator
     * down to [inner], as `forEachCoordinatorIncludingInner` walks them.
     */
    private fun writeHelper(mv: MethodVisitor) {
        val loop = Label()
        val end = Label()
        val locals = arrayOf<Any>(COORDINATOR, COORDINATOR)
        mv.visitCode()
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, COORDINATOR, "getLayoutNode", "()L$LAYOUT_NODE;", false)
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, LAYOUT_NODE, "getOuterCoordinator\$ui", "()L$COORDINATOR;", false)
        mv.visitVarInsn(Opcodes.ASTORE, 1)
        mv.visitLabel(loop)
        mv.visitFrame(Opcodes.F_FULL, 2, locals, 0, emptyArray())
        mv.visitVarInsn(Opcodes.ALOAD, 1)
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, COORDINATOR, "onPlaced", "()V", false)
        mv.visitVarInsn(Opcodes.ALOAD, 1)
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitJumpInsn(Opcodes.IF_ACMPEQ, end)
        mv.visitVarInsn(Opcodes.ALOAD, 1)
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, COORDINATOR, "getWrapped\$ui", "()L$COORDINATOR;", false)
        mv.visitVarInsn(Opcodes.ASTORE, 1)
        mv.visitVarInsn(Opcodes.ALOAD, 1)
        mv.visitJumpInsn(Opcodes.IFNONNULL, loop)
        mv.visitLabel(end)
        mv.visitFrame(Opcodes.F_FULL, 2, locals, 0, emptyArray())
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(2, 2)
        mv.visitEnd()
    }
}
