package il.ac.technion.cs.softwaredesign

import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory
import java.util.concurrent.CompletableFuture


class CFakeSecureStorageFactory : SecureStorageFactory {
    private val storageNameToIdMap = mutableMapOf<String,Int>()
    private var numOfStorages = 0

    override fun open(name: ByteArray): CompletableFuture<SecureStorage> {
        return CompletableFuture.completedFuture(Unit).thenApply{
            val strName = String(name,Charsets.UTF_8)
            if(storageNameToIdMap[strName]==null){
                numOfStorages++
                storageNameToIdMap[strName] = numOfStorages
            }
            Thread.sleep(100*numOfStorages.toLong())
            CFakeSecureStorage(numOfStorages)
        }
    }
}

