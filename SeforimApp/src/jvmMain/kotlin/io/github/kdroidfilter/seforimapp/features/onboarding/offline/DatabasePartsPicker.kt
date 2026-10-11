package io.github.kdroidfilter.seforimapp.features.onboarding.offline

import io.github.kdroidfilter.seforimapp.features.onboarding.extract.BundleParts
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Prompts for the split database bundle (`.tar.zst.part01` … `.partNN`).
 *
 * The user picks the `.part01` file; the other parts are discovered in the same directory
 * at extraction time (see [BundleParts]), whatever their number. Returns the `.part01`
 * path, or `null` if the user cancelled.
 *
 * FileKit's compose launcher dispatches the picker on Dispatchers.Main; under the
 * Tao backend that is the single GTK event-loop thread, and the picker's blocking
 * D-Bus (xdg-desktop-portal) work would freeze/deadlock it. Running the suspend API
 * on Dispatchers.IO keeps the event loop free; callers resume on their own scope.
 *
 * @param onPart01Picked invoked with the selected `.part01` path (or null) as soon as it is chosen.
 */
suspend fun pickDatabaseParts(onPart01Picked: (String?) -> Unit = {}): String? {
    val part01Path =
        withContext(Dispatchers.IO) {
            FileKit.openFilePicker(type = FileKitType.File(extensions = listOf("part01")))
        }?.path
    onPart01Picked(part01Path)
    return part01Path?.takeIf { it.isNotBlank() }
}
