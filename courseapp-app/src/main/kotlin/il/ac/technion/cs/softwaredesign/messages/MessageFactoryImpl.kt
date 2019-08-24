package il.ac.technion.cs.softwaredesign.messages

import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.IStorage
import il.ac.technion.cs.softwaredesign.constants.EConstants.*
import java.util.concurrent.CompletableFuture



class MessageFactoryImpl @Inject constructor(private val dataStore : IStorage) : MessageFactory{

    private fun getNextId(): Long{
        return dataStore.incCounter(MESSAGES_COUNTER.ordinal)
    }

    override fun create(media: MediaType, contents: ByteArray): CompletableFuture<Message> {
        val messageId = getNextId()
        return CompletableFuture.completedFuture(MessageImpl(dataStore,messageId,media,contents))
    }
}