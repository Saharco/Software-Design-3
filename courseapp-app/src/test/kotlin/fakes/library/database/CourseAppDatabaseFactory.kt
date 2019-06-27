package fakes.library.database

import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory
import io.github.vjames19.futures.jdk8.map
import java.util.concurrent.CompletableFuture

/**
 * Implementation of [DatabaseFactory].
 * This class produces new [CourseAppDatabase] instances
 */
class CourseAppDatabaseFactory(private val storageFactory: SecureStorageFactory) : DatabaseFactory {
    override fun open(dbName: String): CompletableFuture<Database> {
        return storageFactory.open(dbName.toByteArray())
                .map { storage -> CourseAppDatabase(CachedStorage(storage)) }
    }
}
