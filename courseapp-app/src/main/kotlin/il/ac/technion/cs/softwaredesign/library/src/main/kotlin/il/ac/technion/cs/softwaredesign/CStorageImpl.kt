package il.ac.technion.cs.softwaredesign

import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import java.util.concurrent.CompletableFuture


const val MAX_STORAGE_SIZE = 3000000
class CStorageImpl @Inject constructor(private val ICache: ICache, private val storage: CompletableFuture<SecureStorage>) :IStorage {

    enum class DataStatus{
        VALID, INVALID
    }

    //writes data to secure storage
    private fun writeData(key : String, value: String){
        val keyInBytes = key.toBytes()
        val valueInBytes = value.toBytes()
        return storage.thenCompose { it.write(keyInBytes,valueInBytes) }.join()
    }

    //reads data from secure storage
    private fun readData(key : String) : String? {
        val keyInBytes = key.toBytes()
        return storage.thenCompose { it.read(keyInBytes)}.thenApply{
                    if(it==null) null else String( it,Charsets.UTF_8)
               }.get()
    }

    private fun buildKeyInMap(key: String):String{
        return "map-$key"
    }

    private fun buildCounterKey(key: Int, additionalKeyElement: String? = null):String{
        val suffix = if(additionalKeyElement==null) key.toString() else "$key[$additionalKeyElement]"
        return "counter-$suffix"
    }

    private fun buildListKey(fileId:Int, key:String?):String{
        return if(key!=null) "list-$fileId,$key" else "list-$fileId"
    }

    /*
    * generates validating key. -
    * builds a unique key per (fileId, key, additionalKeyElement)
    * the unique key is if there's an additional key element
    * fileId[positions of hyphens in the word key separated by ","]key-additionalKeyElement
    * otherwise it would be : fileId,key
    */
    private fun buildValidatingKey(fileId:Int, key : String, additionalKeyElement :String? = null):String{
        if(additionalKeyElement==null) return "$fileId,$key"
        val list = mutableListOf<Int>()
        var index = key.indexOf('-')
        while (index >= 0) {
            list.add(index)
            index = key.indexOf('-', index + 1)
        }
        val indices = list.joinToString(prefix = "[", postfix = "]")
        val keyWithoutHyphen = key.replace("-","")
        return "validator-$fileId$indices$keyWithoutHyphen-$additionalKeyElement"
    }

    private operator fun get(key :String): String? {
        return if (ICache[key] != null) {
            ICache[key]
        } else {
            val value = readData(key)
            if (value != null) ICache[key] = value
            if(ICache.size > MAX_STORAGE_SIZE) ICache.clear()
            value
        }
    }

    private operator fun set(key:String, value:String){
        ICache[key] = value
        writeData(key,value)
        if(ICache.size > MAX_STORAGE_SIZE) ICache.clear()
    }

    /**
     * getting the requested [key] value from the storage map.
     * If not found, null will be returned.
     */
    override fun readFromMap(key:String):String?{
        val keyInMap = buildKeyInMap(key)
        return this[keyInMap]
    }

    /**
     * write a [key] [value] mapping into the storage map.
     * If the [key] already exists, its value will be overwritten with the new [value].
     */
    override fun writeToMap(key:String, value:String){
        val keyInMap = buildKeyInMap(key)
        this[keyInMap] = value
    }

    /**
     * Gets the counter value from storage at a key that is unique for counters per ([key],[additionalKeyElement] if exists)
     * If there is no corresponding value, 0 will be returned.
     */
    override fun getCounterValue(key: Int, additionalKeyElement: String?) : Long {
        val counterKey = buildCounterKey(key,additionalKeyElement)
        return get(counterKey)?.toLong() ?: 0
    }

    /**
     * Sets the counter value in the storage to the given [value] at a key that is unique for counters
     * per ([key],[additionalKeyElement] if exists)
     */
    override fun setCounterValue(value : Long, key : Int, additionalKeyElement : String?){
        val counterKey = buildCounterKey(key,additionalKeyElement)
        set(counterKey,value.toString())
    }

