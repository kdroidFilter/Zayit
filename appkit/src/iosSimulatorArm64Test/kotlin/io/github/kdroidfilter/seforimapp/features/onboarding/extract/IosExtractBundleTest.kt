package io.github.kdroidfilter.seforimapp.features.onboarding.extract

import com.russhwolf.settings.MapSettings
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.HashingSource
import okio.Path.Companion.toPath
import okio.blackholeSink
import okio.buffer
import okio.use
import platform.Foundation.NSTemporaryDirectory
import platform.posix.getenv
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/**
 * A real database bundle extracted on the iOS simulator's file system, as the onboarding does: `-PseforimTestBundle=`
 * a `.tar.zst` or its `.part01`, with the extracted database's expected sha256 in `-PseforimTestBundleSha256=`.
 * Skipped without them.
 */
class IosExtractBundleTest {
    @OptIn(ExperimentalForeignApi::class)
    private fun env(name: String): String? = getenv(name)?.toKString()?.takeIf { it.isNotBlank() }

    @Test
    fun extractsARealBundleOnTheFileSystem() =
        runTest(timeout = 10.minutes) {
            val bundle = env("SEFORIM_TEST_BUNDLE") ?: return@runTest println("IosExtractBundleTest: no bundle, skipped")
            val expectedSha256 = env("SEFORIM_TEST_BUNDLE_SHA256")
            val fileSystem = FileSystem.SYSTEM
            val dbDir = (NSTemporaryDirectory() + "extract-test").toPath()
            fileSystem.deleteRecursively(dbDir, mustExist = false)

            val start = TimeSource.Monotonic.markNow()
            val dbPath = ExtractUseCase(AppSettings(MapSettings()), fileSystem) { dbDir }.extractToDatabase(bundle) {}
            val elapsed = start.elapsedNow()

            val size = fileSystem.metadata(dbPath.toPath()).size
            val sha256 =
                HashingSource.sha256(fileSystem.source(dbPath.toPath())).use { hashing ->
                    hashing.buffer().readAll(blackholeSink())
                    hashing.hash.hex()
                }
            println("IosExtractBundleTest: $dbPath, $size bytes, sha256 $sha256, extracted in $elapsed")
            expectedSha256?.let { assertEquals(it, sha256) }
            fileSystem.deleteRecursively(dbDir)
        }
}
