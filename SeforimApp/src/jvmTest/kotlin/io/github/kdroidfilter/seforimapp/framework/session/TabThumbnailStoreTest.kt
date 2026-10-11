package io.github.kdroidfilter.seforimapp.framework.session

import androidx.compose.ui.graphics.ImageBitmap
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TabThumbnailStoreTest {
    private val store = TabThumbnailStore(createTempDirectory().toFile())

    @Test
    fun `a saved picture survives, follows a moved tab and is pruned with its tab`() {
        store.save("a", ImageBitmap(40, 30))
        assertEquals(40 to 30, store.load("a")?.let { it.width to it.height })
        assertNull(store.load("unknown"))

        store.copy("a", "b")
        assertEquals(40, store.load("b")?.width)

        store.prune(keep = setOf("b"))
        assertNull(store.load("a"))
        assertEquals(40, store.load("b")?.width)
    }
}
