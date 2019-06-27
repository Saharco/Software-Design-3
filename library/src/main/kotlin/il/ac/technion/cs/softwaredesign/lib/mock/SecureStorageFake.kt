package il.ac.technion.cs.softwaredesign.lib.mock

import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import java.util.HashMap
import java.util.concurrent.CompletableFuture

class ByteArrayKey(private val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean =
            this === other || other is ByteArrayKey && this.bytes contentEquals other.bytes

    override fun hashCode(): Int = bytes.contentHashCode()
    override fun toString(): String = bytes.contentToString()
}

class SecureStorageFake : SecureStorage {

    private val storageMap = HashMap<ByteArrayKey, ByteArray>()
    override fun read(key: ByteArray): CompletableFuture<ByteArray?> {
        val value = storageMap.get(key = ByteArrayKey(key))
        if (value != null) {
            Thread.sleep(value.size.toLong())
//            println("Read ${String(key)}")
            println("time: ${value.size.toLong()}")
//            println(String(key))
        }
        return CompletableFuture.completedFuture(value)
    }

    override fun write(key: ByteArray, value: ByteArray): CompletableFuture<Unit> {
        storageMap.put(ByteArrayKey(key), value)
        return CompletableFuture.completedFuture(null)
    }
}

