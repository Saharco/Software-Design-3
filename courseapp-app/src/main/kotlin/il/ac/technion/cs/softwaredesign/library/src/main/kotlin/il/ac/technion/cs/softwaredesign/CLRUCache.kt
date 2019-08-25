package il.ac.technion.cs.softwaredesign


/**
 * Least recently used ICache.
 */
class CLRUCache(private val ICache:ICache, private val maximalSize: Int = DEFAULT_SIZE) : ICache {

    companion object {
        private const val DEFAULT_SIZE = 100
        private const val PRESENT = true
    }

    private val keyMap = object : LinkedHashMap<String, Boolean>(maximalSize, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>): Boolean {
            val tooManyCachedItems = size > maximalSize
            if (tooManyCachedItems)
                eldestKeyToRemove = eldest.key
            return tooManyCachedItems
        }
    }

    private var eldestKeyToRemove: String? = null

    override val size: Int
        get() = ICache.size

    override fun set(key: String, value: String) {
        ICache[key] = value
        cycleKeyMap(key)
    }

    override fun remove(key: String) = ICache.remove(key)

    override fun get(key: String): String? {
        keyMap[key]
        return ICache[key]
    }

    override fun clear() {
        keyMap.clear()
        ICache.clear()
    }

    //needed for saving least recently used objects.
    private fun cycleKeyMap(key: String) {
        keyMap[key] = PRESENT
        eldestKeyToRemove?.let { ICache.remove(it) }
        eldestKeyToRemove = null
    }

}
