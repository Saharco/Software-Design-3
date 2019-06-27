package fakes.library.utils

import fakes.library.database.CachedStorage
import fakes.library.database.Database
import java.util.concurrent.CompletableFuture

/**
 * Wrapper class that maps database names to their respective databases and storage name to their respective storages
 * @param dbMap: String->Database map
 * @param storageMap: String->SecureStorage map
 */
class DatabaseMapper(private val dbMap: Map<String, CompletableFuture<Database>>,
                     private val storageMap: Map<String, CompletableFuture<CachedStorage>>) {

    /**
     * @return the Database instance that [dbName] is being mapped to
     */
    fun getDatabase(dbName: String): Database {
        return dbMap.getValue(dbName).get()
    }

    /**
     * @return the CachedStorage instance that [storageName] is being mapped to
     */
    fun getStorage(storageName: String): CachedStorage {
        return storageMap.getValue(storageName).get()
    }
}