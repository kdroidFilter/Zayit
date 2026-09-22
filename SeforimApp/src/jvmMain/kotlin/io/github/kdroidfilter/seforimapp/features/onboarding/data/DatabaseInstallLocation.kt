package io.github.kdroidfilter.seforimapp.features.onboarding.data

import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import java.io.File
import java.nio.file.Files

/** The normal install location remains unchanged; a user-selected folder contains a dedicated Zayit directory. */
object DatabaseInstallLocation {
    fun defaultDirectory(): File = File(FileKit.databasesDir.path).absoluteFile

    fun customDirectory(selectedFolder: File): File = File(selectedFolder.absoluteFile, "Zayit")

    /** Updates retain the current location even when the database itself is missing. */
    fun currentDirectoryOrDefault(): File =
        AppSettings.getDatabasePath()?.let { File(it).absoluteFile.parentFile } ?: defaultDirectory()

    /** Reject missing update media and unwritable targets before deleting an old database. */
    fun prepareDirectory(directory: File) {
        val target = directory.absoluteFile
        val current = AppSettings.getDatabasePath()?.let { File(it).absoluteFile.parentFile }
        require(current != target || target.isDirectory) { "Database location is unavailable: $target" }
        // FileKit's default app directory may not exist on a first install. A user-selected
        // parent directory must already exist so a detached drive cannot be recreated elsewhere.
        require(target == defaultDirectory() || target.parentFile?.isDirectory == true) {
            "Database location is unavailable: $target"
        }
        require(!Files.isSymbolicLink(target.toPath())) { "Database location is unavailable: $target" }
        Files.createDirectories(target.toPath())
        val probe = Files.createTempFile(target.toPath(), ".zayit-write-", ".tmp")
        Files.delete(probe)
    }
}
