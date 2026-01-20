package io.github.kdroidfilter.seforimapp.earthwidget

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe cache for pre-rendered starfield backgrounds.
 *
 * Since starfields are deterministic (same size + seed = same result),
 * caching them avoids redundant computation on every frame.
 *
 * The cache stores starfield pixel data indexed by size (width * height).
 * When the cache exceeds [maxEntries], oldest entries are evicted.
 *
 * Usage:
 * ```
 * val starfield = starfieldCache.getOrCreate(width, height)
 * // Copy starfield to output buffer
 * starfield.copyInto(outputBuffer)
 * ```
 *
 * @param maxEntries Maximum number of cached starfields (different sizes).
 */
internal class StarfieldCache(
    private val maxEntries: Int = 4,
) {
    private val mutex = Mutex()
    private val cache = LinkedHashMap<Int, IntArray>(maxEntries, 0.75f, true)

    /**
     * Gets a cached starfield or creates a new one for the given dimensions.
     *
     * The starfield is rendered with the standard seed (STARFIELD_SEED).
     * Returned array should NOT be modified - it's shared across frames.
     *
     * @param width Starfield width in pixels.
     * @param height Starfield height in pixels.
     * @return Cached starfield pixel data (ARGB format).
     */
    suspend fun getOrCreate(
        width: Int,
        height: Int,
    ): IntArray {
        val key = cacheKey(width, height)

        mutex.withLock {
            // Check cache first
            cache[key]?.let { return it }

            // Create new starfield
            val starfield =
                IntArray(width * height).apply {
                    fill(OPAQUE_BLACK)
                }
            renderStarfieldInternal(starfield, width, height, STARFIELD_SEED)

            // Evict oldest if cache is full
            if (cache.size >= maxEntries) {
                val oldestKey = cache.keys.first()
                cache.remove(oldestKey)
            }

            // Store in cache
            cache[key] = starfield
            return starfield
        }
    }

    /**
     * Clears all cached starfields.
     * Call this on memory pressure or when cache is no longer needed.
     */
    suspend fun clear() {
        mutex.withLock {
            cache.clear()
        }
    }

    /**
     * Returns current cache statistics for debugging.
     */
    suspend fun stats(): CacheStats {
        mutex.withLock {
            val totalPixels = cache.values.sumOf { it.size }
            return CacheStats(
                entryCount = cache.size,
                totalBytes = totalPixels * 4L, // 4 bytes per pixel (Int)
                sizes = cache.keys.toList(),
            )
        }
    }

    private fun cacheKey(
        width: Int,
        height: Int,
    ): Int {
        // Combine width and height into single key
        // Assumes width/height < 65536
        return (width shl 16) or height
    }

    data class CacheStats(
        val entryCount: Int,
        val totalBytes: Long,
        val sizes: List<Int>,
    )
}

/**
 * Internal starfield rendering (deterministic, used for caching).
 *
 * Uses xorshift32 PRNG for fast, reproducible star placement.
 */
private fun renderStarfieldInternal(
    dst: IntArray,
    width: Int,
    height: Int,
    seed: Int,
) {
    val pixelCount = width * height
    val starCount = (pixelCount / PIXELS_PER_STAR).coerceIn(MIN_STAR_COUNT, MAX_STAR_COUNT)
    var state = seed xor (width shl 16) xor height

    repeat(starCount) {
        // Random position
        state = xorshift32Internal(state)
        val x = (state ushr 1) % width
        state = xorshift32Internal(state)
        val y = (state ushr 1) % height

        // Random brightness with cubic falloff (more dim stars)
        state = xorshift32Internal(state)
        val t = ((state ushr 24) and 0xFF) / 255f
        val intensity = (32f + 223f * (t * t * t)).toInt().coerceIn(0, 255)

        // Optional slight color tint (blue/yellow)
        state = xorshift32Internal(state)
        val tint = (state ushr 30) and 0x3
        val r: Int
        val g: Int
        val b: Int
        when (tint) {
            0 -> {
                r = intensity
                g = intensity
                b = (intensity * 1.1f).toInt().coerceAtMost(255)
            } // Slight blue
            1 -> {
                r = (intensity * 1.05f).toInt().coerceAtMost(255)
                g = intensity
                b = (intensity * 0.95f).toInt()
            } // Slight yellow
            else -> {
                r = intensity
                g = intensity
                b = intensity
            } // White
        }

        dst[y * width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}

/**
 * xorshift32 PRNG (local copy to avoid circular dependencies).
 */
private fun xorshift32Internal(value: Int): Int {
    var x = value
    x = x xor (x shl 13)
    x = x xor (x ushr 17)
    x = x xor (x shl 5)
    return x
}

/**
 * Global starfield cache instance.
 *
 * Caches up to 4 different starfield sizes (typical use cases:
 * main scene, moon inset, different window sizes).
 */
internal val globalStarfieldCache = StarfieldCache(maxEntries = 4)
