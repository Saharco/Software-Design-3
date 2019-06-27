package fakes.library.mocks

import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory
import io.github.vjames19.futures.jdk8.Future
import java.util.concurrent.CompletableFuture

class SecureStorageFactoryMock : SecureStorageFactory {

    private val existingDatabases = mutableMapOf<String, SecureStorage>()

    override fun open(name: ByteArray): CompletableFuture<SecureStorage> {
        val dbName = String(name)
        return CompletableFuture.supplyAsync {
            if (existingDatabases.containsKey(dbName)) {
                existingDatabases[dbName]!!
            } else {
                val storage = SecureStorageMock()
                existingDatabases[dbName] = storage
                storage
            }
        }
    }
}