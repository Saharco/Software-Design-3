package il.ac.technion.cs.softwaredesign.lib.utils

import com.google.gson.reflect.TypeToken
import il.ac.technion.cs.softwaredesign.lib.db.dal.GsonInstance
import il.ac.technion.cs.softwaredesign.lib.utils.StorageTree.StorageNode.Companion.deserialize
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import java.io.*
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.collections.HashMap

class DefaultComparator<K: Comparable<K>> : Comparator<K> {
    override fun compare(o1: K, o2: K): Int = o1.compareTo(o2)
}

class BalancedStorageTree<K: Comparable<K>, V>(storage: CompletableFuture<SecureStorage>, comparator: Comparator<K> = DefaultComparator(), type: String = "") :
        StorageTree<K, V>(storage, comparator, true, type)

open class StorageTree<K: Comparable<K>, V>(private val storage: CompletableFuture<SecureStorage>, private val comparator: Comparator<K>, private val isBalancing: Boolean, private val type: String = "") {

    companion object {
        const val KEY_ROOT_NODE = "root node"
        const val KEY_MAX_ID = "max id"
    }

    /**
     * The root node of the tree
     */
    private var root: CompletableFuture<StorageNode<K, V>?> = deserialize(KEY_ROOT_NODE.toByteArray(), this)
        set(value) {
            field = value
                    .thenCompose { node -> storage.thenApply { Pair(node, it) } }
                    .thenCompose { (node, s) ->
                        s.write((type + KEY_ROOT_NODE).toByteArray(), node?.serialize() ?: ByteArray(0)).thenApply { node }
                    }
        }

    /**
     * Maximal id in the tree. Used to track adding new nodes
     */
    private var maxId: CompletableFuture<Int> = storage
            .thenCompose { s -> s.read((type + KEY_MAX_ID).toByteArray()) }
            .thenApply { bytes -> if (bytes !== null) ByteBuffer.wrap(bytes).int else -1 }
        set(value) {
            field = value
                    .thenCompose { id -> storage.thenApply { Pair(id, it) } }
                    .thenCompose { (id, s) ->
                        s.write((type + KEY_MAX_ID).toByteArray(), id.bytes()).thenApply { id }
                    }
        }

    private fun nextId(): CompletableFuture<Int> {
        maxId = maxId.thenApply { it.inc() }
        return maxId
    }

    private val cache: MutableMap<Int, StorageNode<K, V>> = HashMap()

