package fakes.library.database

import il.ac.technion.cs.softwaredesign.storage.SecureStorage

/**
 * Implementation of [ExtendableDocumentReference].
 *
 * This class is abstract - it can only be constructed via [CourseAppCollection]
 */
abstract class CourseAppExtendableDocument internal constructor(path: String,
                                                                storage: SecureStorage)
    : CourseAppDocument(path, storage), ExtendableDocumentReference {

    override fun collection(name: String): CollectionReference {
        return object : CourseAppCollection(path + name.replace("/", ""), storage) {}
    }
}