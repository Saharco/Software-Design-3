package il.ac.technion.cs.softwaredesign

/**
 * Util class, to be used to get unique indices
 */
class CUniqueIndexGeneratorImpl (private val IStorage : IStorage,
                                 private val fileId:Int) : IUniqueIndexGenerator{

    private val stackSizeKey = "indices_stack_size,$fileId"
    private val numOfUsedIndicesKey = "used_indices_num,$fileId"

//    private var stackSize = IStorage.readFromMap(stackSizeKey)?.toInt() ?: 0
//    private var numOfUsedIndices = IStorage.readFromMap(numOfUsedIndicesKey)?.toInt() ?: 0

    private fun stackSize():Int{
        return IStorage.readFromMap(stackSizeKey)?.toInt() ?: 0
    }

    private fun numOfUsedIndices():Int{
        return IStorage.readFromMap(numOfUsedIndicesKey)?.toInt() ?: 0
    }
    private fun setNumOfUsedIndices(value :Int){
        IStorage.writeToMap(numOfUsedIndicesKey,value.toString())
    }

    private fun setStackSize(value :Int){
        IStorage.writeToMap(stackSizeKey,value.toString())
    }

    private fun popIndexFromStack():Int{
        val stackSize = stackSize()
        val indexKey = "stack[$stackSize],$fileId"
        setStackSize(stackSize-1)
        return IStorage.readFromMap(indexKey)!!.toInt()
    }

    private fun pushIndexToStack(index :Int){
        setStackSize(stackSize()+1)
        val stackSize = stackSize()
        val indexKey = "stack[$stackSize],$fileId"
        IStorage.writeToMap(indexKey,index.toString())
    }

    /**
    @return : a unique index that hasn't been used before or has been taken back in takeUniqueIndex
     */
    override fun getUniqueIndex() : Int {
        val stackSize = stackSize()
        val numOfUsedIndices = numOfUsedIndices()
        return if(stackSize == 0) {
            setNumOfUsedIndices(numOfUsedIndices+1)
            numOfUsedIndices()
        } else popIndexFromStack()
    }

    /**
    makes [index] available for use
     */
    override fun takeUniqueIndex(index : Int) {
        pushIndexToStack(index)
    }
}