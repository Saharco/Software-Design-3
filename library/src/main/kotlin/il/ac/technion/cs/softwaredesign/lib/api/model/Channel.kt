package il.ac.technion.cs.softwaredesign.lib.api.model

import il.ac.technion.cs.softwaredesign.lib.db.BaseModel

/**
 * Describes a channel in the application
 */
class Channel: BaseModel() {

    companion object {
        const val TYPE = "channel"
        const val KEY_NAME = "name"
        /**
         * Holds the list of operators for a given channel
         *
         */
        const val LIST_OPERATORS = "operators"
        /**
         * Holds the list of joined users for a given channel
         *
         */
        const val LIST_USERS = "users"
    }

    lateinit var name: String

}