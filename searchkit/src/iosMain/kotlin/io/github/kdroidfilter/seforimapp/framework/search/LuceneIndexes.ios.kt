@file:OptIn(ExperimentalForeignApi::class)

package io.github.kdroidfilter.seforimapp.framework.search

import io.github.kdroidfilter.seforimlibrary.search.index.LuceneIndex
import io.github.kdroidfilter.seforimlibrary.search.index.bridge.EmbeddedJvm
import io.github.kdroidfilter.seforimlibrary.search.index.bridge.JvmLuceneIndex
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager

/**
 * The Java runtime the iOS app embeds for Lucene (OpenJDK's mobile port, statically linked: see SeforimLibrary's
 * IOS_RUNTIME.md). It starts with the first index opened; set these before.
 */
object IosJavaRuntime {
    /** The jars of the search (SeforimLibrary's `iosJvmClasspath`): by default those of `<app>/lib/app`. */
    var classPath: List<String> = bundledJars()

    /**
     * The runtime's options: its heap above all (Lucene's caches live there). 128 MB keeps the app's footprint around
     * 160 MB on real data; a larger heap only grows it (the GC runs less), 32 MB fails.
     */
    var options: List<String> = listOf("-Xmx128m")

    val jvm: EmbeddedJvm by lazy { EmbeddedJvm.start(classPath, options) }

    private fun bundledJars(): List<String> {
        val dir = NSBundle.mainBundle.bundlePath + "/lib/app"
        return NSFileManager.defaultManager
            .contentsOfDirectoryAtPath(dir, null)
            .orEmpty()
            .map { it.toString() }
            .filter { it.endsWith(".jar") }
            .sorted()
            .map { "$dir/$it" }
    }
}

actual fun openLuceneIndex(
    indexDir: String,
    cacheQueries: Boolean,
): LuceneIndex = JvmLuceneIndex(IosJavaRuntime.jvm, indexDir, cacheQueries)
