package fakes.library.database.datastructures

/**
 * Cache that wraps HashMap in order to cache up to [limit] elements.
 * If the amount of elements in the cache succeeds the given limit: it will flush
 */
class LimitedCacheMap<K, V>(var limit: Int) {

    private val map = mutableMapOf<K, V>()

    /**
     * @return true if [key] is mapped to some value, false otherwise
     */
    fun containsKey(key: K): Boolean {
        return map.containsKey(key)
    }

    /**
     * @return true if [value] has a key thats mapped to it, false otherwise
     */
    fun containsValue(value: V): Boolean {
        return map.containsValue(value)
    }

    /**
     * @return the value that corresponds to [key], or null if no such value exists
     */
    operator fun get(key: K): V? {
        return map[key]
    }

    /**
     * Map [key] to [value].
     * Cache is reset if its size exceeds the limit
     */
    operator fun set(key: K, value: V) {
        if (map.size >= limit)
            flush()
        map[key] = value
    }

    /**
     * @return true if there are no values in the cache, false otherwise
     */
    fun isEmpty(): Boolean {
        return map.isEmpty()
    }

    /**
     * resets the cache
     */
    fun flush() {
        map.clear()
    }
}