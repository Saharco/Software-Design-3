package il.ac.technion.cs.softwaredesign

/**
 * Backend cache storage (no limitations, just for caching)
 */
class CPerpetualICache : ICache {
    private val cache = HashMap<String, String>()

    override val size: Int
        get() = cache.size

    override fun set(key: String, value: String) {
            this.cache[key] = value

    }

    override fun remove(key: String) = this.cache.remove(key)

    override fun get(key: String) : String?{
        return this.cache[key]
    }

    override fun clear() = this.cache.clear()
}