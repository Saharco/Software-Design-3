package il.ac.technion.cs.softwaredesign

interface IStorage {

    fun readFromMap(key:String):String?

    fun writeToMap(key: String, value: String)

    fun getCounterValue(key: Int, additionalKeyElement: String? = null):Long

    fun setCounterValue(value: Long, key: Int, additionalKeyElement: String? = null)

    fun incCounter(key: Int, additionalKeyElement: String? = null):Long

    fun decCounter(key: Int, additionalKeyElement: String? = null):Long

    fun makeValid(fileId: Int, key: String, additionalKeyElement: String? = null)

    fun invalidate(fileId: Int, key: String, additionalKeyElement: String? = null)

    fun isValid(fileId: Int, key: String, additionalKeyElement: String? = null):Boolean

    fun addToList(fileId: Int, key: String?=null, value: Int): Boolean

    fun listContains(fileId: Int, key: String?=null, value: Int): Boolean

    fun removeFromList(fileId: Int, key: String?=null, value: Int): Boolean

    fun getList(fileId: Int, key: String?=null): List<Int>
}