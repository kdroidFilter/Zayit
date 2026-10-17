package io.github.kdroidfilter.seforimapp.core

/**
 * A map bounded to [maxSize] entries that evicts the least recently used one, as the JVM's access-order
 * `LinkedHashMap` (which common code lacks). Not thread-safe: callers synchronize.
 */
class LruCache<K, V>(
    private val maxSize: Int,
    private val onEvict: (V) -> Unit = {},
) {
    // Insertion order, kept as access order by moving each hit to the end
    private val entries = LinkedHashMap<K, V>()

    operator fun get(key: K): V? {
        val value = entries.remove(key) ?: return null
        entries[key] = value
        return value
    }

    operator fun set(
        key: K,
        value: V,
    ) {
        entries.remove(key)
        entries[key] = value
        while (entries.size > maxSize) {
            val eldest = entries.keys.first()
            entries.remove(eldest)?.let(onEvict)
        }
    }

    inline fun getOrPut(
        key: K,
        create: () -> V,
    ): V = get(key) ?: create().also { set(key, it) }
}
