package fakes.library.database

import java.util.concurrent.CompletableFuture


/**
 *  Reference to a document in the database which stores information in a field-value fashion.
 *
 *  Akin to *files* in the file system
 */
interface DocumentReference {

    /**
     * Sets a field in the document.
     * The information will be stored upon creating the document with a [write]
     *
     * @param field: 1st value: field's name. 2nd value: data
     */
    fun set(field: Pair<String, String>): DocumentReference

    /**
     * Sets a field in the document that corresponds to a list.
     * The information will be stored upon creating the document with a [write]
     *
     * @param field: field's name
     * @param list: list of strings to store in this field
     */
    fun set(field: String, list: List<String>): DocumentReference

    /**
     * Sets multiple fields in the document.
     * The information will be stored upon creating the document with a [write]
     *
     * @param data: map of field-value information
     */
    fun set(data: Map<String, String>): DocumentReference

    /**
     * Write the document to the database.
     *
     * This is a *terminal* operation
     *
     * @throws IllegalStateException if the document to be written contains no information
     * @throws IllegalArgumentException if the document to be written already exists
     */
    fun write(): CompletableFuture<Unit>

    /**
     * Reads a document's field from the database, or null if it does not exist
     *
     * This is a *terminal* operation
     *
     * @param field: name of the field from which the desired string will be read
     */
    fun read(field: String): CompletableFuture<String?>


    /**
     * Reads a document's list field from the database, or null if it does not exist
     *
     * This is a *terminal* operation
     *
     * @param field: name of the field from which the desired list will be read
     */
    fun readList(field: String): CompletableFuture<List<String>?>

    /**
     * Update information of a document in the database. This operation may be performed on an existing document
     *
     * This is a *terminal* operation
     *
     * @throws IllegalStateException if the document to be updated contains no extra information
     */
    fun update(): CompletableFuture<Unit>

    /**
     * Delete a document from the database.
     *
     * This is a *terminal* operation
     */
    fun delete(): CompletableFuture<Unit>

    /**
     * Delete a document's fields from the database.
     *
     * This is a *terminal* operation
     *
     * @param fields: list of fields to be deleted
     */
    fun delete(fields: List<String>): CompletableFuture<Unit>

    /**
     * Returns whether or not the document exists in the database.
     *
     * This is a *terminal* operation
     */
    fun exists(): CompletableFuture<Boolean>
}