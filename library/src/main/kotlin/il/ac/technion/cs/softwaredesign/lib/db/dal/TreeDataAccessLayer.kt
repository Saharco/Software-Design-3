package il.ac.technion.cs.softwaredesign.lib.db.dal

import il.ac.technion.cs.softwaredesign.lib.utils.BalancedStorageTree
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import java.io.Serializable
import java.util.concurrent.CompletableFuture

data class KeyPair<K : Comparable<K>>(private var first: K, private val second: K) : Comparable<KeyPair<K>>, Serializable{

    fun setFirst(value: K) {
        this.first = value
    }

    fun getFirst() = first

    fun getSecond() = second

    override fun compareTo(other: KeyPair<K>): Int {
        val firstCompare :Int = this.first.compareTo(other.first)
        return if (firstCompare != 0) firstCompare.times(-1) else this.second.compareTo(other.second)
    }
}

class TreeDataAccessLayer<T>(storage: CompletableFuture<SecureStorage>): DataAccessLayer<KeyPair<Long>, T> {
    private val tree = BalancedStorageTree<KeyPair<Long>, T>(storage)

    override fun readObject(id: KeyPair<Long>, keys: List<String>): CompletableFuture<T?> {
        return tree.search(id)
    }

    override fun writeObject(id: KeyPair<Long>, value: T): CompletableFuture<Unit> {
        return tree.insert(id,value).thenApply { }
    }

    override fun deleteObject(id: KeyPair<Long>, keys: List<String>): CompletableFuture<T?> {
        return tree.search(id).thenCompose { v -> tree.delete(id).thenApply { v } }
    }

    fun getSequence(n: Int): CompletableFuture<Sequence<Pair<KeyPair<Long>, T>>> {
        return tree.asList(n).thenApply { it.asSequence() }
    }
}