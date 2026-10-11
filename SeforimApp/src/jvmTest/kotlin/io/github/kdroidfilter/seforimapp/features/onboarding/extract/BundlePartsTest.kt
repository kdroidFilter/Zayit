package io.github.kdroidfilter.seforimapp.features.onboarding.extract

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BundlePartsTest {
    private val dir = Files.createTempDirectory("bundle-parts").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun touch(name: String) = File(dir, name).apply { writeText("x") }

    @Test
    fun `parses part numbers`() {
        assertEquals(1, BundleParts.partNumber("seforim_bundle.tar.zst.part01"))
        assertEquals(12, BundleParts.partNumber("seforim_bundle.tar.zst.PART12"))
        assertNull(BundleParts.partNumber("seforim_bundle.tar.zst"))
        assertNull(BundleParts.partNumber("seforim.db.part01"))
    }

    @Test
    fun `finds every sibling part in order`() {
        listOf("03", "01", "02").forEach { touch("seforim_bundle.tar.zst.part$it") }
        touch("other.tar.zst.part04")
        touch("seforim_bundle.tar.zst")

        val parts = BundleParts.siblingsOf(File(dir, "seforim_bundle.tar.zst.part02"))

        assertEquals(
            listOf("part01", "part02", "part03"),
            parts.map { it.name.substringAfterLast('.') },
        )
    }

    @Test
    fun `fails on a gap`() {
        touch("seforim_bundle.tar.zst.part01")
        touch("seforim_bundle.tar.zst.part03")

        assertFailsWith<IllegalArgumentException> {
            BundleParts.siblingsOf(File(dir, "seforim_bundle.tar.zst.part01"))
        }
    }
}
