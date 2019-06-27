package il.ac.technion.cs.softwaredesign.lib.api.model

import il.ac.technion.cs.softwaredesign.lib.db.BaseModel

class User: BaseModel() {

    companion object {
        const val TYPE = "user"

        const val KEY_PASSWORD_HASH = "password"
        const val KEY_LOGGED_IN = "loggedIn"
        const val KEY_IS_ADMINISTRATOR = "isAdmin"
        const val LIST_CHANNELS = "channels"
        const val KEY_CREATED_AT = "timestamp"
    }

    lateinit var password: String
    var loggedIn: Boolean = false
    var isAdmin: Boolean = false
    var timestamp: Long = -1

}