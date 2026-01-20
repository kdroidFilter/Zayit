package io.github.kdroidfilter.seforimapp.earthwidget

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe pool of reusable pixel buffers (IntArray) for rendering.
 *
 * Reduces GC pressure by reusing buffers instead of allocating new ones for each frame.
 * Buffers are grouped by size (pixel count) and managed with a LRU-style eviction.
 *
 * Usage:
 * ```
 * val buffer = bufferPool.acquire(width * height)
 * try {
 *     // use buffer for rendering
 * } finally {
 *     bufferPool.release(buffer)
 * }
 * ```
 *
 * Or with the inline helper:
 * ```
 * bufferPool.withBuffer(width * height) { buffer ->
 *     // use buffer for rendering
 * }
 * ```
 */
internal class PixelBufferPool(
    /** Maximum number of buffers to keep per size bucket. */
    private val maxBuffersPerSize: Int = 3,
    /** Maximum total number of buffers across all sizes. */
    private val maxTotalBuffers: Int = 12,
) {
    private val mutex = Mutex()
    private val pools = mutableMapOf<Int, ArrayDeque<IntArray>>()
    private var totalBufferCount = 0

    /**
     * Acquires a buffer of the specified size from the pool or creates a new one.
     * The buffer contents are cleared (filled with zeros) before returning.
     *
     * @param pixelCount Total number of pixels (width * height).
     * @return An IntArray of the requested size, cleared to zeros.
     */
    suspend fun acquire(pixelCount: Int): IntArray {
        mutex.withLock {
            val pool = pools[pixelCount]
            val buffer = pool?.removeLastOrNull()
            if (buffer != null) {
                totalBufferCount--
                // Clear buffer before returning (important for transparency)
                buffer.fill(0)
                return buffer
            }
        }
        // No pooled buffer available, create new one
        return IntArray(pixelCount)
    }

    /**
     * Releases a buffer back to the pool for reuse.
     * If the pool is full, the buffer is discarded.
     *
     * @param buffer The buffer to release.
     */
    suspend fun release(buffer: IntArray) {
        mutex.withLock {
            val size = buffer.size
            val pool = pools.getOrPut(size) { ArrayDeque() }

            // Check if we can add to this size's pool
            if (pool.size >= maxBuffersPerSize) {
                // Pool for this size is full, discard buffer
                return
            }

            // Check total pool limit
            if (totalBufferCount >= maxTotalBuffers) {
                // Evict oldest buffer from the largest pool
                evictOldestBuffer()
            }

            pool.addLast(buffer)
            totalBufferCount++
        }
    }

    /**
     * Evicts the oldest buffer from the pool with the most buffers.
     * Called when total buffer count exceeds the limit.
     * Must be called while holding the mutex.
     */
    private fun evictOldestBuffer() {
        val largestPool = pools.entries.maxByOrNull { it.value.size }?.value ?: return
        if (largestPool.isNotEmpty()) {
            largestPool.removeFirst()
            totalBufferCount--
        }
    }

    /**
     * Clears all pooled buffers.
     * Useful for memory pressure situations.
     */
    suspend fun clear() {
        mutex.withLock {
            pools.clear()
            totalBufferCount = 0
        }
    }

    /**
     * Returns current pool statistics for debugging/monitoring.
     */
    suspend fun stats(): PoolStats {
        mutex.withLock {
            return PoolStats(
                totalBuffers = totalBufferCount,
                sizeDistribution = pools.mapValues { it.value.size }.toMap(),
            )
        }
    }

    data class PoolStats(
        val totalBuffers: Int,
        val sizeDistribution: Map<Int, Int>,
    )
}

/**
 * Executes the given block with an acquired buffer, automatically releasing it afterward.
 *
 * @param pixelCount Size of buffer to acquire.
 * @param block Function to execute with the buffer.
 * @return Result of the block execution.
 */
internal suspend inline fun <T> PixelBufferPool.withBuffer(
    pixelCount: Int,
    block: (IntArray) -> T,
): T {
    val buffer = acquire(pixelCount)
    return try {
        block(buffer)
    } finally {
        release(buffer)
    }
}

/**
 * Global buffer pool instance shared across all renderers.
 *
 * Configured with reasonable defaults:
 * - Up to 3 buffers per size (handles main + Earth + Moon buffers)
 * - Up to 12 total buffers (handles multiple concurrent renders)
 */
internal val globalPixelBufferPool =
    PixelBufferPool(
        maxBuffersPerSize = 3,
        maxTotalBuffers = 12,
    )