    /**
     * Increases the counter value by 1 at a key that is unique for counters per ([key],[additionalKeyElement] if exists)
     * If the counter is not initialized, it will be set at the corresponding key with initial value of 1.
     */
    override fun incCounter(key: Int, additionalKeyElement: String?):Long{
        val currentValue = getCounterValue(key,additionalKeyElement)
        setCounterValue(currentValue+1,key,additionalKeyElement)
        return currentValue+1
    }

    /**
     * Decreases the counter value by 1 at a key that is unique for counters per ([key],[additionalKeyElement] if exists)
     * If the counter is not initialized, it will be set at the corresponding key with initial value of -1.
     */
    override fun decCounter(key: Int, additionalKeyElement: String? ):Long{
        val currentValue = getCounterValue(key,additionalKeyElement)
        setCounterValue(currentValue-1,key,additionalKeyElement)
        return currentValue-1
    }

    /**
     * Validates a key that is unique per ([fileId],[key],[additionalKeyElement])
     * By validates, we mean that the function isValid will return true when called with the same parameters
     *
     * For example : makeValid(IS_OPERATOR,username,channel) or makeValid(IS_ADMIN,username)
     */
    override fun makeValid(fileId:Int, key : String, additionalKeyElement :String?){
        val k = buildValidatingKey(fileId,key,additionalKeyElement)
        set(k,DataStatus.VALID.name)
    }

    /**
     * Invalidates a key that is unique per ([fileId],[key],[additionalKeyElement])
     * By invalidates, we mean that the function isValid will return false when called with the same parameters
     *
     * For example : invalidate(IS_OPERATOR,username,channel) or invalidate(IS_ADMIN,username)
     */
    override fun invalidate(fileId:Int, key : String, additionalKeyElement :String?){
        val k = buildValidatingKey(fileId,key,additionalKeyElement)
        set(k,DataStatus.INVALID.name)
        ICache.remove(k)
    }

    /**
     * Checks whether the function makeValid was called with the same parameters and invalidate was not called afterwards
     */
    override fun isValid(fileId:Int, key : String, additionalKeyElement :String?):Boolean{
        val k = buildValidatingKey(fileId,key,additionalKeyElement)
        return get(k)==DataStatus.VALID.name
    }

    /**
     * adds [value] to the list at [key] in [fileId]
     * @return : false if the list already contains [value], true otherwise
     */
    override fun addToList(fileId:Int, key:String?, value:Int):Boolean{
        if(listContains(fileId,key,value)) return false
        val k = buildListKey(fileId,key)
        val storedList = get(k) ?: ""
        val newList = if(storedList=="") value.toString() else "$storedList,$value"
        set(k,newList)
        return true
    }

    /**
     * checks if the list at [key] in [fileId] contains [value]
     * @return : true if the list contains [value], false otherwise
     */
    override fun listContains(fileId:Int,key:String?,value:Int):Boolean{
        val list = getList(fileId,key)
        return list.contains(value)
    }

    /**
     * removes [value] from the list at [key] in [fileId]
     * @return : false if the list doesn't contain [value], true otherwise
     */
    override fun removeFromList(fileId: Int, key: String?, value: Int): Boolean {
        if(!listContains(fileId,key,value)) return false
        val k = buildListKey(fileId,key)
        val storedList = get(k) ?: ""
        var newList = storedList.replace(",$value,",",")
        if(storedList == "$value"){
            newList = ""
        }
        if(newList.startsWith("$value,"))
            newList = newList.drop(value.toString().length+1)
        if(newList.endsWith(",$value"))
            newList = newList.dropLast(value.toString().length+1)
        set(k,newList)
        return true
    }

    /**
     * @return : the list  at [key] in [fileId]
     */
    override fun getList(fileId: Int, key: String?): List<Int> {
        val k = buildListKey(fileId,key)
        val storedList = get(k) ?: return ArrayList()
        if(storedList=="") return ArrayList()
        return storedList.split(",").map{it.toInt()}
    }
}