package il.ac.technion.cs.softwaredesign

import com.google.inject.Inject
import java.lang.Math.max
import kotlin.collections.ArrayList


class CAvlTree @Inject constructor(private val IStorage: IStorage,
                                   private val pointerGenerator: IUniqueIndexGenerator,
                                   private val fileId: Int,
                                   private val comp: ArrayList<(String, String) -> Int>) : IDictionary{

    /**
     *
     * The CAvlTree is backed up in secureStorage and uses the provided cache
     * $fileId,size => tree size
     * $fileId,indices_stack => x
     * $fileId,root_index => root node index
     * all node keys prefixed by fileId
     * node i:
     * $fileId,i,1 => height
     * $fileId,i,2 => balance factor
     * $fileId,i,3 => node data
     * $fileId,i,4 => left child node index
     * $fileId,i,5 => right child node index
     * $fileId,i,6 => parent node index
     * $fileId,i,7 => key[0]
     * .
     * .
     * .
     * $fileId,i,($numOfElementsPerKey+6) => key[$numOfElementsPerKey-1]
     */
    companion object {
        //node fields
        const val HeightField = 1
        const val BalanceFactorField = 2
        const val DataField = 3
        const val LeftChildField = 4
        const val RightChildField = 5
        const val ParentField = 6
        const val FirstKeyElementField = 7
    }

    private val treeSizeKey = "$fileId,size"
    private val rootKey = "$fileId,root_index"
    private val numOfElementsPerKey = comp.size


    private fun treeSize():Int{
        return IStorage.readFromMap(treeSizeKey)?.toInt() ?: 0
    }

    private fun root():Int?{
        return if(IStorage.readFromMap(rootKey)=="N") null else IStorage.readFromMap(rootKey)?.toInt()
    }

    private fun buildStorageKeyOf(node_idx : Int, node_field_idx: Int):String{
        return "$fileId,$node_idx,$node_field_idx"
    }

    private fun setNodeField(node_idx : Int, value : String, field : Int){
        val storageKey = buildStorageKeyOf(node_idx, field)
        IStorage.writeToMap(storageKey,value)
    }

    private fun getNodeField(node_idx: Int , field : Int): String? {
        val storageKey = buildStorageKeyOf(node_idx, field)
        return IStorage.readFromMap(storageKey)
    }

    private fun setRightChild(parent: Int, node:Int?){
        val rightChild = node?.toString() ?: "N"
        setNodeField(parent,rightChild, RightChildField)
    }
    private fun setLeftChild(parent: Int, node:Int?){
        val leftChild = node?.toString() ?: "N"
        setNodeField(parent,leftChild, LeftChildField)
    }
    private fun setParent(node:Int,parent:Int?){
        val p = parent?.toString() ?: "N"
        setNodeField(node,p, ParentField)
    }

    private fun setRoot(node:Int?){
        val r = node?.toString() ?: "N"
        IStorage.writeToMap("$fileId,root_index",r)
        if(node!=null) setParent(node,null)
    }

    private fun getRightChild(node:Int):Int?{
        val right = getNodeField(node, RightChildField)
        return if(right=="N") null else right?.toInt()
    }

    private fun getLeftChild(node:Int):Int?{
        val left = getNodeField(node, LeftChildField)
        return if(left=="N") null else left?.toInt()
    }

    private fun getParent(node:Int):Int?{
        val parent = getNodeField(node, ParentField)
        return if(parent=="N") null else parent?.toInt()
    }

    private fun getHeight(node:Int?):Int{
        //should be called on existing node or null
        if(node == null) return -1
        return getNodeField(node, HeightField)!!.toInt()
    }

    private fun getKeys(node:Int): ArrayList<String>{
        val list = arrayListOf<String>()
        for(i in 0 until numOfElementsPerKey){
            val storageKey = buildStorageKeyOf(node, FirstKeyElementField+i)
            val keyElement = IStorage.readFromMap(storageKey) ?: break
            list.add(keyElement)
        }
        return list
    }

    private fun getAllKeysRecursive(node : Int?, list: ArrayList<String>){
        if(node == null) return
        getAllKeysRecursive(getLeftChild(node),list)
        list.addAll(getKeys(node))
        getAllKeysRecursive(getRightChild(node),list)
    }

    /**
     * @return a list of the keys in the tree in ascending order
     */
    override fun getAllKeysInOrder(): ArrayList<String>{
        val list = ArrayList<String>()
        val root = root() ?: return list
        getAllKeysRecursive(root,list)
        return list
    }

    /**
     * @returns the tree size
     */
    override fun getSize() : Int {
        return treeSize()
    }

    /**
     * inserts a new node at [key] with [data], in case the key exists the data is updated
     */
    override fun insert(key: ArrayList<String>, data: String){
        var currNode  = root()
        val root = root()
        if(root == null){
            val newNodeIndex = pointerGenerator.getUniqueIndex()
            insertNode(newNodeIndex,key, data,null)
            setRoot(newNodeIndex)
            return
        }
        while(true){
            val compResult = lazyCompareKeys(currNode!!,key)
            if(compResult == 0){
                setNodeField(currNode,data, DataField)
                return
            } else if(compResult > 0){
                val leftChild = getLeftChild(currNode)
                if(leftChild == null){
                    val nodeIndex = pointerGenerator.getUniqueIndex()
                    setLeftChild(currNode,nodeIndex)
                    insertNode(nodeIndex,key,data,currNode)
                    return
                } else currNode = leftChild
            } else {
                val rightChild = getRightChild(currNode)
                if(rightChild == null){
                    val nodeIndex = pointerGenerator.getUniqueIndex()
                    setRightChild(currNode,nodeIndex)
                    insertNode(nodeIndex,key,data,currNode)
                    return
                } else currNode = rightChild
            }
        }
    }

    private fun updateTreeSize(size :Int){
        IStorage.writeToMap("$fileId,size",size.toString())
    }

    private fun insertNode(nodeIndex:Int,key:ArrayList<String>, data:String, parent : Int?): Int{
        setNodeField(nodeIndex,data, DataField)
        setLeftChild(nodeIndex,null)
        setRightChild(nodeIndex,null)
        setParent(nodeIndex,parent)
        setNodeField(nodeIndex,"0", HeightField)
        setNodeField(nodeIndex,"0", BalanceFactorField)
        for(i in 0 until numOfElementsPerKey){
            val storageKey = buildStorageKeyOf(nodeIndex, FirstKeyElementField+i)
            IStorage.writeToMap(storageKey,key[i])
        }
        updateTreeSize(treeSize()+1)
        val treeSize = treeSize()
        if(treeSize > 1 && parent!=null){
            if(treeSize<=2) updateParentsHeightsAndBalanceFactor(parent)
            if(treeSize>2) setRoot(rotateIfNeeded(parent))
        }
        return nodeIndex
    }

    private fun lazyCompareKeys(nodeIndex: Int, key:ArrayList<String>): Int{
        for(i in 0 until numOfElementsPerKey){
            val storageKey = buildStorageKeyOf(nodeIndex, FirstKeyElementField+i)
            val keyElement = IStorage.readFromMap(storageKey)
            val compResult = comp[i](keyElement!!,key[i])
            if (compResult!=0) return compResult
        }
        return 0
    }

    private fun rotateLeft(a : Int) :Int {
        // assumption: a has a right child
        val b = getRightChild(a)
        setParent(b!!,getParent(a))
        val bLeftChild = getLeftChild(b)
        setRightChild(a,bLeftChild)
        if(bLeftChild!=null){
            setParent(bLeftChild,a)
        }
        setLeftChild(b,a)
        setParent(a,b)
        val bParent = getParent(b)
        if(bParent!=null){
            if(a == getRightChild(bParent)){
                setRightChild(bParent,b)
            } else {
                setLeftChild(bParent,b)
            }
        }
        updateNodeHeightAndBalanceFactor(a,b)
        return b
    }

    private fun rotateRight(a : Int) :Int {
        //assumption: a has a left child
        val b = getLeftChild(a)
        setParent(b!!,getParent(a))
        val bRightChild = getRightChild(b)
        setLeftChild(a,bRightChild)
        if(bRightChild!=null){
            setParent(bRightChild,a)
        }
        setRightChild(b,a)
        setParent(a,b)
        val bParent = getParent(b)
        if(bParent!=null){
            if(a == getRightChild(bParent)){
                setRightChild(bParent,b)
            } else {
                setLeftChild(bParent,b)
            }
        }
        updateNodeHeightAndBalanceFactor(a,b)
        return b
    }

    private fun rotateLeftThenRight(node_idx: Int):Int{
        val node = rotateLeft(getLeftChild(node_idx)!!)
        setLeftChild(node_idx,node)
        setParent(node,node_idx)
        return rotateRight(node_idx)
    }

    private fun rotateRightThenLeft(node_idx: Int):Int{
        val node = rotateRight(getRightChild(node_idx)!!)
        setRightChild(node_idx,node)
        setParent(node,node_idx)
        return rotateLeft(node_idx)
    }

    private fun rotateIfNeeded(node_idx : Int):Int?{
        var currNode = node_idx
        updateNodeHeightAndBalanceFactor(node_idx)
        val balanceFactor = getNodeField(currNode, BalanceFactorField)!!.toInt()
        if(balanceFactor == -2) {
            val leftChild = getLeftChild(currNode)!!
            val llChild = getLeftChild(leftChild)
            val lrChild = getRightChild(leftChild)
            currNode = if(getHeight(llChild) >= getHeight(lrChild)){
                rotateRight(currNode)
            } else {
                rotateLeftThenRight(currNode)
            }
        } else if(balanceFactor == 2) {
            val rightChild = getRightChild(currNode)!!
            val rrChild = getRightChild(rightChild)
            val rlChild = getLeftChild(rightChild)
            currNode = if(getHeight(rrChild) >= getHeight(rlChild)){
                rotateLeft(currNode)
            } else {
                rotateRightThenLeft(currNode)
            }
        }
        val parent = getParent(currNode)
        return parent?.let { rotateIfNeeded(it) } ?: //setRoot(currNode)
        currNode
    }

    private fun updateNodeHeightAndBalanceFactor(vararg nodeArray : Int):Boolean{
        var isChanged = false
        for(parent_idx in nodeArray) {
            val leftChild = getLeftChild(parent_idx)
            val rightChild = getRightChild(parent_idx)
            val leftChildHeight: Int = getHeight(leftChild)
            val rightChildHeight: Int = getHeight(rightChild)

            val parentHeight = max(leftChildHeight, rightChildHeight) + 1
            val parentBalanceFactor: Int = rightChildHeight - leftChildHeight
            val parentOldBalanceFactor = getNodeField(parent_idx, BalanceFactorField)!!.toInt()
            val parentOldHeight = getHeight(parent_idx)

            if (parentBalanceFactor != parentOldBalanceFactor) {
                setNodeField(parent_idx, parentBalanceFactor.toString(), BalanceFactorField)
            }
            if (parentHeight != parentOldHeight) {
                setNodeField(parent_idx, parentHeight.toString(), HeightField)
                isChanged = true
            }
        }
        return isChanged
    }
    private fun updateParentsHeightsAndBalanceFactor(parent_idx : Int) {
        val changedHeight = updateNodeHeightAndBalanceFactor(parent_idx)
        if(!changedHeight) return
        if(parent_idx == root()) return
        updateParentsHeightsAndBalanceFactor(getParent(parent_idx)!!)
    }

    private fun getSuccessor(node:Int):Int? {
        var n = getRightChild(node)
        if(n==null) return n
        var leftChild = getLeftChild(n)
        while(leftChild!=null) {
            n = leftChild
            leftChild = getLeftChild(n)
        }
        return n
    }

    private fun copyNode(dst:Int,src:Int){
        val data = getNodeField(src, DataField)!!
        setNodeField(dst,data, DataField)
        val keyList = getKeys(src)
        for(i in 0 until numOfElementsPerKey){
            val storageKey = buildStorageKeyOf(dst, FirstKeyElementField+i)
            IStorage.writeToMap(storageKey,keyList[i])
        }
    }



    private fun getDataSortedByKeysInDescendingOrderRec(node:Int?, requiredKeysNum:Int, list:ArrayList<String>){
        val num = requiredKeysNum
        if(node == null || num<=0) return
        getDataSortedByKeysInDescendingOrderRec(getRightChild(node),num,list)
        if(list.size < num)  list.add(getNodeField(node, DataField)!!)
        getDataSortedByKeysInDescendingOrderRec(getLeftChild(node),num,list)
    }

    /**
     * @return list of the largest [n] elements in tree sorted by keys, in case the tree has less than [n] elements
     * a list with less than [n] elements is returned
     */
    override fun getDataSortedByKeysInDescendingOrder(n:Int): ArrayList<String>{
        val list = ArrayList<String>()
        getDataSortedByKeysInDescendingOrderRec(root(),n,list)
        return list
    }

    /**
     * deletes the node with [key] if exists
     */
    override fun delete(key:ArrayList<String>){
        val root = root() ?: return
        setRoot(deleteRecursive(root,key))
    }


    private fun deleteRecursive(node: Int?,key:ArrayList<String>):Int? {
        var currNode = node
        if (currNode == null) return currNode
        val compResult = lazyCompareKeys(currNode, key)
        when {
            compResult > 0 -> {
                val left =  deleteRecursive(getLeftChild(currNode), key)
                setLeftChild(currNode,left)
                if(left!=null) setParent(left,currNode)
            }
            compResult < 0 -> {
                val right =  deleteRecursive(getRightChild(currNode), key)
                setRightChild(currNode,right)
                if(right!=null) setParent(right,currNode)
            }
            else -> {
                updateTreeSize(treeSize() - 1)
                val leftChild = getLeftChild(currNode)
                val rightChild = getRightChild(currNode)
                if(leftChild==null || rightChild==null){
                    pointerGenerator.takeUniqueIndex(currNode)
                }
                if (leftChild == null) currNode = rightChild
                else if (rightChild == null) {currNode = leftChild}
                else {
                    val successor = getSuccessor(currNode)!!
                    copyNode(currNode, successor)
                    updateTreeSize(treeSize() + 1)
                    val right = deleteRecursive(getRightChild(currNode), getKeys(successor))
                    setRightChild(currNode, right)
                    if(right!=null) setParent(right,currNode)
                }
            }
        }
        if (currNode == null) return null
        updateNodeHeightAndBalanceFactor(currNode)
        val balance = getNodeField(currNode, BalanceFactorField)!!.toInt()
        if (balance > 1)
        {
            val b = getNodeField(getRightChild(currNode)!!, BalanceFactorField)!!.toInt()
            if (b >= 0)
            {
                currNode = rotateLeft(currNode)
            }
            else
            {
                val node = rotateRight(getRightChild(currNode)!!)
                setRightChild(currNode,node)
                setParent(node,currNode)
                currNode =  rotateLeft(currNode)
            }

        }else if (balance < -1)
        {
            val b = getNodeField(getLeftChild(currNode)!!, BalanceFactorField)!!.toInt()
            if(b<=0){
                currNode = rotateRight(currNode)
            }else {
                val node = rotateLeft(getLeftChild(currNode)!!)
                setLeftChild(currNode,node)
                setParent(node,currNode)
                currNode = rotateRight(currNode)
            }

        }
        return currNode
    }

}