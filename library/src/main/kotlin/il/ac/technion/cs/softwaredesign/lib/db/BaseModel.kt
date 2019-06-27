package il.ac.technion.cs.softwaredesign.lib.db

/**
 * Represents a base data model for a database object. This can be used to extend your own models and work with objects
 * for the database.
 *
 * To create an object, extends this class and add all fields.
 * For each field, you should add it's name as a constant string (this is used when reading the object fields).
 * This example shows how to create a model for a User:
 *
 * ```
 * class User: BaseModel() {
 *
 * companion object {
 *      const val TYPE = "user"
 *
 *      const val KEY_PASSWORD_HASH = "password"
 *      const val KEY_LOGGED_IN = "loggedIn"
 *      const val KEY_IS_ADMINISTRATOR = "isAdmin"
 *      const val LIST_CHANNELS = "channels"
 *      const val KEY_CREATED_AT = "timestamp"
 * }
 *
 * lateinit var password: String
 * var loggedIn: Boolean = false
 * var isAdmin: Boolean = false
 * var timestamp: Long = -1
 *
 * }
 * ```
 *
 * Key fields must be consistent with the field names inside the class (for serialization).
 *
 * An example of creating and the finding a user:
 *
 * ```
 *  return db.document(User.TYPE)
 *      .create("user1")
 *      .set(User.KEY_PASSWORD_HASH, password.hashPassword())
 *      .set(User.KEY_IS_ADMINISTRATOR, true)
 *      .set(User.KEY_CREATED_AT, Date().time)
 *      .executeFor(User::class.java).thenApply { user -> println(user.id) // Should print user1 }
 *      .join()
 * ```
 *
 * ``
 * val keys = listOf(User.KEY_PASSWORD_HASH, User.KEY_LOGGED_IN, User.KEY_CREATED_AT, User.KEY_IS_ADMINISTRATOR)
 * return db.document(User.TYPE)
 *      .find("user1", keys)
 *      .executeFor(User::class.java)
 *      .thenApply { user -> println(user.isAdmin) // Should print true }
 *      .join()
 * ```
 *
 */
abstract class BaseModel {

    lateinit var id: String

}