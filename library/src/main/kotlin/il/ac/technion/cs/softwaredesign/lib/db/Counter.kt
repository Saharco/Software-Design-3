package il.ac.technion.cs.softwaredesign.lib.db

class Counter : BaseModel() {

    companion object {
        const val KEY_VALUE = "value"
    }

    var value: Long = 0L
}