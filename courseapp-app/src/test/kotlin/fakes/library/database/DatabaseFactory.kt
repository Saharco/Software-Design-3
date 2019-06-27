package fakes.library.database

import fakes.library.database.Database
import java.util.concurrent.CompletableFuture

/**
 * Factory for accessing database instances.
 *
 * This class produces *file systems*
 */
interface DatabaseFactory {
    /**
     * Opens a given database.
     * If the given database does not exist: a new one is created
     *
     * @param dbName: name of the database
     * @return reference to the root of the database
     */
    fun open(dbName: String): CompletableFuture<Database>
}