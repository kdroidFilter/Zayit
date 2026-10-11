package io.github.kdroidfilter.seforimapp.features.onboarding.extract

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BundlePartsTest {
    private val dir = Files.createTempDirectory("bundle-parts").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun part(
        number: Int,
        size: Int,
        base: String = BundleParts.BUNDLE_NAME,
    ) = File(dir, "$base.part%02d".format(number)).apply { writeBytes(ByteArray(size)) }

    private fun manifest(vararg sizes: Long) {
        val parts =
            sizes.mapIndexed { i, size ->
                """{ "file": "${BundleParts.BUNDLE_NAME}.part%02d", "size": $size, "sha256": "x" }""".format(i + 1)
            }
        File(dir, BundleParts.manifestNameFor(BundleParts.BUNDLE_NAME)).writeText(
            """{ "formatVersion": 1, "file": "${BundleParts.BUNDLE_NAME}", "size": ${sizes.sum()}, "sha256": "x",
               "parts": [${parts.joinToString()}], "futureField": true }""",
        )
    }

    private fun resolveNames() = BundleParts.resolve(File(dir, "${BundleParts.BUNDLE_NAME}.part01")).map { it.name.substringAfterLast('.') }

    @Test
    fun `parses part numbers`() {
        assertEquals(1, BundleParts.partNumber("seforim_bundle.tar.zst.part01"))
        assertEquals(12, BundleParts.partNumber("seforim_bundle.tar.zst.PART12"))
        assertNull(BundleParts.partNumber("seforim_bundle.tar.zst"))
        assertNull(BundleParts.partNumber("seforim.db.part01"))
        assertNull(BundleParts.partNumber("seforim_bundle.tar.zst.part99999999999"))
    }

    @Test
    fun `legacy part names keep only the bundle, in order`() {
        val names =
            BundleParts.legacyPartNames(
                listOf("seforim_bundle.tar.zst.part02", "lucene.tar.zst.part01", "seforim_bundle.tar.zst.part01", "catalog.pb"),
            )
        assertEquals(listOf("seforim_bundle.tar.zst.part01", "seforim_bundle.tar.zst.part02"), names)
    }

    @Test
    fun `without manifest finds every part in order`() {
        part(3, 2)
        part(1, 4)
        part(2, 4)
        part(4, 4, base = "other.tar.zst")

        assertEquals(listOf("part01", "part02", "part03"), resolveNames())
    }

    @Test
    fun `without manifest rejects a gap, a lone part and a missing last part`() {
        part(1, 4)
        assertFailsWith<IllegalArgumentException> { resolveNames() }
        part(3, 2)
        assertFailsWith<IllegalArgumentException> { resolveNames() }
        File(dir, "${BundleParts.BUNDLE_NAME}.part03").delete()
        part(2, 4)
        assertFailsWith<IllegalArgumentException> { resolveNames() }
    }

    @Test
    fun `manifest decides the parts and ignores leftovers`() {
        part(1, 4)
        part(2, 1)
        part(3, 4) // leftover of an older, bigger bundle
        manifest(4, 1)

        assertEquals(listOf("part01", "part02"), resolveNames())
    }

    @Test
    fun `manifest catches a missing or truncated part`() {
        part(1, 4)
        part(2, 4)
        manifest(4, 4, 1)
        assertFailsWith<IllegalArgumentException> { resolveNames() }

        part(3, 0)
        assertFailsWith<IllegalArgumentException> { resolveNames() }
    }

    @Test
    fun `bundle files include the manifest`() {
        part(1, 4)
        part(2, 1)
        manifest(4, 1)
        File(dir, "catalog.pb").writeText("x")

        val names = BundleParts.bundleFiles(File(dir, "${BundleParts.BUNDLE_NAME}.part01")).map { it.name }.toSet()

        assertEquals(3, names.size)
        assertTrue(BundleParts.manifestNameFor(BundleParts.BUNDLE_NAME) in names)
    }
}
