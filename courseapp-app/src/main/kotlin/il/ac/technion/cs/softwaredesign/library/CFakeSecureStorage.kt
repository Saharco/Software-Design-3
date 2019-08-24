package il.ac.technion.cs.softwaredesign

import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import java.util.concurrent.CompletableFuture

class CFakeSecureStorage @Inject constructor(private val id : Int): SecureStorage{
    private val storageMock = HashMap<CByteArrayWrapper,ByteArray>()

    private fun buildStorageKey(key:ByteArray):CByteArrayWrapper{
        val strKey =String(key,Charsets.UTF_8)
        val storageKey = "$id-$strKey"
        return CByteArrayWrapper(storageKey.toBytes())
    }

    override fun write(key: ByteArray, value: ByteArray): CompletableFuture<Unit> {
        return CompletableFuture.completedFuture(Unit).thenApply {
            val k = buildStorageKey(key)
            storageMock[k] = value
        }
    }

    override fun read(key: ByteArray): CompletableFuture<ByteArray?> {
        val k = buildStorageKey(key)
        return CompletableFuture.supplyAsync {
            val value = storageMock[k]
            if(value!=null){
                val length = String(value, Charsets.UTF_8).length
                //Thread.sleep(length.toLong())
            }
            storageMock[k]
        }
    }
}


