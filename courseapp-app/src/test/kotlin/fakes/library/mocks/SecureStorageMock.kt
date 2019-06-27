package fakes.library.mocks

import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import io.github.vjames19.futures.jdk8.Future
import java.util.concurrent.CompletableFuture

class SecureStorageMock : SecureStorage {

    private val db = mutableMapOf<String, String>()
    private val charset = charset("UTF-8")

    override fun read(key: ByteArray): CompletableFuture<ByteArray?> {
        val value =  db[key.toString(charset)]?.toByteArray()
        Thread.sleep(value?.size?.toLong() ?: 0)
        return CompletableFuture.supplyAsync { value }
    }

    override fun write(key: ByteArray, value: ByteArray): CompletableFuture<Unit> {
        return CompletableFuture.supplyAsync { db[key.toString(charset)] = value.toString(charset) }
    }
}