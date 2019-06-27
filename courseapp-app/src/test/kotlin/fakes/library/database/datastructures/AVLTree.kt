import com.google.gson.Gson
import com.google.gson.JsonParser
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import kotlin.math.max

var charset = Charsets.UTF_8

/**
 * Updates a tree - wraps insertion and (possibly) deletion in one operation
 *
 * @param storage: the storage where the tree is stored
 * @param value: the new value of the stored node
 * @param newPrimaryKey: the new primary key of the node
 * @param oldPrimaryKey: the previous primary key, as currently stored in the storage
 * @param secondaryKey: the secondary key of the node
 * @param isDelete: if true - this is a delete operation (no re-insertion made)
 */
fun updateTree(storage: SecureStorage, value: String, newPrimaryKey: Int, oldPrimaryKey: Int,
               secondaryKey: Int, isDelete: Boolean = false) {
    val tree = AVLTree(storage)
    val prevKey = generateKey(oldPrimaryKey, secondaryKey)
    tree.delete(prevKey)
    if (!isDelete) {
        val newKey = generateKey(newPrimaryKey, secondaryKey)
        tree.insert(newKey, value.toByteArray(charset))
    }
}

/**
 * Collects the highest-valued elements in the tree
 *
 * @param storage: the storage where the tree is stored
 * @param k: desired amount of top elements to query from the tree
 * @return a list of the [k] highest-valued nodes' values, according to the comparator.
 *  If there are less than [k] nodes in the tree: that many nodes are returned
 */
fun treeTopK(storage: SecureStorage, k: Int = 10): List<String> {
    val tree = AVLTree(storage)
    return tree.topKTree(k)
}

/**
 *  Generates a key from a primary key & secondary key
 */
private fun generateKey(primaryKey: Int, secondaryKey: Int) =
        "$primaryKey/$secondaryKey".toByteArray(charset)

/**
 * Compares two keys. The keys are compared such that:
 * [key1] > [key2] if its primary key is bigger, or its secondary key is smaller.
 * [key2] > [key1] if its primary key is bigger, or its secondary key is smaller.
 * A pair of keys where none of these conditions are met are considered to be equal
 *
 * @return a positive number if [key1] > [key2],
 *  negative number if [key2] > [key1]
 *  0 otherwise.
 */
private fun compareKeys(key1: ByteArray, key2: ByteArray): Int {
    val key1String = key1.toString(charset)
    val key2String = key2.toString(charset)
    val key1separatorIndex = key1String.indexOf('/')
    val key2separatorIndex = key2String.indexOf('/')

    val primary1 = key1String.substring(0, key1separatorIndex).toInt()
    val secondary1 = key1String.substring(key1separatorIndex + 1).toInt()
    val primary2 = key2String.substring(0, key2separatorIndex).toInt()
    val secondary2 = key2String.substring(key2separatorIndex + 1).toInt()
    if (primary1 > primary2) {
        return 1
    } else if (primary1 < primary2) {
        return -1
    }
    if (secondary1 < secondary2)
        return 1
    else if (secondary2 < secondary1)
        return -1
    return 0
}

/**
 * Implementation of an AVL tree. Nodes are stored externally in a given storage.
 * @param storage - the SecureStorage instance on which to store the tree
 */
class AVLTree(private val storage: SecureStorage) {
    private val gson = Gson()
    private var root: AVLNode? = null

