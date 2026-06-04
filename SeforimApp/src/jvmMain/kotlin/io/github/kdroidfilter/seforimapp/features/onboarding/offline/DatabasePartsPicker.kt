package io.github.kdroidfilter.seforimapp.features.onboarding.offline

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Prompts for the split database bundle (`.tar.zst.part01` / `.part02`).
 *
 * The user picks the `.part01` file; the matching `.part02` is auto-detected in the
 * same directory when present, otherwise a second picker is shown. Returns the
 * `.part01` path once both parts are available, or `null` if the user cancelled.
 *
 * FileKit's compose launcher dispatches the picker on Dispatchers.Main; under the
 * Tao backend that is the single GTK event-loop thread, and the picker's blocking
 * D-Bus (xdg-desktop-portal) work would freeze/deadlock it. Running the suspend API
 * on Dispatchers.IO keeps the event loop free; callers resume on their own scope.
 *
 * @param onPart01Picked invoked with the selected `.part01` path (or null) as soon as
 *   it is chosen, so callers can reflect the selection in the UI before part02 resolves.
 */
suspend fun pickDatabaseParts(onPart01Picked: (String?) -> Unit = {}): String? {
    val part01Path =
        withContext(Dispatchers.IO) {
            FileKit.openFilePicker(type = FileKitType.File(extensions = listOf("part01")))
        }?.path
    onPart01Picked(part01Path)
    if (part01Path.isNullOrBlank()) return null

    // Part02 lives next to part01 in the common case; only prompt when it is missing.
    val part01File = File(part01Path)
    val part02File = File(part01File.parent, part01File.name.replace(".part01", ".part02"))
    if (part02File.exists()) return part01Path

    val part02Path =
        withContext(Dispatchers.IO) {
            FileKit.openFilePicker(type = FileKitType.File(extensions = listOf("part02")))
        }?.path
    return if (!part02Path.isNullOrBlank()) part01Path else null
}
