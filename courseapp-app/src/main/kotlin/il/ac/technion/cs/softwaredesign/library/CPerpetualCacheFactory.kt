package il.ac.technion.cs.softwaredesign

import com.google.inject.Inject

class CPerpetualCacheFactory @Inject constructor() : ICacheFactory {
    override fun create(maxSize: Int): ICache {
        return CPerpetualICache()
    }
}