package il.ac.technion.cs.softwaredesign.lib.db

import il.ac.technion.cs.softwaredesign.lib.db.dal.DataAccessLayer
import il.ac.technion.cs.softwaredesign.lib.db.dal.toDataClass
import java.util.concurrent.CompletableFuture


/**
 * Represents a single query for data, acting on a given data access layer (DAL)
 * The query is lazy evaluated, to execute the query for a result, use the execute() or executeFor() methods
 * @see execute
 */
abstract class Query(private val type: String, protected val dataAccessLayer: DataAccessLayer<String, Map<String, Any>>) {

    protected var semanticOperation: CompletableFuture<MutableMap<String, Any>> = CompletableFuture.completedFuture(HashMap())
    protected lateinit var id: String

    protected fun hashKey() : String {
        return "${this.id}_${this.type}"
    }

    protected abstract fun innerExecute() : CompletableFuture<Map<String, Any>?>

    /**
     * Execute the query for a result document.
     * @return A document satisfying the query, or null if none do
     */
    fun execute() : CompletableFuture<Document?> {
        return innerExecute()
                .thenApply { map ->
                    if (map == null || map.isEmpty()) null
                    else Document(id, type, map.toMap())
                }
    }

    /**
     * Execute the query for a result Model.
     * @param clazz the model class to be returned
     * @return A Model satisfying the query, or null if none do
     * @see BaseModel for more information about Models
     */
    fun <U: BaseModel> executeFor(clazz: Class<U>) : CompletableFuture<U?> {
        return innerExecute()
                .thenApply { docMap ->

                    if (docMap == null) return@thenApply null

                    val pojo = docMap.toDataClass(clazz)
                    pojo.id = id
                    pojo
                }
    }
}

/**
 * Represents a write query which satisfies a document being written to the database
 */
class WriteQuery(type: String, dataAccessLayer: DataAccessLayer<String, Map<String, Any>>, id: String, map: Map<String, Any> = HashMap()) : Query(type, dataAccessLayer) {

    init {
        this.id = id
        semanticOperation = semanticOperation.thenApply { currMap ->
            currMap.putAll(map)
            currMap
        }
    }

    /**
     * @see WriteQuery.set
     */
    fun set(vararg pairs: Pair<String, Any>): WriteQuery {
        pairs.asSequence().forEach {
            set(it.first, it.second)
        }
        return this
    }

    /**
     * Set a field in the document for a given key. If the field already exists, it is overridden by the last call to
     * this method which changes it. If it isn't, it is added to the document
     *
     * @param key The field's key
     * @param value The field's new value
     */
    fun set(key: String, value: Any): WriteQuery {
        // Add a set operation at the end of the list
        semanticOperation = semanticOperation.thenApply {
            it[key] = value
            it
        }
        return this
    }

    override fun innerExecute(): CompletableFuture<Map<String, Any>?> {
        return semanticOperation.thenCompose { map ->
            if (map.isNotEmpty()) {
                dataAccessLayer.writeObject(hashKey(), map).thenApply { map.toMap() }
            } else {
                CompletableFuture.completedFuture(null as Map<String, Any>?)
            }
        }
    }
}

/**
 * Represents a read query which satisfies a document in the database for a given criteria
 */
class ReadQuery(type: String, dataAccessLayer: DataAccessLayer<String, Map<String, Any>>, id: String, private val keys: List<String>) : Query(type, dataAccessLayer) {

    init {
        this.id = id
    }

    override fun innerExecute(): CompletableFuture<Map<String, Any>?> {
        return dataAccessLayer.readObject(hashKey(), keys)
    }
}

/**
 * Represents a delete query which satisfies a single document being deleted from the database
 */
class DeleteQuery(type: String, dataAccessLayer: DataAccessLayer<String, Map<String, Any>>, id: String, private val keys: List<String>): Query(type, dataAccessLayer) {

    init {
        this.id = id
    }

    override fun innerExecute(): CompletableFuture<Map<String, Any>?> {
        return dataAccessLayer.deleteObject(hashKey(), keys)
    }
}

/**
 * Represents an update query which satisfies a single document in the database which is being updated
 */
class UpdateQuery(type: String, dataAccessLayer: DataAccessLayer<String, Map<String, Any>>, id: String): Query(type, dataAccessLayer) {

    private var exists = true

    init {
        this.id = id
    }

    override fun innerExecute(): CompletableFuture<Map<String, Any>?> {
        return semanticOperation.thenCompose { currMap ->
            dataAccessLayer.readObject(hashKey(), listOf()).thenApply { map ->
                if (map == null) {
                    currMap.clear()
                    exists = false
                }
                currMap
            }
        }
        .thenCompose { currMap ->
            if (exists) {
                dataAccessLayer.writeObject(hashKey(), currMap).thenApply { currMap.toMap() }
            } else CompletableFuture.completedFuture(null as Map<String, Any>?)

        }.thenCompose<Map<String, Any>?> { currMap ->
            if (currMap == null || currMap.isEmpty()) CompletableFuture.completedFuture(null)
            else dataAccessLayer.writeObject(hashKey(), currMap).thenApply { currMap }
        }
    }

    /**
     * Update the mapping of the document
     * @param map A (key,value) mapping of the document's fields to be updated. Any fields which are not present in the
     * document, are created with their initial value set to the one provided in the mapping
     */
    fun set(map: Map<String, Any> = HashMap()): UpdateQuery {
        semanticOperation = semanticOperation.thenApply { currMap ->
            for (entry in map.entries) {
                currMap[entry.key] = entry.value
            }
            currMap
        }
        return this
    }

    /**
     * @see UpdateQuery.set
     */
    fun set(vararg pairs: Pair<String, Any>): UpdateQuery {
        pairs.asSequence().forEach {
            set(it.first, it.second)
        }
        return this
    }

    /**
     * Update the mapping of the document
     * @param key The key for the updated field
     * @param value The value for the updated field
     * @see UpdateQuery.set
     */
    fun set(key: String, value: Any): UpdateQuery {
        semanticOperation = semanticOperation.thenApply { currMap ->
            currMap[key] = value
            currMap
        }
        return this
    }

    /**
     * Remove the mapping of the document
     * @param key The key for the removed field
     */
    fun remove(key: String): UpdateQuery {
        semanticOperation = semanticOperation.thenApply { currMap ->
            currMap.remove(key)
            currMap
        }
        return this
    }
}