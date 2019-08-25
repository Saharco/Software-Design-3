package il.ac.technion.cs.softwaredesign

interface IDictionary {

    fun delete(key:ArrayList<String>)

    fun insert(key: ArrayList<String>, data: String)

    fun getAllKeysInOrder(): ArrayList<String>

    fun getDataSortedByKeysInDescendingOrder(n:Int): ArrayList<String>

    fun getSize() : Int
}