package il.ac.technion.cs.softwaredesign.lib.db

import il.ac.technion.cs.softwaredesign.lib.db.dal.DocumentDataAccessLayer
import il.ac.technion.cs.softwaredesign.lib.db.dal.KeyPair
import il.ac.technion.cs.softwaredesign.lib.db.dal.TreeDataAccessLayer
import il.ac.technion.cs.softwaredesign.lib.utils.StorageList
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory
import java.util.concurrent.CompletableFuture
import javax.inject.Inject

class Database @Inject constructor(private val factory: SecureStorageFactory) {

    companion object {
        const val KEY_METADATA_COUNTER = "counterDAL"
        const val DEFAULT_DATABASE = "__default"
        const val LIST_DATABASE = "__lists"
        const val COUNTER_DATABASE = "__counter"
    }

    private val treeDALS: MutableMap<String, TreeDataAccessLayer<String>> = HashMap()
    private val listDALS: MutableMap<String, StorageList> = HashMap()
    private val listStorage = factory.open(LIST_DATABASE.toByteArray())
    private val documentDAL: DocumentDataAccessLayer = DocumentDataAccessLayer(factory.open(DEFAULT_DATABASE.toByteArray()))


    /**
     * Starts a query for data that is not metadata
     * @type the document type, for this query
     * @see BaseModel for information about document types
     * @see QueryBuilder for more information about the queries
     * a query example: adding a user to the system
     * ``
     * document("User").create("user1")
     *                 .set("user1_username" to "user1",
     *                      "user1_password" to "1234",
     *                      "user1_isAdmin" to true)
     *                 .execute()
     * ```
     */
    fun document(type: String = ""): QueryBuilder = QueryBuilder(type, documentDAL)

    /**
     * starts a query for metadata data
     * @param query the current metadata function that was called,
     * used to decide which storage to use for this query
     * @see MetadataQueryBuilder for more information about the queries
     */
    fun metadata(query: String): MetadataQueryBuilder {
        val dal = treeDALS[query] ?: TreeDataAccessLayer(factory.open(query.toByteArray()))
        treeDALS[query] = dal
        return MetadataQueryBuilder(query, dal)
    }

    /**
     *
     */
    fun list(type: String = "", name: String): ListQueryBuilder {
        val key = type + "_" + name
        val dal = listDALS[key] ?: StorageList(key, listStorage)
        listDALS[key] = dal
        return ListQueryBuilder(dal)
    }

    /**
     * A query builder for the data
     * Allows create,find,update,delete for data stored in the storage
     * Each query works on a single document
     * The QueryBuilder functions represent a query type, and allows you building your own query
     * with the provided functions
     * @see Query for more information about the query functions
     */
    inner class QueryBuilder(private val type: String, private val dataAccessLayer: DocumentDataAccessLayer) {

        /**
         * Check if a document with a given id exists in the database
         * @param id The document's id
         */
        fun exists(id: String): CompletableFuture<Boolean> {
            return find(id, listOf()).execute().thenApply { document -> document == null }
        }

        /**
         * Starts a create document query, allows creation of documents into the storage.
         * @param id The document's id
         * @param obj A (key, value) mapping of the document
         *
         */
        fun create(id: String, obj: Map<String, Any> = HashMap()) = WriteQuery(type, dataAccessLayer, id, obj)

        /**
         * Starts a find document query, allows retrieving documents from the storage.
         * @param id The document's id
         * @param keys The keys to select. Refer to ```BaseModel``` for more information on how to represent keys in the model class
         */
        fun find(id: String, keys: List<String>): ReadQuery = ReadQuery(type, dataAccessLayer, id, keys)

        /**
         * Starts an update document query, allows adding/changing for existing documents.
         * @param id The document's id
         */
        fun update(id: String): UpdateQuery = UpdateQuery(type, dataAccessLayer, id)

        /**
         * Starts a Delete document query, allows deleting documents from the storage.
         * @param id The document's id
         * @param keys The keys to return upon successful deletion. Refer to ```BaseModel``` for more information on how to represent keys in the model class
         */
        fun delete(id: String, keys: List<String>): DeleteQuery = DeleteQuery(type, dataAccessLayer, id, keys)
    }

    inner class ListQueryBuilder(private val list: StorageList) {

        fun insert(element: String): CompletableFuture<Unit> {
            return list.insert(element)
        }

        fun get(index: Int): CompletableFuture<String?> {
            return list.get(index)
        }

        fun remove(element: String): CompletableFuture<Unit> {
            return indexOf(element)
                    .thenCompose { i -> if (i != -1 ) remove(i) else CompletableFuture.completedFuture(Unit) }
        }

        private fun remove(index: Int): CompletableFuture<Unit> {
            return list.remove(index)
        }

        private fun indexOf(element: String): CompletableFuture<Int> {
            return list.asSequence()
                    .thenApply { s -> s.filter { it.second == element }.firstOrNull()?.first ?: -1 }
        }

        fun contains(element: String) : CompletableFuture<Boolean> {
            return list.asSequence()
                    .thenApply { s -> s.map { it.second }.any { it == element } }
        }

        fun asSequence() = list.asSequence()
    }