    class StorageNode<K : Comparable<K>, V>(var key: K,
                                            var value: V,
                                            private var parentId: Int?,
                                            var id: Int,
                                            @Transient private var comparator: Comparator<K>,
                                            @Transient private var tree: StorageTree<K, V>) : Serializable {

        private var balance: Int = 0
        private var leftId: Int? = null
        private var rightId: Int? = null

        @Transient
        var storage: CompletableFuture<SecureStorage> = tree.storage

        companion object {
            private const val serialVersionUID = 42L

            fun <K : Comparable<K>, V> deserialize(bytes: ByteArray?, tree: StorageTree<K, V>): CompletableFuture<StorageNode<K, V>?> {
                if (bytes == null) return CompletableFuture.completedFuture(null)

                return tree.storage
                        .thenCompose { it.read(tree.type.toByteArray().plus(bytes)) }
                        .thenApply {
                            if (it != null) {
//                                val res = ObjectInputStream(BufferedInputStream(ByteArrayInputStream(it))).readObject() as StorageNode<K, V>
                                val gson = GsonInstance.instance
                                val type = object : TypeToken<Map<String, Any>>() {}.type
                                val map: Map<String, Any> = gson.fromJson(String(it), type)

                                val res = StorageNode(
                                        key = map["k"] as K,
                                        value = map["v"] as V,
                                        parentId = map["p"] as Int?,
                                        id = map["i"] as Int,
                                        comparator = tree.comparator,
                                        tree = tree
                                )

                                res.leftId = map["li"] as Int?
                                res.rightId = map["ri"] as Int?
                                println("Des Object: ${res.value}")
                                res.storage = tree.storage

                                res
                            } else null
                        }
            }
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }

        override fun equals(other: Any?): Boolean {
            return if (other is StorageNode<*, *>) id == other.id else false
        }

        // TODO Join
        private fun rebalance(n: StorageNode<K, V>): CompletableFuture<StorageNode<K, V>?> {

            return setBalance(n).thenCompose { n.left() }
                    .thenCompose { nnLeft -> n.right().thenApply { Pair(nnLeft, it) } }
                    .thenCompose { (nnLeft, nnRight) ->

                        if (n.balance == -2) {

                            if (nnLeft == null) {
                                rotateLeftThenRight(n)
                            } else {
                                nnLeft.left().thenCompose { nnLeftLeft -> nnLeft.right().thenApply { Pair(nnLeftLeft, it) } }
                                        .thenCompose { (nnLeftLeft, nnLeftRight) -> height(nnLeftLeft).thenApply { Pair(it, nnLeftRight) } }
                                        .thenCompose { (nnLeftLeftHeight, nnLeftRight) -> height(nnLeftRight).thenApply { Pair(nnLeftLeftHeight, it) } }
                                        .thenApply { (h1, h2) -> h1 >= h2 }
                                        .thenCompose { condition ->
                                            if (condition) rotateRight(n)
                                            else rotateLeftThenRight(n)
                                        }
                            }

                        } else if (n.balance == 2) {

                            if (nnRight == null) {
                                rotateRightThenLeft(n)
                            } else {
                                nnRight.right().thenCompose { nnRightRight -> nnRight.left().thenApply { Pair(nnRightRight, it) } }
                                        .thenCompose { (nnRightRight, nnRightLeft) -> height(nnRightRight).thenApply { Pair(it, nnRightLeft) } }
                                        .thenCompose { (nnRightRightHeight, nnRightLeft) -> height(nnRightLeft).thenApply { Pair(nnRightRightHeight, it) } }
                                        .thenApply { (h1, h2) -> h1 >= h2 }
                                        .thenCompose { condition ->
                                            if (condition) rotateLeft(n)
                                            else rotateRightThenLeft(n)
                                        }
                            }
                        } else {
                            CompletableFuture.completedFuture(n)
                        }
                    }
                    .thenCompose { nn ->
                        nn.parent().thenApply { Pair(nn, it) }
                    }
                    .thenCompose { (nn, parent) ->
                        if (parent != null) {
                            rebalance(parent)
                        } else {
                            // This is the root, make sure it's updated
                            CompletableFuture.completedFuture(nn)
                        }
                    }
        }

        private fun rotateLeft(a: StorageNode<K, V>): CompletableFuture<StorageNode<K, V>> {
            return a.right().thenApply { it!! }.thenCompose { b ->
                b.parentId = a.parentId
                a.rightId = b.leftId

                (if (a.rightId != null) {
                    a.right().thenApply { it!! }
                            .thenCompose { newARight ->
                                newARight.parentId = a.id
                                newARight.updateNode()
                            }
                            .thenApply { Pair(a, b) }
                } else CompletableFuture.completedFuture(Pair(a, b)))
            }.thenCompose { (a, b) ->
                b.leftId = a.id
                a.parentId = b.id

                if (b.parentId != null) {
                    b.parent()
                            .thenApply { it!! }
                            .thenApply { bParent ->
                                if (bParent.rightId == a.id) {
                                    bParent.rightId = b.id
                                } else {
                                    bParent.leftId = b.id
                                }
                                bParent
                            }
                            .thenCompose { bParent -> bParent.updateNode().thenApply { Pair(a, b) } }

                } else CompletableFuture.completedFuture(Pair(a, b))

            }.thenCompose { (a, b) -> setBalance(a, b).thenApply { b } }
        }

        private fun rotateRight(a: StorageNode<K, V>): CompletableFuture<StorageNode<K, V>> {
            return a.left().thenApply { it!! }.thenCompose { b ->
                b.parentId = a.parentId
                a.leftId = b.rightId

                (if (a.leftId != null) {
                    a.left().thenApply { it!! }
                            .thenCompose { newALeft ->
                                newALeft.parentId = a.id
                                newALeft.updateNode()
                            }
                            .thenApply { Pair(a, b) }
                } else CompletableFuture.completedFuture(Pair(a, b)))
            }.thenCompose { (a, b) ->
                b.rightId = a.id
                a.parentId = b.id

                if (b.parentId != null) {
                    b.parent()
                            .thenApply { it!! }
                            .thenApply { bParent ->
                                if (bParent.rightId == a.id) {
                                    bParent.rightId = b.id
                                } else {
                                    bParent.leftId = b.id
                                }
                                bParent
                            }
                            .thenCompose { bParent -> bParent.updateNode().thenApply { Pair(a, b) } }

                } else CompletableFuture.completedFuture(Pair(a, b))

            }.thenCompose { (a, b) -> setBalance(a, b).thenApply { b } }
        }

        private fun rotateLeftThenRight(n: StorageNode<K, V>): CompletableFuture<StorageNode<K, V>> {
            return n.left()
                    .thenCompose { left -> rotateLeft(left!!) }
                    .thenCompose { rotated ->
                        n.leftId = rotated.id
                        rotateRight(n)
                    }
        }

        private fun rotateRightThenLeft(n: StorageNode<K, V>): CompletableFuture<StorageNode<K, V>> {
            return n.right()
                    .thenCompose { right -> rotateRight(right!!) }
                    .thenCompose { rotated ->
                        n.rightId = rotated.id
                        rotateLeft(n)
                    }
        }

        private fun height(n: StorageNode<K, V>?): CompletableFuture<Int> {
            if (n == null) return CompletableFuture.completedFuture(-1)

            return n.right().thenCompose { r -> n.left().thenApply { Pair(r, it) } }
                    .thenCompose { (right, left) ->
                        height(right).thenApply { Pair(left, it) }
                    }
                    .thenCompose { (left, rHeight) ->
                        height(left).thenApply { Pair(it, rHeight) }
                    }
                    .thenApply { (lHeight, rHeight) ->
                        1 + Math.max(lHeight, rHeight)
                    }
        }

        private fun setBalance(vararg nodes: StorageNode<K, V>): CompletableFuture<Unit> {
            return CompletableFuture.allOf(
                    *nodes.map { n ->
                        n.right().thenCompose { r -> n.left().thenApply { Pair(r, it) } }
                                .thenCompose { (right, left) ->
                                    height(right).thenCompose { rHeight -> height(left).thenApply { Pair(rHeight, it) } }
                                            .thenCompose { (rHeight, lHeight) ->
                                                n.balance = rHeight - lHeight
                                                n.updateNode()
                                            }
                                }
                    }.toTypedArray()
            ).thenApply { }
        }

        private fun parent(): CompletableFuture<StorageNode<K, V>?> {
            if (this.parentId == null) return CompletableFuture.completedFuture(null)
            if (tree.cache.containsKey(this.parentId!!)) {
                return CompletableFuture.completedFuture(tree.cache[this.parentId!!])
            }
            return deserialize(this.parentId?.bytes(), tree)
        }

        private fun left(): CompletableFuture<StorageNode<K, V>?> {
            if (this.leftId == null) return CompletableFuture.completedFuture(null)
            if (tree.cache.containsKey(this.leftId!!)) {
                return CompletableFuture.completedFuture(tree.cache[this.leftId!!])
            }
            return deserialize(this.leftId?.bytes(), tree)
        }

        private fun right(): CompletableFuture<StorageNode<K, V>?> {
            if (this.rightId == null) return CompletableFuture.completedFuture(null)
            if (tree.cache.containsKey(this.rightId!!)) {
                return CompletableFuture.completedFuture(tree.cache[this.rightId!!])
            }
            return deserialize(this.rightId?.bytes(), tree)
        }

        fun leftmost(): CompletableFuture<StorageNode<K, V>> {
            return left().thenCompose { left ->
                left?.leftmost() ?: CompletableFuture.completedFuture(this)
            }
        }

        fun search(key: K): CompletableFuture<StorageNode<K, V>?> {
            if (this.key == key) return CompletableFuture.completedFuture(this)

            val goLeft = this.key > key

            return if (goLeft) {
                left().thenCompose { left ->
                    left?.search(key) ?: CompletableFuture.completedFuture(null as StorageNode<K, V>?)
                }
            } else {
                right().thenCompose { right ->
                    right?.search(key) ?: CompletableFuture.completedFuture(null as StorageNode<K, V>?)
                }
            }
        }

        fun insert(key: K, value: V): CompletableFuture<Boolean> {
            if (this.key == key) return CompletableFuture.completedFuture(false)

            val goLeft = this.key > key
            val childFuture = if (goLeft) left() else right()

            return tree.nextId().thenCompose { nextId ->
                childFuture.thenCompose { child ->
                    if (child == null) {
                        // End of search path
                        val node = StorageNode(key, value, id, nextId, comparator, tree)

                        // Update node child
                        if (goLeft) leftId = node.id
                        else rightId = node.id

                        updateNode()
                                .thenCompose { node.updateNode() }
                                .thenCompose { node.parent().thenApply { it!! } }
                                .thenCompose { parent -> rebalance(parent) }
                                .thenApply { updated ->
                                    if (updated != null) {
                                        tree.root = CompletableFuture.completedFuture(updated)
                                    }
                                    true
                                }
                    } else {
                        child.insert(key, value)
                    }
                }
            }
        }

        private fun removeNode(): CompletableFuture<Unit> {
            if (leftId == null && rightId == null) {
                // Case 1: Node is leaf. simply detach it
                if (this.parentId != null) {
                    return parent()
                            .thenApply { it!! }
                            .thenCompose { parent ->
                                when (id) {
                                    parent.leftId -> parent.leftId = null
                                    parent.rightId -> parent.rightId = null
                                }
                                parent.updateNode()
                            }
                            .thenApply { tree.cache.remove(id) }
                            .thenDispose()
                } else {
                    // The node is the root of the parent
                    tree.root = CompletableFuture.completedFuture(null)
                }
            } else if (leftId == null) {
                // Case 2: Node has a right child only
                return right()
                        .thenApply { it!! }
                        .thenApply { right ->
                            right.parentId = this.parentId
                            right.updateNode()
                            right
                        }
                        .thenCompose { right -> parent().thenApply { Pair(right, it) } }
                        .thenCompose { (right, parent) ->
                            if (this.parentId != null) {
                                val p = parent!!
                                when (id) {
                                    p.leftId -> p.leftId = right.id
                                    p.rightId -> p.rightId = right.id
                                }
                                parent.updateNode()
                                        .thenApply { tree.cache.remove(id) }
                                        .thenDispose()
                            } else {
                                // The node is the root of the parent
                                tree.root = CompletableFuture.completedFuture(right)
                                CompletableFuture.completedFuture(Unit)
                            }
                        }
            } else if (rightId == null) {
                // Case 3: Node has a left child only
                return left()
                        .thenApply { it!! }
                        .thenApply { left ->
                            left.parentId = this.parentId
                            left.updateNode()
                            left
                        }
                        .thenCompose { left -> parent().thenApply { Pair(left, it) } }
                        .thenCompose { (left, parent) ->
                            if (this.parentId != null) {
                                val p = parent!!
                                when (id) {
                                    p.leftId -> p.leftId = left.id
                                    p.rightId -> p.rightId = left.id
                                }
                                parent.updateNode()
                                        .thenApply { tree.cache.remove(id) }
                                        .thenDispose()
                            } else {
                                // The node is the root of the parent
                                tree.root = CompletableFuture.completedFuture(left)
                                CompletableFuture.completedFuture(Unit)
                            }
                        }
            } else {
                // Case 4: Node has 2 children
                return next()
                        .thenCompose { next ->
                            val successor = next!!.second
                            this.key = successor.key
                            this.value = successor.value
                            updateNode()
                                    .thenCompose { successor.removeNode() }
                                    .thenApply { tree.cache.remove(successor.id) }
                        }
                        .thenDispose()
            }

            return CompletableFuture.completedFuture(null)
        }

        fun delete(delKey: K): CompletableFuture<Int?> {
            return if (this.key.compareTo(delKey) == 0) {
                removeNode()
                        .thenCompose { parent().thenApply { it!! } }
                        .thenCompose { parent -> if (parentId != null) rebalance(parent) else CompletableFuture.completedFuture(null as StorageNode<K, V>?) }
                        .thenApply { updated ->
                            if (updated != null) {
                                tree.root = CompletableFuture.completedFuture(updated)
                            }
                            id
                        }
            } else {
                val goLeft = this.key > delKey

                if (goLeft) {
                    left().thenCompose { left -> left?.delete(delKey) }
                } else {
                    right().thenCompose { right -> right?.delete(delKey) }
                }
            }
        }

        fun serialize(): ByteArray {
//            val os = ByteArrayOutputStream()
//            ObjectOutputStream(os).writeObject(this)
//            return os.toByteArray()
            val map: MutableMap<String, Any> = mutableMapOf(
                    "k" to key,
                    "v" to (value as Any),
                    "i" to id
            )
            if (parentId != null) map["p"] = parentId!!
            if (rightId != null) map["ri"] = rightId!!
            if (leftId != null) map["li"] = leftId!!

            return GsonInstance.instance.toJson(map).toByteArray()
        }

        fun updateNode(): CompletableFuture<Unit> {
            tree.cache[id] = this
            return tree.storage.thenCompose { s -> s.write(tree.type.toByteArray().plus(id.bytes()), serialize()) }
        }

        /**
         * Traverse the node sub-tree in-order (with respect to comparison order)
         */
        fun inorder(operator: (Pair<K, V>) -> Unit): CompletableFuture<Unit> {
            return left()
                    .thenApply { left -> left?.inorder(operator) ?: CompletableFuture.completedFuture(Unit) }
                    .thenApply { operator(Pair(key, value)) }
                    .thenCompose { right() }
                    .thenCompose { right -> right?.inorder(operator) ?: CompletableFuture.completedFuture(Unit) }
        }

        private fun nextRight(): CompletableFuture<Pair<K, StorageNode<K, V>>?> {
            val nId = this.id
            return parent().thenCompose { parent ->
                if (parentId == null || nId != parent?.rightId) {
                    if (parentId == null) {
                        CompletableFuture.completedFuture(null as Pair<K, StorageNode<K, V>>?)
                    } else {
                        CompletableFuture.completedFuture(Pair(parent!!.key, parent))
                    }
                } else {
                    parent.nextRight()
                }
            }
        }

        fun next(): CompletableFuture<Pair<K, StorageNode<K, V>>?> {
            return if (rightId != null) {
                // If we have a right child, the successor will be there.
                right()
                        .thenCompose { right -> right!!.leftmost() }
                        .thenApply { child -> Pair(child.key, child) }
            } else {
                // Otherwise, start searching up.
                if (parentId == null) CompletableFuture.completedFuture<Pair<K, StorageNode<K, V>>?>(null) else nextRight()
            }
        }
    }

