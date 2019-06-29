package fakes.messages

import il.ac.technion.cs.softwaredesign.ObjectSerializer
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.Message
import java.io.Serializable
import java.time.LocalDateTime

/**
 * Implementation for the [Message] interface.
 *
 * Added properties:
 * [usersCount] - amount of users the message is pending for. 0 by default
 * [sender] - source of this message. Null by default
 */
class MessageImpl(override val id: Long, override val media: MediaType, override val contents: ByteArray,
                  override val created: LocalDateTime, var usersCount: Long = 0,
                  override var received: LocalDateTime? = null, var sender: String? = null) : Message, Serializable {

    companion object {

        private const val serialVersionUID = 43L
        private val charset = Charsets.UTF_8

        /**
         * @param string: *serialized* string
         * @return a MessageImpl instance that was deserialized from [string]
         */
        fun deserialize(string: String): MessageImpl {
            return ObjectSerializer.deserialize(string)
        }
    }

    /**
     * Copy constructor:
     * @param msg: message to copy
     * @return copy of [msg]
     */
    constructor(msg: MessageImpl)
            : this(msg.id, msg.media, msg.contents, msg.created, msg.usersCount, msg.received, msg.sender)

    /**
     * @return string that can be deserialized back into a MessageImpl instance
     */
    fun serialize(): String {
        return ObjectSerializer.serialize(this)
    }

    /**
     * @return true if there are no more users that this message is pending for, false otherwise
     */
    fun isDonePending(): Boolean {
        return usersCount == 0L
    }
}