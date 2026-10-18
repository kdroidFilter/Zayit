package io.github.kdroidfilter.seforimapp.features.onboarding.extract

import com.russhwolf.settings.MapSettings
import com.squareup.zstd.okio.zstdCompress
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.ByteString.Companion.toByteString
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.fakefilesystem.FakeFileSystem
import okio.use
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExtractUseCaseTest {
    private val fileSystem = FakeFileSystem()
    private val dbDir = "/data/databases".toPath()
    private val downloads = "/downloads".toPath().also { fileSystem.createDirectories(it) }
    private val appSettings = AppSettings(MapSettings())
    private val useCase = ExtractUseCase(appSettings, fileSystem) { dbDir }

    // Pseudo-random, so the bundle stays big enough to be split
    private val database = Random(42).nextBytes(300_000)
    private val segment = ByteArray(5_000) { (it % 7).toByte() }

    private fun bundle(vararg extra: Pair<String, ByteArray>): ByteArray {
        val tar =
            TarWriter().apply {
                entry("seforim.db.lucene/", type = '5')
                entry("seforim.db.lucene/_0.cfs", segment)
                entry("seforim.db", database)
                extra.forEach { (name, content) -> entry(name, content) }
                end()
            }
        val compressed = Buffer()
        (compressed as okio.Sink).zstdCompress().buffer().use { it.writeAll(tar.buffer) }
        return compressed.readByteArray()
    }

    /** [bytes] split in parts of [partSize] in [dir], with their manifest when [withManifest]. */
    private fun writeParts(
        bytes: ByteArray,
        dir: Path,
        partSize: Int,
        withManifest: Boolean = true,
        sha256: String = bytes.toByteString().sha256().hex(),
    ): Path {
        val chunks = bytes.toList().chunked(partSize).map { it.toByteArray() }
        val names = chunks.indices.map { "${BundleParts.BUNDLE_NAME}.part${(it + 1).toString().padStart(2, '0')}" }
        chunks.zip(names).forEach { (chunk, name) -> fileSystem.write(dir / name) { write(chunk) } }
        if (withManifest) {
            val parts = chunks.zip(names).joinToString { (chunk, name) -> """{ "file": "$name", "size": ${chunk.size}, "sha256": "x" }""" }
            fileSystem.write(dir / BundleParts.manifestNameFor(BundleParts.BUNDLE_NAME)) {
                writeUtf8(
                    """{ "formatVersion": 1, "file": "${BundleParts.BUNDLE_NAME}", "size": ${bytes.size}, "sha256": "$sha256", "parts": [$parts] }""",
                )
            }
        }
        return dir / names.first()
    }

    @Test
    fun `a split bundle is extracted checked and its parts deleted`() =
        runTest {
            val bytes = bundle()
            val part01 = writeParts(bytes, dbDir.also { fileSystem.createDirectories(it) }, partSize = bytes.size / 3 + 1)
            val progress = mutableListOf<Float>()

            val dbPath = useCase.extractToDatabase(part01.toString()) { progress += it }

            assertEquals((dbDir / "seforim.db").toString(), dbPath)
            assertEquals(dbPath, appSettings.getDatabasePath())
            assertContentEquals(database, fileSystem.read(dbDir / "seforim.db") { readByteArray() })
            assertContentEquals(segment, fileSystem.read(dbDir / "seforim.db.lucene" / "_0.cfs") { readByteArray() })
            assertEquals(1f, progress.last())
            assertTrue(progress.zipWithNext().all { (a, b) -> a <= b })
            // Downloaded by the app into its databases directory: the parts and their manifest go
            assertFalse(fileSystem.list(dbDir).any { BundleParts.isPart(it.name) || BundleParts.isManifest(it.name) })
        }

    @Test
    fun `parts picked elsewhere are kept`() =
        runTest {
            val part01 = writeParts(bundle(), downloads, partSize = 120_000, withManifest = false)

            useCase.extractToDatabase(part01.toString()) {}

            assertTrue(fileSystem.exists(part01))
        }

    @Test
    fun `a bundle not matching its manifest's sha256 is refused`() =
        runTest {
            val part01 = writeParts(bundle(), downloads, partSize = 120_000, sha256 = "00".repeat(32))

            assertFailsWith<IllegalStateException> { useCase.extractToDatabase(part01.toString()) {} }
            assertFalse(fileSystem.exists(dbDir / "seforim.db"))
        }

    @Test
    fun `an entry climbing out of the databases directory is refused`() =
        runTest {
            val archive = downloads / "evil.tar.zst"
            fileSystem.write(archive) { write(bundle("../outside.txt" to "x".encodeToByteArray())) }

            assertFailsWith<IllegalArgumentException> { useCase.extractToDatabase(archive.toString()) {} }
            assertFalse(fileSystem.exists("/data/outside.txt".toPath()))
        }

    @Test
    fun `a compressed database alone is decompressed`() =
        runTest {
            val compressed = Buffer()
            (compressed as okio.Sink).zstdCompress().buffer().use { it.write(database) }
            val source = downloads / "seforim.db.zst"
            fileSystem.write(source) { writeAll(compressed) }

            val dbPath = useCase.extractToDatabase(source.toString()) {}

            assertContentEquals(database, fileSystem.read(dbPath.toPath()) { readByteArray() })
        }
}
