package fakes.library.database

/**
 * Reference to a collection of documents inside the database. This class's sole purpose is to contain documents
 * Akin to *folders* in the file system.
 *
 * @see DocumentReference
 */
interface CollectionReference {
    /**
     * Access a document stored inside this collection. It will be created upon writing to it if it does not exist
     *
     * @param name: name of the document
     * @return the document's reference
     */
    fun document(name: String): ExtendableDocumentReference
}