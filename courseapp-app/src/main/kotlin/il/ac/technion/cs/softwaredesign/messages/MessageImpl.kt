package il.ac.technion.cs.softwaredesign.messages

import il.ac.technion.cs.softwaredesign.IStorage
import il.ac.technion.cs.softwaredesign.constants.EConstants.*
import il.ac.technion.cs.softwaredesign.messages.MessageFields.*

import java.time.LocalDateTime


class MessageImpl(private val dataStore :IStorage,
                  private val messageId : Long,
                  private val media_: MediaType,
                  private val contents_:ByteArray) : Message {

    init{
        storeMessage()
    }

    private fun buildFieldKey(field: MessageFields, messageId: Long): String {
        return "${MESSAGE_FILE_ID.ordinal},$messageId,${field.ordinal}"
    }

    private fun storeMessage() {
        if(dataStore.readFromMap(buildFieldKey(ID,messageId))==null){
            dataStore.writeToMap(buildFieldKey(ID,messageId),messageId.toString())
            dataStore.writeToMap(buildFieldKey(MEDIA,messageId),media_.ordinal.toString())
            dataStore.writeToMap(buildFieldKey(CONTENTS,messageId), String(contents_,Charsets.UTF_8))
            dataStore.writeToMap(buildFieldKey(CREATED,messageId), LocalDateTime.now().toString())
        }
    }

    override val id: Long
        get() = dataStore.readFromMap(buildFieldKey(ID, messageId))!!.toLong()

    override val media: MediaType
        get() {
            val ordinal = dataStore.readFromMap(buildFieldKey(MEDIA, messageId))!!.toInt()
            return MediaType.values()[ordinal]
        }
//
    override val contents: ByteArray
        get() = dataStore.readFromMap(buildFieldKey(CONTENTS, messageId))!!.toByteArray(Charsets.UTF_8)

    override val created: LocalDateTime
        get() = LocalDateTime.parse(dataStore.readFromMap(buildFieldKey(CREATED, messageId))!!)

    override var received: LocalDateTime?
        get() {
            val result = dataStore.readFromMap(buildFieldKey(RECIEVED, messageId))
            return if(result==null) null else LocalDateTime.parse(result)
        }
        set(value) {
            dataStore.writeToMap(buildFieldKey(RECIEVED, messageId),value.toString())
        }
}