package il.ac.technion.cs.softwaredesign.utils

import il.ac.technion.cs.softwaredesign.lib.db.Database
import il.ac.technion.cs.softwaredesign.wrappers.KeywordsTracker
import java.io.Serializable
import java.util.concurrent.CompletableFuture

class DatabaseAbstraction(private val db: Database, private val document: String, private val id: String) {

    fun writePrimitive(key: String, value: Any?): CompletableFuture<Unit> {
        if (value != null) {
            return db.document(document)
                    .create(id)
                    .set(key to value)
                    .execute()
                    .thenApply { Unit }
        }
        return db.document(document)
                .delete(id, listOf(key))
                .execute()
                .thenApply { Unit }
    }

    fun <T : Serializable> writeSerializable(key: String, value: T?): CompletableFuture<Unit> {
        if (value != null) {
            return db.document(document)
                    .create(id)
                    .set(key to ObjectSerializer.serialize(value))
                    .execute()
                    .thenApply { Unit }
        }
        return db.document(document)
                .delete(id, listOf(key))
                .execute()
                .thenApply { Unit }
    }

    fun readString(key: String): CompletableFuture<String?> {
        return db.document(document)
                .find(id, listOf(key))
                .execute()
                .thenApply { it?.getAsString(key) }
    }

    fun <T : Serializable> readSerializable(key: String, ifAbsent: T): CompletableFuture<T> {
        return db.document(document)
                .find(id, listOf(key))
                .execute()
                .thenApply { document ->
                    if (document?.getAsString(key) == null)
                        ifAbsent
                    else
                        ObjectSerializer.deserialize(document.getAsString(key)!!) }
    }

    fun <T> removeFromList(listName: String, value: T): CompletableFuture<Unit> {
        return readSerializable(listName, ArrayList<T>()).thenApply {
            it.remove(value)
            it
        }.thenCompose {
            writeSerializable(listName, it)
        }
    }

    fun <K, V> removeFromMap(mapName: String, key: K): CompletableFuture<Unit> {
        return readSerializable(mapName, HashMap<K, V>()).thenApply {
            it.remove(key)
            it
        }.thenCompose {
            writeSerializable(mapName, it)
        }
    }
}