package il.ac.technion.cs.softwaredesign

import java.util.concurrent.CompletableFuture

/**
 * ICache interface, performing basic operations that could be done on caches.
 */
interface ICache {
    val size: Int

    operator fun set(key: String, value: String)

    operator fun get(key: String): String?

    fun remove(key: String): String?

    fun clear()
}