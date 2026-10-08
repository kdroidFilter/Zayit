package io.github.kdroidfilter.seforimapp.backup

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Writes [bytes] through a temporary sibling, so a failed write never leaves half a file. The
 * rename is atomic where the file system allows it (not on some network or FUSE mounts).
 */
fun File.writeBytesAtomically(bytes: ByteArray) {
    val tmp = File(absoluteFile.parentFile, "$name.tmp")
    try {
        tmp.writeBytes(bytes)
        try {
            Files.move(tmp.toPath(), toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        tmp.delete()
    }
}
