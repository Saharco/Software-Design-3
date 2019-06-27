package il.ac.technion.cs.softwaredesign.lib.utils

import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture

class StorageList(type: String, private val storage: CompletableFuture<SecureStorage>) {

    companion object {
        const val KEY_SIZE = "list size"
    }

    private val tree = BalancedStorageTree<Int, String>(storage, type = type)

    var size: CompletableFuture<Int> = storage
            .thenCompose { s -> s.read(KEY_SIZE.toByteArray()) }
            .thenApply { bytes -> if (bytes !== null) ByteBuffer.wrap(bytes).int else -1 }
        set(value) {
            field = value
                    .thenCompose { size -> storage.thenApply { Pair(size, it) } }
                    .thenCompose { (size, s) ->
                        s.write(KEY_SIZE.toByteArray(), size.bytes()).thenApply { size }
                    }
        }

    fun insert(element: String): CompletableFuture<Unit> {
        return size.thenCompose { s ->
            tree.insert(s, element).thenApply { Pair(it, s) }
        }.thenApply { (inserted, oldSize) ->
            if (inserted) {
                size = CompletableFuture.completedFuture(oldSize + 1)
            }
        }
    }

    fun get(index: Int): CompletableFuture<String?> {
        return tree.asSequence()
                .thenApply { sequence -> sequence.elementAt(index).second }
    }

    fun remove(index: Int): CompletableFuture<Unit> {
        return size.thenCompose { s ->
            tree.delete(index).thenApply { Pair(it, s) }
        }.thenApply { (deleted, oldSize) ->
            if (deleted) {
                size = CompletableFuture.completedFuture(oldSize - 1)
            }
        }
    }

    fun asSequence(): CompletableFuture<Sequence<Pair<Int, String>>> {
        return tree.asSequence()
    }
}