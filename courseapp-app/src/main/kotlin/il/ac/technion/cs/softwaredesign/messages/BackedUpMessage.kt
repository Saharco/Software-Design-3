package il.ac.technion.cs.softwaredesign.messages

interface BackedUpMessage {

    var source : String?
    var destination : String?
    var type : MessageType?
    var pendingReadersNum: Long?
}