    /**
     * a class that represents a node.
     * the next nodes in the tree (left and right) can be retrieved through the corresponding keys that are
     * stored in this node.
     * @param storage the SecureStorage instance to store the node
     * @param key the node's key
     * @param value the node's value
     * @param left the node's left key
     * @param right the node's left key
     * @param height the node's height
     */
    class AVLNode(var storage: SecureStorage,
                  var key: ByteArray, var value: ByteArray,
                  var left: ByteArray? = null, var right: ByteArray? = null,
                  var height: Int = 0) {

        private val gson = Gson()

        override fun toString(): String {
            return "key is: ${key.toString(charset)} and value is ${value.toString(charset)}"
        }

        /**
         * saves the node in the storage
         */
        fun save() {
            val collection = ArrayList<Any?>()
            collection.add(key)
            collection.add(value)
            collection.add(left)
            collection.add(right)
            collection.add(height)
            val json = gson.toJson(collection)
            storage.write(key, json.toByteArray(charset)).get()
        }

        companion object {
            /**
             * a static method to read a node from the storage given it's key
             * @param storage the securestorage instance to read from
             * @param gson the Gson object used to parse the node
             * @param key the node's key.
             */
            fun read(storage: SecureStorage, gson: Gson, key: ByteArray?): AVLNode? {
                if (key == null) {
                    return null
                }
                val storageResult = storage.read(key).get() ?: return null
                val parser = JsonParser()
                val array = parser.parse(storageResult.toString(charset)).asJsonArray
                val readKey = gson.fromJson(array.get(0), ByteArray::class.java)
                val value = gson.fromJson(array.get(1), ByteArray::class.java)
                val left = gson.fromJson(array.get(2), ByteArray::class.java)
                val right = gson.fromJson(array.get(3), ByteArray::class.java)
                val height = gson.fromJson(array.get(4), Int::class.java)
                return AVLNode(storage, readKey, value, left, right, height)
            }
        }

        /**
         * appends the top k elements in the node's subtree based on their keys
         * @param list the list to append the elements to
         */
        fun topK(list: MutableList<String>, k: Int) {
            if (list.size == k) return
            read(storage, gson, right)?.topK(list, k)
            if (list.size == k) return
            list.add(value.toString(charset))
            if (list.size == k) return
            read(storage, gson, left)?.topK(list, k)
        }

    }

    /**
     * initializes the tree by reading a special key "node" which stores a root reference.
     */
    init {
        val rootJson = storage.read("root".toByteArray(charset)).get()
        if (rootJson == null) {
            root = null
        } else {
            root = AVLNode.read(storage, gson, rootJson)
        }
    }

    /**
     * returns the height of a given node.
     * @param nodeKey the key of the node
     */
    private fun height(nodeKey: ByteArray?): Int {
        if (nodeKey == null) {
            return -1
        }
        val node = AVLNode.read(storage, gson, nodeKey)
        return node?.height ?: -1
    }

    /**
     * Inserts a new key, value pair to the tree
     * @param key the node's key
     * @param value the node's value
     */
    fun insert(key: ByteArray, value: ByteArray) {
        root = AVLNode.read(storage, gson, insert(key, value, root))
        storage.write("root".toByteArray(charset), root!!.key).get()
    }

    /**
     * Inserts a new key,value pair to the subtree based at node.
     * @param key the node's key
     * @param value the node's value
     * @param node the subtree root node
     * @return the new root key of the subtree
     */
    private fun insert(key: ByteArray, value: ByteArray, node: AVLNode?): ByteArray? {
        if (node == null) {
            val newNode = AVLNode(storage, key, value)
            newNode.save()
            return newNode.key
        }
        val cmp = compareKeys(node.key, key)
        if (cmp > 0) {
            node.left = insert(key, value, AVLNode.read(storage, gson, node.left))
            node.height = max(height(node.left) + 1, node.height)
        } else if (cmp < 0) {
            node.right = insert(key, value, AVLNode.read(storage, gson, node.right))
            node.height = max(height(node.right) + 1, node.height)
        }
        node.save()
        return balance(node, key)
    }

    /**
     * balances a subtree based in node. returns the key of the new root node
     * @param node the subtree's root node
     * @param key the new key that was inserted into the tree
     */
    private fun balance(node: AVLNode, key: ByteArray): ByteArray? {
        //balance
        val balance = balanceFactor(node)

        // Left Left
        if (balance > 1 && compareKeys(node.left!!, key) > 0) {
            return rotateRight(node.key)
        }
        // Right Right
        if (balance < -1 && compareKeys(node.right!!, key) < 0) {
            return rotateLeft(node.key)
        }
        // Left Right
        if (balance > 1 && compareKeys(node.left!!, key) < 0) {
            node.left = rotateLeft(node.left!!)
            node.save()
            return rotateRight(node.key)
        }
        // Right Left
        if (balance < -1 && compareKeys(node.right!!, key) > 0) {
            node.right = rotateRight(node.right!!)
            node.save()
            return rotateLeft(node.key)
        }
        return node.key
    }

    /**
     * returns the balance factor of a given node by subtracting the left node's height by the right node's height.
     * @param node the node to calculate the balance factor
     */
    private fun balanceFactor(node: AVLNode): Int {
        return height(node.left) - height(node.right)
    }

