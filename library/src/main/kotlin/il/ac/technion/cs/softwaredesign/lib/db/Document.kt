package il.ac.technion.cs.softwaredesign.lib.db

import il.ac.technion.cs.softwaredesign.lib.db.dal.KeyPair
import java.io.Serializable

/**
 * Represents a single document in the database of a distinct type, as an object mapping from keys to values
 */
data class Document(var id: String,
                    var type: String,
                    private var documentMap: Map<String, Any> = HashMap()): Serializable {

    /**
     * Find a value by a given key
     * @param key The key to search by
     * @return The value for this key in this document, or null if such a key doesn't exist
     */
    fun get(key: String): Any? {
        return documentMap[key]
    }

    /**
     * Find a value by a given key and treat it as a string
     * @see Document.get
     * @return Same as Document.get, but returns null if the value is not a string
     */
    fun getAsString(key: String) : String? {
        val value = documentMap[key] ?: return null
        return if (value is String) value else null
    }

    /**
     * Find a value by a given key and treat it as an integer
     * @see Document.get
     * @return Same as Document.get, but returns null if the value is not an integer
     */
    fun getInteger(key: String): Int? {
        val value = documentMap[key] ?: return null
        return if (value is Int) value else null
    }

    /**
     * Find a value by a given key and treat it as a boolean
     * @see Document.get
     * @return Same as Document.get, but returns null if the value is not a boolean
     */
    fun getBoolean(key: String): Boolean {
        val value = documentMap[key] ?: return false
        return if (value is Boolean) value else false
    }

    inline fun <reified T> toArray(list: List<*>): Array<T> {
        return (list as List<T>).toTypedArray()
    }

    fun getAsList(key: String): MutableList<String>? {
        val value = documentMap[key] ?: return null
        return if (value is List<*>) mutableListOf(*toArray(value)) else null
    }

    fun getAsKeyPair(key: String): KeyPair<Long>?{
        val value = documentMap[key] ?: return null

        return if (value is KeyPair<*>) {
            value as KeyPair<Long>
        } else null
    }
}