    /**
     * Insert an element into the tree
     * @param key The element's key
     * @param value The element's value
     * @return true if the element was successfully inserted, or false if and element with the given key
     * already exists in the tree
     */
    fun insert(key: K, value: V): CompletableFuture<Boolean> {
        return root.thenCompose { r ->
            if (r == null) {
                nextId().thenApply { nextId ->
                    root = CompletableFuture.completedFuture(StorageNode(key, value, null, nextId, comparator, this))
                    true
                }
            } else {
                r.insert(key, value).thenApply { r.updateNode(); it }
            }
        }
    }

    /**
     * Search for an element inside the tree
     * @param key The key to look up
     * @return The value associated with the given key, or null, if the key does not exist in the tree
     */
    fun search(key: K): CompletableFuture<V?> {
        return root.thenCompose { r ->
            r?.search(key) ?: CompletableFuture.completedFuture(null as StorageNode<K, V>?)
        }.thenApply { node -> node?.value }
    }

    /**
     * Delete an element from the tree
     * @param delKey The key to look up and delete the element associated with it
     * @return true if the element was deleted successfully, or false if there is no element in the tree
     * associated with the given key
     */
    fun delete(delKey: K): CompletableFuture<Boolean> {
        return root.thenApply { r -> r?.delete(delKey) != null }
    }

