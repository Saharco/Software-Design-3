package il.ac.technion.cs.softwaredesign.lib.api.model

import il.ac.technion.cs.softwaredesign.lib.db.BaseModel

class Session : BaseModel() {

    companion object {
        const val TYPE = "session"
        const val KEY_USER = "user"
    }

    lateinit var user: String

}