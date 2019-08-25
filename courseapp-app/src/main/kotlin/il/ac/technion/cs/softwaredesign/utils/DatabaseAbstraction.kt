package il.ac.technion.cs.softwaredesign.utils

import il.ac.technion.cs.softwaredesign.lib.db.Database
import il.ac.technion.cs.softwaredesign.wrappers.KeywordsTracker
import java.io.Serializable
import java.util.concurrent.CompletableFuture

/**
 * This class serves as an abstraction on top of the Database that was provided by the library we chose.
 * Provides easy & convenient API for (de)serialization of complex objects.
 * @param db - the Database the abstraction instance works on.
 * @param document - the document's name that the abstraction modifies and reads.
 * @param id - the id of the document.
 * @see: [Database]
 */
class DatabaseAbstraction(private val db: Database, private val document: String, val id: String) {

    private val deleted = "NULL_LOGICAL_DELETE"

    /**
     * Writes a primitive type (such as Int) to the document.
     * @param key - the field's key.
     * @param value - the value to write to the supplied field.
     */
    fun writePrimitive(key: String, value: Any?): CompletableFuture<Unit> {
        if (value != null) {
            return db.document(document)
                    .create(id)
                    .set(key to value)
                    .execute()
                    .thenApply { Unit }
        }
        return db.document(document)
                .create(id)
                .set(key to deleted)
                .execute()
                .thenApply { Unit }
    }

    /**
     * Writes a Serializable object into a field in the document.
     * @param key - the field's key.
     * @param value - the value to write to the supplied field.
     */
    fun <T : Serializable> writeSerializable(key: String, value: T?): CompletableFuture<Unit> {
        if (value != null) {
            return db.document(document)
                    .create(id)
                    .set(key to ObjectSerializer.serialize(value))
                    .execute()
                    .thenApply { Unit }
        }
        return db.document(document)
                .update(id)
                .remove(key)
                .execute()
                .thenApply { Unit }
    }

    /**
     * Reads a String from a field.
     * @param key - the String's key.
     */
    fun readString(key: String): CompletableFuture<String?> {
        return db.document(document)
                .find(id, listOf(key))
                .execute()
                .thenApply { it?.getAsString(key) }
                .thenApply {
                    if(it == deleted)
                        null
                    else
                        it
                }
    }

    /**
     * Reads a Serializable object from the document.
     * @param key - the object's key.
     * @param ifAbsent - if the field is empty this value will be returned.
     */
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

    /**
     * Removes an item from a list which is stored in the document.
     * @param listName - the list's name (key).
     * @param value - the item to be removed from the list.
     */
    fun <T> removeFromList(listName: String, value: T): CompletableFuture<Unit> {
        return readSerializable(listName, ArrayList<T>()).thenApply {
            it.remove(value)
            it
        }.thenCompose {
            writeSerializable(listName, it)
        }
    }

    /**
     * Removes an item from a map which is stored in the document given it's key.
     * @param mapName - the map's name (key)
     * @param key - the key that identifies the item that needs to be deleted from the map.
     */
    fun <K, V> removeFromMap(mapName: String, key: K): CompletableFuture<Unit> {
        return readSerializable(mapName, HashMap<K, V>()).thenApply {
            it.remove(key)
            it
        }.thenCompose {
            writeSerializable(mapName, it)
        }
    }
}