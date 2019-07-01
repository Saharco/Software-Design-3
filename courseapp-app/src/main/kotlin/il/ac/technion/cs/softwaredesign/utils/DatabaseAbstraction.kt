package il.ac.technion.cs.softwaredesign.utils

import il.ac.technion.cs.softwaredesign.lib.db.Database
import il.ac.technion.cs.softwaredesign.wrappers.KeywordsTracker
import java.io.Serializable
import java.util.concurrent.CompletableFuture

class DatabaseAbstraction(private val db: Database, private val document: String, private val id: String) {

    fun writePrimitiveToDocument(key: String, value: Any?): CompletableFuture<Unit> {
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

    fun <T : Serializable> writeSerializableToDocument(key: String, value: T?): CompletableFuture<Unit> {
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

    fun readStringFromDocument(key: String): CompletableFuture<String?> {
        return db.document(document)
                .find(id, listOf(key))
                .execute()
                .thenApply { it?.getAsString(key) }
    }

    fun <K, V> readMapFromDocument(key: String): CompletableFuture<HashMap<K, V>> {
        return db.document(document)
                .find(id, listOf(key))
                .execute()
                .thenApply { document ->
                    if (document?.getAsString(key) == null)
                        hashMapOf()
                    else
                        ObjectSerializer.deserialize<HashMap<K, V>>(document.getAsString(key)!!)
                }
    }

    fun <T> readListFromDocument(key: String): CompletableFuture<ArrayList<T>> {
        return db.document(document)
                .find(id, listOf(key))
                .execute()
                .thenApply { document ->
                    if (document?.getAsString(key) == null)
                        arrayListOf()
                    else
                        ObjectSerializer.deserialize<ArrayList<T>>(document.getAsString(key)!!)
                }
    }

    fun readKeywordsTrackerFromDocument(key: String): CompletableFuture<KeywordsTracker> {
        return db.document(document)
                .find(id, listOf(key))
                .execute()
                .thenApply { document ->
                    if (document?.getAsString(key) == null)
                        KeywordsTracker()
                    else
                        ObjectSerializer.deserialize(document.getAsString(key)!!)
                }
    }


    fun <T> removeFromList(listName: String, value: T): CompletableFuture<Unit> {
        return readListFromDocument<T>(listName).thenApply {
            it.remove(value)
            it
        }.thenCompose {
            writeSerializableToDocument(listName, it)
        }
    }

    fun <K, V> removeFromMap(mapName: String, key: K): CompletableFuture<Unit> {
        return readMapFromDocument<K, V>(mapName).thenApply {
            it.remove(key)
            it
        }.thenCompose {
            writeSerializableToDocument(mapName, it)
        }
    }
}