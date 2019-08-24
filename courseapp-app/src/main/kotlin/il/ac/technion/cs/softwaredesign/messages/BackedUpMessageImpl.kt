package il.ac.technion.cs.softwaredesign.messages

import il.ac.technion.cs.softwaredesign.IStorage
import il.ac.technion.cs.softwaredesign.constants.EConstants.*
import il.ac.technion.cs.softwaredesign.messages.MessageFields.*

class BackedUpMessageImpl(private val dataStore : IStorage,
                          private val messageId : Long) : BackedUpMessage {

    private val fileId =  MESSAGE_FILE_ID.ordinal

    override var source: String?
        get() = dataStore.readFromMap(buildFieldKey(SOURCE,messageId))
        set(value) {
            if(value!=null) dataStore.writeToMap(buildFieldKey(SOURCE,messageId),value)
        }
    override var destination: String?
        get() = dataStore.readFromMap(buildFieldKey(DESTINATION,messageId))
        set(value) {
            if(value!=null) dataStore.writeToMap(buildFieldKey(DESTINATION,messageId),value)
        }
    override var type: MessageType?
        get() {
            val key = buildFieldKey(TYPE,messageId)
            val type = dataStore.readFromMap((key))?.toInt() ?: return null
            return MessageType.values()[type]
        }
        set(value) {
            if(value!=null) dataStore.writeToMap(buildFieldKey(TYPE,messageId),value.ordinal.toString())
        }
    override var pendingReadersNum: Long?
        get() = dataStore.readFromMap(buildFieldKey(NUM_OF_PENDING_USERS,messageId))?.toLong()
        set(value) {
            if(value!=null) dataStore.writeToMap(buildFieldKey(NUM_OF_PENDING_USERS,messageId),value.toString())
        }


    private fun buildFieldKey(field: MessageFields, messageId: Long) : String{
        return "$fileId,$messageId,${field.ordinal}"
    }
}