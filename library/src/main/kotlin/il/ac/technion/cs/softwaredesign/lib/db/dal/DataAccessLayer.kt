package il.ac.technion.cs.softwaredesign.lib.db.dal

import java.util.concurrent.CompletableFuture

interface DataAccessLayer<K, T> {
    fun readObject(id: K, keys: List<String>): CompletableFuture<T?>
    fun writeObject(id: K, value: T): CompletableFuture<Unit>
    fun deleteObject(id: K, keys: List<String>): CompletableFuture<T?>
}