    /**
     * Traverse the tree in order of sorting (from the bottom-leftmost node).
     */
    fun inorder(operator: (Pair<K, V>) -> Unit): CompletableFuture<Unit> {
        return root.thenCompose { r -> r?.inorder(operator) ?: CompletableFuture.completedFuture(Unit) }
    }

    /**
     * Return a sequence of key value pairs in the tree, in sorted order.
     */
    fun asSequence(): CompletableFuture<Sequence<Pair<K, V>>> {
        return root.thenApply { r ->
            var currentNode: StorageNode<K, V> = r ?: return@thenApply emptySequence<Pair<K, V>>()

            currentNode = currentNode.leftmost().join()
            var currentKey = currentNode.key
            var stop = false

            return@thenApply generateSequence {
                if (stop)
                    return@generateSequence null
                val ret = Pair(currentKey, currentNode.value)
                val pair = currentNode.next().join()
                if (pair != null) {
                    currentKey = pair.first
                    currentNode = pair.second
                } else {
                    stop = true
                }
                ret
            }
        }
    }

    fun asList(n: Int): CompletableFuture<List<Pair<K, V>>>{
        return CompletableFuture.completedFuture(LinkedList<Pair<K, V>>())
                .thenCompose { list ->
                    root.thenApply { Pair(it,list) }
                            .thenCompose { (currentNode, list) ->
                                    if(currentNode == null) {
                                        CompletableFuture.completedFuture(Pair(null as StorageNode<K,V>,list))
                                    }
                                    else {
                                        currentNode.leftmost().thenApply { Pair(it,list) }
                                    }
                                } .thenApply { (currentNode, list) -> Triple(currentNode,currentNode.key, list)}
                            }.thenCompose { (node, key, list) ->
                    var currentNode = node
                    var currentKey = key
                    list.add(Pair(currentKey, currentNode.value))
                    var futureList: CompletableFuture<List<Pair<K, V>>> = CompletableFuture.completedFuture(LinkedList<Pair<K, V>>())
                    for(i in 1 until n) {
                        futureList = currentNode.next().thenApply { pair ->
                            if(pair != null) {
                                currentNode = pair.second
                                list.add(Pair(pair.first, pair.second.value))
                            }
                        list}
                    }
                    futureList
                }
    }
}