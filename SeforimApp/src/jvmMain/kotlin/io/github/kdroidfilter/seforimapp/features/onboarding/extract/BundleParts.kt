package io.github.kdroidfilter.seforimapp.features.onboarding.extract

import java.io.File

/**
 * Naming of the split database bundle: `<base>.tar.zst.partNN`, numbered from 01.
 * The number of parts is not fixed (GitHub caps each asset at 2 GiB, so it grows with the DB).
 */
object BundleParts {
    private val PART_NAME = Regex("""^(.+\.tar\.zst)\.part(\d+)$""", RegexOption.IGNORE_CASE)

    /** Part number of [name] (1 for `.part01`), or null if it is not a bundle part. */
    fun partNumber(name: String): Int? =
        PART_NAME
            .matchEntire(name)
            ?.groupValues
            ?.get(2)
            ?.toIntOrNull()

    fun isPart(name: String): Boolean = partNumber(name) != null

    /** All parts of the bundle [anyPart] belongs to, found in its directory, in order. */
    fun siblingsOf(anyPart: File): List<File> {
        val base = PART_NAME.matchEntire(anyPart.name)?.groupValues?.get(1) ?: return emptyList()
        val byNumber =
            anyPart.absoluteFile.parentFile
                ?.listFiles()
                .orEmpty()
                .mapNotNull { file ->
                    PART_NAME
                        .matchEntire(file.name)
                        ?.takeIf { it.groupValues[1] == base }
                        ?.let { it.groupValues[2].toInt() to file }
                }.toMap()
        return requireContiguous(byNumber.keys).map { byNumber.getValue(it) }
    }

    /** Sorted part numbers, failing if they do not run 1..N without a gap. */
    fun requireContiguous(numbers: Collection<Int>): List<Int> {
        val sorted = numbers.sorted()
        val missing = (1..(sorted.lastOrNull() ?: 0)).firstOrNull { it !in numbers }
        require(sorted.isNotEmpty()) { "No bundle part found" }
        require(missing == null) { "Missing bundle part %02d".format(missing) }
        return sorted
    }
}
