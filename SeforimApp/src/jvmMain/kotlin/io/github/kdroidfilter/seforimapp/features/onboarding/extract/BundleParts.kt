package io.github.kdroidfilter.seforimapp.features.onboarding.extract

import java.io.File

/**
 * Naming of the split database bundle: `<base>.tar.zst.partNN`, numbered from 01, described by
 * `<base>.tar.zst.manifest.json`. The number of parts is not fixed (GitHub caps each asset at
 * 2 GiB, so it grows with the DB).
 */
object BundleParts {
    /** Name of the bundle published in SeforimLibrary releases. */
    const val BUNDLE_NAME = "seforim_bundle.tar.zst"

    private const val MANIFEST_SUFFIX = ".manifest.json"
    private val PART_NAME = Regex("""^(.+\.tar\.zst)\.part(\d+)$""", RegexOption.IGNORE_CASE)

    /** Part number of [name] (1 for `.part01`), or null if it is not a bundle part. */
    fun partNumber(name: String): Int? =
        PART_NAME
            .matchEntire(name)
            ?.groupValues
            ?.get(2)
            ?.toIntOrNull()

    fun isPart(name: String): Boolean = partNumber(name) != null

    /** Bundle a part belongs to (`seforim_bundle.tar.zst` for its `.part01`), or null. */
    private fun bundleNameOf(partName: String): String? = PART_NAME.matchEntire(partName)?.groupValues?.get(1)

    fun isManifest(name: String): Boolean = name.endsWith(".tar.zst$MANIFEST_SUFFIX", ignoreCase = true)

    fun manifestNameFor(bundleName: String): String = bundleName + MANIFEST_SUFFIX

    /**
     * Parts of [bundleName] among [names] (release assets without a manifest), in order.
     * Fails on a gap; a missing last part can only be detected with a manifest.
     */
    fun legacyPartNames(
        names: Collection<String>,
        bundleName: String = BUNDLE_NAME,
    ): List<String> {
        val byNumber =
            names
                .mapNotNull { name ->
                    PART_NAME
                        .matchEntire(name)
                        ?.takeIf { it.groupValues[1].equals(bundleName, ignoreCase = true) }
                        ?.let { match -> match.groupValues[2].toIntOrNull()?.let { it to name } }
                }.toMap()
        if (byNumber.isEmpty()) return emptyList()
        return requireContiguous(byNumber.keys).map { byNumber.getValue(it) }
    }

    /** Parts of a bundle in concatenation order, with the bundle's sha256 when its manifest is known. */
    class Resolved(
        val parts: List<File>,
        val sha256: String?,
    )

    /**
     * Ordered, complete list of the parts of the bundle [anyPart] belongs to, found in its directory.
     *
     * The manifest is optional (an offline install may only have the parts). When it sits next to
     * the parts and matches them, it decides the list and leftovers are ignored. Otherwise (absent,
     * unknown format, or left over from another release) parts are discovered by name and checked
     * with [requireComplete].
     */
    fun resolve(anyPart: File): Resolved {
        val bundleName = bundleNameOf(anyPart.name) ?: error("Not a bundle part: ${anyPart.name}")
        val dir = anyPart.absoluteFile.parentFile ?: error("Invalid part path: $anyPart")
        val manifest =
            File(dir, manifestNameFor(bundleName))
                .takeIf { it.isFile }
                ?.let { BundleManifest.parseOrNull(it.readText()) }
        val manifestParts = manifest?.parts?.map { File(dir, it.file) }
        if (manifestParts != null && manifestParts.zip(manifest.parts).all { (file, part) -> file.length() == part.size }) {
            return Resolved(manifestParts, manifest.sha256)
        }
        val parts = legacyPartNames(dir.list().orEmpty().toList(), bundleName).map { File(dir, it) }
        requireComplete(parts.map { it.length() })
        return Resolved(parts, sha256 = null)
    }

    /**
     * Without a manifest, completeness follows from how the bundle is split: every part but the
     * last has the same size and the last one is strictly smaller, so a missing last part shows
     * up as a full-size tail. A bundle is only split when it needs several parts.
     */
    fun requireComplete(sizes: List<Long>) {
        require(sizes.size >= 2) { "Only one bundle part found" }
        val full = sizes.first()
        require(sizes.dropLast(1).all { it == full }) { "Bundle parts have inconsistent sizes" }
        require(sizes.last() < full) { "The last bundle part is missing" }
    }

    /** Files belonging to the bundle [anyPart] is part of (parts and manifest), for cleanup. */
    fun bundleFiles(anyPart: File): List<File> {
        val bundleName = bundleNameOf(anyPart.name) ?: return emptyList()
        val dir = anyPart.absoluteFile.parentFile ?: return emptyList()
        return dir
            .listFiles()
            .orEmpty()
            .filter { file ->
                file.name.equals(manifestNameFor(bundleName), ignoreCase = true) ||
                    bundleNameOf(file.name).equals(bundleName, ignoreCase = true)
            }
    }

    /** Sorted part numbers, failing if they do not run 1..N without a gap. */
    fun requireContiguous(numbers: Collection<Int>): List<Int> {
        require(numbers.isNotEmpty()) { "No bundle part found" }
        val sorted = numbers.sorted()
        val missing = (1..sorted.last()).firstOrNull { it !in numbers }
        require(missing == null) { "Missing bundle part %02d".format(missing) }
        return sorted
    }
}
