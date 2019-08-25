package il.ac.technion.cs.softwaredesign

class CLRUCacheFactory : ICacheFactory {
    override fun create(maxSize: Int): ICache {
        return CLRUCache(CPerpetualICache(),maxSize)
    }
}