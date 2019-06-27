package fakes.messages

import com.google.inject.Inject
import fakes.library.utils.DatabaseMapper
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.Message
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

/**
 * Implementation for the [MessageFactory] interface.
 */
class MessageFactoryImpl @Inject constructor(dbMapper: DatabaseMapper) : MessageFactory {

    private val dbName = "course_app_database"
    private val messagesRoot = dbMapper.getDatabase(dbName)
            .collection("all_messages")
            .document("metadata")

    override fun create(media: MediaType, contents: ByteArray): CompletableFuture<Message> {
        return messagesRoot.read("constructed_messages_count")
                .thenApply { serialId ->
                    serialId?.toLong()?.plus(1) ?: 1
                }.thenCompose { serialId ->
                    messagesRoot.set(Pair("constructed_messages_count", serialId.toString()))
                            .update()
                            .thenApply { serialId }
                }.thenApply { serialId ->
                    MessageImpl(serialId, media, contents, LocalDateTime.now())
                }
    }
}