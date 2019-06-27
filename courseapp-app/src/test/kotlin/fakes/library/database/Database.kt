package fakes.library.database

import fakes.library.database.CollectionReference

/**
 * This class is a reference to the database's root whose sole purpose is
 * to branch to different collections that contain the database's information (documents)
 *
 * This is the *root* of the file system
 *
 * @see CollectionReference
 * @see DocumentReference
 */
interface Database {
    /**
     * Access a collection in the database's root. It will be created if it does not exist
     *
     * @param name: name of the collection
     * @return the collection's reference
     */
    fun collection(name: String): CollectionReference
}