    /**
     * A query builder for the metadata
     * Allows create, find, update, delete of metadata from the storage
     * Each query works on a single metadata data, can only get/create one metadata for each query
     * Does not provide a fluent API like QueryBuilder
     */
    inner class MetadataQueryBuilder(private val query: String, private val dataAccessLayer: TreeDataAccessLayer<String>) {

        private fun getOrCreateMetadataCounter() : CompletableFuture<Counter> {
            return document(COUNTER_DATABASE)
                    .find(KEY_METADATA_COUNTER, listOf(Counter.KEY_VALUE))
                    .executeFor(Counter::class.java)
                    .thenCompose { counter ->
                        if (counter == null) document(COUNTER_DATABASE).create(KEY_METADATA_COUNTER).set(Counter.KEY_VALUE, 0).executeFor(Counter::class.java)
                        else CompletableFuture.completedFuture(counter)
                    }
                    .thenApply { it!! }
        }

        /**
         * Returns the top n elements from the metadata storage
         * @see TreeDataAccessLayer for more information
         */
        fun getTop(n: Int): CompletableFuture<List<Pair<KeyPair<Long>, String>>> {
            return dataAccessLayer.getSequence(n).thenApply { it.take(n).toList() }
        }

        /**
         * Returns metadata value from the storage
         * @param id the metadata id that was used in the create function
         * for example: Channel metadata that represents the number of users in it, the id can be channel name
         * @return the requested metadata value, or null if does not exist
         */
        fun find(id: String): CompletableFuture<Long?> {
            return document(COUNTER_DATABASE)
                    .find(id, listOf(query))
                    .execute()
                    .thenApply { doc -> doc?.getAsKeyPair(query)?.getFirst() }
        }

        /**
         * deletes metadata from the storage
         * @param id the metadata id that was used in the create function
         * for example: Channel metadata that represents the number of users in it, the id can be channel name
         * @return the deleted metadata value, null if doesnt not exist
         */
        fun delete(id: String): CompletableFuture<Long?> {
            return document(COUNTER_DATABASE)
                    .find(id, listOf(query))
                    .execute()
                    .thenApply { it?.getAsKeyPair(query) }
                    .thenCompose { keyPair -> dataAccessLayer.deleteObject(keyPair!!, listOf(query)).thenApply { keyPair.getFirst() } }
        }

        /**
         * creates metadata in the storage
         * @param id the metadata identifier, will be used for finding/deleteing/updating the metadata
         * for example: Channel metadata that represents the number of users in it, the id can be channel name
         * @param value the metadata value
         */
        fun create(id: String, value: Long) : CompletableFuture<Unit> {
            return getOrCreateMetadataCounter()
                    .thenApply { it.value }
                    .thenCompose { cnt ->
                        val keyPair = KeyPair(value, cnt)
                        dataAccessLayer.writeObject(keyPair, id)
                                .thenCompose {
                                    document(COUNTER_DATABASE).exists(id).thenCompose { exists ->
                                        if (exists) dataAccessLayer.deleteObject(keyPair, listOf(query))
                                                .thenCompose { document(COUNTER_DATABASE).create(KEY_METADATA_COUNTER).set(Counter.KEY_VALUE, cnt.inc()).executeFor(Counter::class.java) }
                                                .thenApply { keyPair }
                                        else CompletableFuture.completedFuture(keyPair)
                                    }
                                }
                    }
                    .thenCompose { keyPair ->
                        keyPair.setFirst(value)
                        document(COUNTER_DATABASE).create(id).set(query, keyPair).execute()
                    }
                    .thenApply {  }
        }

        /**
         * updates an exsiting metadata in the storage
         * @param id the metadata id that was used in the create function
         * for example: Channel metadata that represents the number of users in it, the id can be channel name
         * @param value the new value for the metadata
         */
        fun update(id: String, value: Long) : CompletableFuture<Unit>{
            return document(COUNTER_DATABASE)
                    .find(id, listOf(query))
                    .execute()
                    .thenCompose { doc ->
                        val keyPair = doc!!.getAsKeyPair(query)!!
                        dataAccessLayer.deleteObject(keyPair, listOf(query)).thenApply { keyPair }}
                    .thenCompose { keyPair ->
                        keyPair.setFirst(value)

                        document(COUNTER_DATABASE)
                                .update(id)
                                .set(query, keyPair)
                                .execute()
                                .thenApply { keyPair }
                    }
                    .thenCompose { dataAccessLayer.writeObject(it, id) }
        }
    }
}


