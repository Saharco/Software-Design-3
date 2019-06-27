package il.ac.technion.cs.softwaredesign.lib.mock

import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory
import java.util.concurrent.CompletableFuture

class SecureStorageFactoryFake : SecureStorageFactory {

    private val storageLayers = HashMap<String, SecureStorage>()

    override fun open(name: ByteArray): CompletableFuture<SecureStorage> {
        val key = String(name)
//        Thread.sleep(100L*storageLayers.size)
        return if (storageLayers.containsKey(key)) CompletableFuture.completedFuture(storageLayers[key]!!)
        else {
            storageLayers[key] = SecureStorageFake()
            CompletableFuture.completedFuture(storageLayers[key]!!)
        }
    }
}