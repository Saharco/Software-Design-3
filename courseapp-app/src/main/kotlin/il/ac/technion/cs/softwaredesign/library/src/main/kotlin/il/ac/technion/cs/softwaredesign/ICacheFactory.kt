package il.ac.technion.cs.softwaredesign

/**
 * ICache factory
 */
interface ICacheFactory {
    fun create(maxSize : Int = 1000) : ICache
}