    /**
     *  the rotations functions.
     */
    private fun rotateRight(node: ByteArray): ByteArray {
        val x = AVLNode.read(storage, gson, node) ?: return node
        val y: AVLNode = AVLNode.read(storage, gson, x.left) ?: return node
        x.left = y.right
        y.right = x.key
        x.height = 1 + max(height(x.left), height(x.right))
        x.save()
        y.height = 1 + max(height(y.left), height(y.right))
        y.save()
        return y.key
    }

    private fun rotateLeft(node: ByteArray): ByteArray {
        val x = AVLNode.read(storage, gson, node) ?: return node
        val y: AVLNode = AVLNode.read(storage, gson, x.right) ?: return node
        x.right = y.left
        y.left = x.key
        x.height = 1 + max(height(x.left), height(x.right))
        x.save()
        y.height = 1 + max(height(y.left), height(y.right))
        y.save()
        return y.key
    }

    /**
     * search's a value given it's key
     * @param key the node's key
     */
    fun search(key: ByteArray): ByteArray? {
        return search(root, key)
    }

    /**
     * search's a value given it's key in a given node's subtree
     * @param key the node's key
     * @param node the subtree's root
     */
    private fun search(node: AVLNode?, key: ByteArray): ByteArray? {
        if (node == null) {
            return null
        }
        var curr = node
        var result: ByteArray? = null
        while (curr != null) {
            val cmp = compareKeys(curr.key, key)
            if (cmp > 0) {
                curr = AVLNode.read(storage, gson, curr.left)
            } else if (cmp < 0) {
                curr = AVLNode.read(storage, gson, curr.right)
            } else {
                result = curr.value
                break
            }
        }
        return result
    }

    /**
     * deletes a node from the tree
     * @param key: the node's key
     */
    fun delete(key: ByteArray) {
        root = AVLNode.read(storage, gson, delete(root, key))
    }

    /**
     * deletes a node from a subtree.
     * @param node the subtree root
     * @param key the node's key
     */
    private fun delete(node: AVLNode?, key: ByteArray): ByteArray? {
        if (node == null) {
            return null
        }
        val cmp = compareKeys(node.key, key)
        when {
            cmp > 0 -> {
                node.left = delete(AVLNode.read(storage, gson, node.left), key)
                node.save()
                return node.key
            }
            cmp < 0 -> {
                node.right = delete(AVLNode.read(storage, gson, node.right), key)
                node.save()
                return node.key
            }
            else -> // found the node that needs to be deleted
                return when {
                    node.left == null -> node.right
                    node.right == null -> node.left
                    else -> {
                        // the node has 2 children
                        val result = replaceWithSuccessor(node)
                        result.key
                    }
                }
        }
    }

    private fun replaceWithSuccessor(node: AVLNode): AVLNode {
        var prev = node
        var curr: AVLNode = AVLNode.read(storage, gson, node.right)!!
        while (curr.left != null) {
            prev = curr
            curr = AVLNode.read(storage, gson, curr.left)!!
        }
        if (prev.key.contentEquals(node.key)) {
            prev.key = curr.key
            prev.value = curr.value
            prev.right = AVLNode.read(storage, gson, curr.right)?.key
            prev.save()
            updateHeights(root!!, prev.key)
            return prev
        }
        prev.left = curr.right
        prev.save()
        node.key = curr.key
        node.value = curr.value
        node.save()
        updateHeights(root!!, prev.key)
        return node
    }

    private fun updateHeights(curr: AVLNode, key: ByteArray): Int {
        val cmp = compareKeys(curr.key, key)
        val rightNode = AVLNode.read(storage, gson, curr.right)
        val leftNode = AVLNode.read(storage, gson, curr.left)
        when {
            cmp > 0 -> {
                if (rightNode != null) {
                    curr.height = max(rightNode.height, updateHeights(leftNode!!, key))
                } else {
                    curr.height = updateHeights(leftNode!!, key) + 1
                }
                curr.save()
                return curr.height
            }
            cmp < 0 -> {
                if (leftNode != null) {
                    curr.height = max(leftNode.height, updateHeights(rightNode!!, key))
                } else {
                    curr.height = updateHeights(rightNode!!, key) + 1
                }
                curr.save()
                return curr.height
            }
            else -> return max((AVLNode.read(storage, gson, curr.left)?.height ?: -1) + 1,
                    (AVLNode.read(storage, gson, curr.right)?.height ?: -1) + 1)
        }
    }

    /**
     * returns the top k elements in the list based on their keys
     * @param k
     */
    fun topKTree(k: Int): List<String> {
        val topKList = mutableListOf<String>()
        root?.topK(topKList, k)
        return topKList
    }
}