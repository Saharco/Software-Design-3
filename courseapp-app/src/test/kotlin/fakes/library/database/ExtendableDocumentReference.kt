package fakes.library.database

import fakes.library.database.CollectionReference
import fakes.library.database.DocumentReference


/**
 * Reference to a document inside the database that may also contain other collections.
 * @see DocumentReference
 */
interface ExtendableDocumentReference : DocumentReference {
    /**
     * Access a collection inside this document. It will be created if it does not exist
     *
     * @param name: name of the collection
     * @return the collection's reference
     */
    fun collection(name: String): CollectionReference
}
