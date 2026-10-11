package io.github.kdroidfilter.seforimapp.framework.session

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File

/**
 * The pictures of the tabs' windows (their hover cards) on disk, one PNG per tab id next to the
 * session, so a cold start shows the cards of the tabs it restores. Best effort: a picture that
 * cannot be written or read is simply absent.
 */
class TabThumbnailStore(
    private val dir: File,
) {
    init {
        dir.mkdirs()
    }

    private fun file(tabId: String) = File(dir, "$tabId.png")

    /** Writes [picture] for [tabId]; call off the UI thread. */
    fun save(
        tabId: String,
        picture: ImageBitmap,
    ) {
        runCatching {
            val data = Image.makeFromBitmap(picture.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG) ?: return
            val tmp = File(dir, "$tabId.png.tmp")
            tmp.writeBytes(data.bytes)
            tmp.renameTo(file(tabId))
        }
    }

    /** The picture saved for [tabId], or `null`; call off the UI thread. */
    fun load(tabId: String): ImageBitmap? =
        runCatching {
            file(tabId).takeIf { it.isFile }?.readBytes()?.let { Image.makeFromEncoded(it).toComposeImageBitmap() }
        }.getOrNull()

    /** A tab moved under a new id keeps its picture. */
    fun copy(
        fromTabId: String,
        toTabId: String,
    ) {
        runCatching { file(fromTabId).takeIf { it.isFile }?.copyTo(file(toTabId), overwrite = true) }
    }

    /** Deletes the pictures of every tab not in [keep]. */
    fun prune(keep: Set<String>) {
        runCatching {
            dir.listFiles()?.forEach { f ->
                if (f.name.removeSuffix(".tmp").removeSuffix(".png") !in keep) f.delete()
            }
        }
    }
}
