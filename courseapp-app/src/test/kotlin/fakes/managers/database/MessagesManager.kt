package fakes.managers.database

import il.ac.technion.cs.softwaredesign.CourseApp
import fakes.CourseAppFake
import il.ac.technion.cs.softwaredesign.ListenerCallback
import fakes.library.database.Database
import fakes.library.database.DocumentReference
import il.ac.technion.cs.softwaredesign.exceptions.InvalidTokenException
import il.ac.technion.cs.softwaredesign.exceptions.NoSuchEntityException
import il.ac.technion.cs.softwaredesign.exceptions.UserNotAuthorizedException
import fakes.messages.MessageImpl
import fakes.library.utils.DatabaseMapper
import il.ac.technion.cs.softwaredesign.messages.Message
import treeTopK
import updateTree
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

/**
 * Manages messages in the app: this class wraps messaging functionality
 * @see CourseAppFake
 * @see Database
 *
 * @param dbMapper: mapper object that contains the app's open databases
 *
 */
class MessagesManager(private val dbMapper: DatabaseMapper) {
    private val messageListeners = HashMap<String, MutableList<ListenerCallback>>()
    private val dbName = "course_app_database"

    private val messagesRoot = dbMapper.getDatabase(dbName)
            .collection("all_messages")
    private val messagesMetadataRoot = messagesRoot.document("metadata")

    private val usersRoot = dbMapper.getDatabase(dbName)
            .collection("all_users")
    private val tokensRoot = dbMapper.getDatabase(dbName)
            .collection("tokens")
    private val usersMetadataRoot = dbMapper.getDatabase(dbName)
            .collection("users_metadata")

    private val channelsRoot = dbMapper.getDatabase(dbName)
            .collection("all_channels")

    private val channelsByMessagesStorage = dbMapper.getStorage("channels_by_messages")

    /**
     * Adds a listener to this MessagesManager instance.
     *
     * This is an *update* command.
     *
     * The procedure is:
     *  adds listener to user of [token]'s list of callbacks,
     *  tries reading any new pending messages for the user -
     *  @see [tryReadingPendingMessages]
     *
     * @throws InvalidTokenException if the auth [token] is invalid.
     */
    fun addListener(token: String, callback: ListenerCallback): CompletableFuture<Unit> {
        return tokenToUser(token)
                .thenApply { tokenUsername ->
                    if (messageListeners[tokenUsername] == null)
                        messageListeners[tokenUsername] = mutableListOf(callback)
                    else
                        messageListeners[tokenUsername]?.add(callback)
                    tokenUsername
                }.thenCompose { tokenUsername ->
                    tryReadingPendingMessages(tokenUsername, callback)
                }
    }

    /**
     * Remove a listener from this Course App instance.
     *
     * @throws InvalidTokenException If the auth [token] is invalid.
     * @throws NoSuchEntityException If [callback] is not registered with this instance.
     */
    fun removeListener(token: String, callback: ListenerCallback): CompletableFuture<Unit> {
        return tokenToUser(token)
                .thenApply { tokenUsername ->
                    if (messageListeners[tokenUsername] == null || !messageListeners[tokenUsername]!!.contains(callback))
                        throw NoSuchEntityException("given callback is not registered ")
                    else {
                        messageListeners[tokenUsername]!!.remove(callback)
                        if (messageListeners[tokenUsername]?.size == 0)
                            messageListeners.remove(tokenUsername)
                    }
                }.thenApply { }
    }

    /**
     * Send a message to a channel from the user identified by [token]. Listeners will be notified, source will be
     * "[channel]@<user>" (including the leading `#`). So, if `gal` sent a message to `#236700`, the source will be
     * `#236700@gal`.
     *
     * This is an *update* command.
     *
     * Procedure is:
     *  verifies that the channel exists and username referenced by [token] is a member of it,
     *  updates [message] such that the number of users it is pending for will be the amount of users in the channel,
     *  updates [message]'s sender,
     *  uploads the message and increases channel message counters,
     *  invokes any necessary callbacks,
     *  updates query tree
     *
     * @throws InvalidTokenException If the auth [token] is invalid.
     * @throws NoSuchEntityException If [channel] does not exist.
     * @throws UserNotAuthorizedException If [token] identifies a user who is not a member of [channel].
     */
    fun channelSend(token: String, channel: String, message: Message): CompletableFuture<Unit> {
        val messageToSend = message as MessageImpl
        return tokenToUser(token)
                .thenCompose { tokenUsername ->
                    channelsRoot.document(channel)
                            .exists()
                            .thenApply { exists ->
                                if (!exists)
                                    throw NoSuchEntityException("channel does not exist")
                                tokenUsername
                            }
                }.thenCompose { tokenUsername ->
                    isMemberOfChannel(tokenUsername, channel)
                            .thenApply { isMember ->
                                if (!isMember)
                                    throw UserNotAuthorizedException("user is not a member of given channel")
                                else
                                    tokenUsername
                            }
                }.thenCompose { tokenUsername ->
                    channelsRoot.document(channel)
                            .read("users_count")
                            .thenApply { Pair(tokenUsername, it!!.toLong()) }
                }.thenCompose { (tokenUsername, channelUsersCount) ->
                    messageToSend.sender = "$channel@$tokenUsername"
                    messageToSend.usersCount = channelUsersCount
                    val messageDoc = messagesRoot.document("channel_messages")
                    uploadMessage(messageToSend, messageDoc)
                            .thenApply { messageToSend }
                }.thenCompose { uploadedMessage ->
                    invokeChannelCallbacks(channel, messageListeners.keys.toList(), uploadedMessage)
                }.thenCompose {
                    channelsRoot.document(channel)
                            .read("messages_count")
                            .thenApply { it?.toInt() ?: 0 }
                }.thenCompose { oldMsgsCount ->
                    channelsRoot.document(channel)
                            .set(Pair("messages_count", (oldMsgsCount + 1).toString()))
                            .update()
                            .thenApply { oldMsgsCount + 1 }
                }.thenCompose { newMsgsCount ->
                    channelsRoot.document(channel)
                            .read("creation_counter")
                            .thenApply { Pair(newMsgsCount, it?.toInt() ?: 0) }
                }.thenApply { (newMsgsCount, channelCreationCounter) ->
                    updateTree(channelsByMessagesStorage, channel, newMsgsCount, newMsgsCount - 1,
                            channelCreationCounter)
                }
    }

    /**
     * Sends a message to all users from an admin identified by [token]. Listeners will be notified, source is
     * "BROADCAST".
     *
     * This is an *update* command.
     *
     * Procedure is:
     *  verifies that the user referenced by [token] is an admin,
     *  updates [message] such that the number of users it is pending for will be the total amount of users in the app,
     *  updates [message]'s sender,
     *  uploads the message and increases broadcast & pending message counters,
     *  invokes any necessary callbacks
     *
     * @throws InvalidTokenException If the auth [token] is invalid.
     * @throws UserNotAuthorizedException If [token] does not identify an administrator.
     */
    fun broadcast(token: String, message: Message): CompletableFuture<Unit> {
        val messageToSend = message as MessageImpl
        return tokenToUser(token)
                .thenCompose { tokenUsername ->
                    isAdmin(tokenUsername)
                            .thenApply { isAdmin ->
                                if (!isAdmin)
                                    throw UserNotAuthorizedException("only admin may send broadcast messages")
                                tokenUsername
                            }
                }.thenCompose {
                    usersMetadataRoot.document("users_data")
                            .read("users_count")
                            .thenApply { it!!.toLong() }
                }.thenCompose { usersCount ->
                    messageToSend.sender = "BROADCAST"
                    messageToSend.usersCount = usersCount
                    val messageDoc = messagesRoot.document("broadcast_messages")
                    uploadMessage(messageToSend, messageDoc)
                            .thenApply { messageToSend }
                }.thenCompose { uploadedMessage ->
                    invokeBroadcastCallbacks(messageListeners.keys.toList(), uploadedMessage)
                }
    }

    /**
     * Sends a private message from the user identified by [token] to [user]. Listeners will be notified, source will be
     * "@<user>", where <user> is the user identified by [token]. So, if `gal` sent `matan` a message, that source will
     * be `@gal`.
     *
     * This is an *update* command.
     *
     * Procedure is:
     *  verifies that [user] exists,
     *  updates [message] such that it is pending for exactly one user,
     *  updates [message]'s sender,
     *  uploads the message and increases [user]'s pending messages & message counters,
     *  invokes any necessary callbacks for [user]
     *
     * @throws InvalidTokenException If the auth [token] is invalid.
     * @throws NoSuchEntityException If [user] does not exist.
     */
    fun privateSend(token: String, user: String, message: Message): CompletableFuture<Unit> {
        val messageToSend = message as MessageImpl
        return tokenToUser(token)
                .thenCompose { tokenUsername ->
                    usersRoot.document(user)
                            .exists()
                            .thenApply { exists ->
                                if (!exists)
                                    throw NoSuchEntityException("$user does not exist")
                                tokenUsername
                            }
                }.thenCompose { tokenUsername ->
                    messageToSend.sender = "@$tokenUsername"
                    messageToSend.usersCount = 1
                    val messageDoc = usersRoot.document(user)
                    uploadMessage(messageToSend, messageDoc)
                            .thenApply { messageToSend }
                }.thenCompose { uploadedMessage ->
                    invokeUserCallbacks(user, uploadedMessage)
                }
    }

    /**
     * Returns the message identified by [id], if it exists.
     *
     * This method is only useful for messages sent to channels.
     *
     * This is a *read* command.
     *
     * @throws InvalidTokenException If the auth [token] is invalid.
     * @throws NoSuchEntityException If [id] does not exist or is not a channel message.
     * @throws UserNotAuthorizedException If [id] identifies a message in a channel that the user identified by [token]
     * is not a member of.
     * @return The message identified by [id] along with its source.
     */
    fun fetchMessage(token: String, id: Long): CompletableFuture<Pair<String, Message>> {
        return tokenToUser(token)
                .thenCompose { tokenUsername ->
                    messagesRoot.document("channel_messages")
                            .readList("messages")
                            .thenApply { Pair(tokenUsername, deserializeToMessagesList(it)) }
                }.thenApply { (tokenUsername, channelMessages) ->
                    val foundMessages: List<MessageImpl> = channelMessages.asSequence()
                            .filter { it.id == id }
                            .toList()
                    if (foundMessages.isEmpty())
                        throw NoSuchEntityException("message with id $id does not exist")
                    val foundMessage: MessageImpl = foundMessages[0]
                    Pair(tokenUsername, foundMessage)
                }.thenCompose { (tokenUsername, foundMessage) ->
                    usersRoot.document(tokenUsername)
                            .readList("channels")
                            .thenApply { Pair(foundMessage, it) }
                }.thenApply { (foundMessage, userChannels) ->
                    val requiredChannelName = foundMessage.sender!!.substringBefore('@')
                    if (userChannels == null || !userChannels.contains(requiredChannelName)) {
                        throw UserNotAuthorizedException(
                                "Must be a member of $requiredChannelName to fetch its messages")
                    }
                    Pair(foundMessage.sender!!, foundMessage)
                }
    }

    /**
     * Total number of pending messages, i.e. messages that are waiting for a user to read them, not including channel
     * messages.
     *
     * @return The number of pending messages.
     */
    fun getPendingMessages(): CompletableFuture<Long> {
        return messagesMetadataRoot.read("pending_messages_count")
                .thenApply { it?.toLong() ?: 0 }
    }

    /**
     * Total number of channel messages, i.e., messages that can be fetched using [CourseApp.fetchMessage].
     *
     * @return The number of messages in channels.
     */
    fun getChannelMessages(): CompletableFuture<Long> {
        return messagesMetadataRoot.read("channels_pending_messages_count")
                .thenApply { it?.toLong() ?: 0 }
    }

    /**
     * Return a sorted list of the top [k] channels in the system by message volume. The list will be sorted in
     * descending order.
     *
     * If two channels have the exact same number of messages, they will be sorted in ascending appearance order.
     *
     * If there are less than [k] channels in the system, a shorter list will be returned.
     *
     * @return A sorted list of channels by message count.
     */
    fun topKChannelsByMessages(k: Int = 10): CompletableFuture<List<String>> {
        return CompletableFuture.completedFuture(treeTopK(channelsByMessagesStorage, k))
    }

    /**
     * Invokes the callback for all broadcast, channel (that the user is member of) & private messages that the user *hasn't read*
     */
    private fun tryReadingPendingMessages(username: String, callback: ListenerCallback)
            : CompletableFuture<Unit> {
        return tryReadingBroadcastMessages(username, callback)
                .thenCompose { maxBroadcastId ->
                    tryReadingChannelMessages(username, callback)
                            .thenCompose { maxChannelId ->
                                tryReadingPrivateMessages(username, callback)
                                        .thenCompose { maxPrivateId ->
                                            val maxId = maxOf(maxBroadcastId, maxChannelId, maxPrivateId)
                                            usersRoot.document(username)
                                                    .set(Pair("last_message_read", maxId.toString()))
                                                    .update()
                                        }
                            }
                }
    }

    /**
     * Tries to invoke a user's callback on all messages in a given messages document (= has a field named "message")
     * User's callback will only be invoked if the message is pending *for the user* and hasn't been read yet
     */
    private fun tryReadingListOfMessages(username: String, callback: ListenerCallback,
                                         msgsListDoc: DocumentReference): CompletableFuture<Long> {
        return msgsListDoc.readList("messages")
                .thenApply { stringList ->
                    deserializeToMessagesList(stringList)
                }.thenCompose { msgsList ->
                    usersRoot.document(username)
                            .read("last_message_read")
                            .thenApply { lastIdRead -> Pair(msgsList, lastIdRead?.toLong() ?: 0) }
                }.thenCompose { (msgsList, lastIdRead) ->
                    tryReadingListOfMessagesAux(username, msgsListDoc, callback, msgsList, lastIdRead)
                }
    }

    private fun tryReadingListOfMessagesAux(username: String, msgsDoc: DocumentReference, callback: ListenerCallback,
                                            msgsList: MutableList<MessageImpl>, lastIdRead: Long, index: Int = 0,
                                            currentMax: Long = lastIdRead): CompletableFuture<Long> {
        if (msgsList.size <= index) {
            return msgsDoc.set("messages", serializeMessagesList(msgsList))
                    .update()
                    .thenApply { currentMax }
        }

        if (msgsList[index].id <= lastIdRead)
            return tryReadingListOfMessagesAux(
                    username, msgsDoc, callback, msgsList, lastIdRead, index + 1, currentMax)

        // user should read this message
        msgsList[index].received = LocalDateTime.now()
        return callback(msgsList[index].sender!!, msgsList[index])
                .thenApply { msgsList[index].usersCount -= 1 }
                .thenCompose {
                    tryDeleteMessage(username, msgsList[index])
                }.thenCompose {
                    val nextMax = maxOf(currentMax, msgsList[index].id)
                    tryReadingListOfMessagesAux(
                            username, msgsDoc, callback, msgsList, lastIdRead, index + 1, nextMax)
                }
    }

    /**
     * Invokes the callback for all private messages sent to [username] that the [username] *hasn't read*
     */
    private fun tryReadingPrivateMessages(username: String, callback: ListenerCallback)
            : CompletableFuture<Long> {
        val msgsDocument = usersRoot.document(username)
        return tryReadingListOfMessages(username, callback, msgsDocument)
    }

    /**
     * Invokes the callback for all channel messages that [username] *hasn't read*
     */
    private fun tryReadingChannelMessages(username: String, callback: ListenerCallback)
            : CompletableFuture<Long> {
        return usersRoot.document(username)
                .readList("channels")
                .thenCompose { channels ->
                    tryReadingChannelMessagesAux(channels, username, callback)
                }
    }

    private fun tryReadingChannelMessagesAux(channels: List<String>?, username: String,
                                             callback: ListenerCallback, index: Int = 0, currentMax: Long = 0)
            : CompletableFuture<Long> {
        if (channels == null || channels.size <= index)
            return CompletableFuture.completedFuture(currentMax)
        val msgsDocument = messagesRoot.document("channel_messages")
        return tryReadingListOfMessages(username, callback, msgsDocument)
                .thenCompose { currentChannelMax ->
                    val newMax = maxOf(currentMax, currentChannelMax)
                    tryReadingChannelMessagesAux(channels, username, callback, index + 1, newMax)
                }
    }

    /**
     * Invokes the callback for all broadcast messages that [username] *hasn't read*
     */
    private fun tryReadingBroadcastMessages(username: String, callback: ListenerCallback)
            : CompletableFuture<Long> {
        val msgsDocument = messagesRoot.document("broadcast_messages")
        return tryReadingListOfMessages(username, callback, msgsDocument)
    }

    /**
     * Invokes all the registered callbacks that belong to a user who is a member of [channel] on a channel message
     */
    private fun invokeChannelCallbacks(channel: String, users: List<String>, message: MessageImpl, index: Int = 0)
            : CompletableFuture<Unit> {
        if (users.size <= index)
            return CompletableFuture.completedFuture(Unit)
        return isMemberOfChannel(users[index], channel)
                .thenCompose { result ->
                    if (result) {
                        invokeUserCallbacks(users[index], message)
                    } else {
                        CompletableFuture.completedFuture(Unit)
                    }
                }.thenCompose {
                    invokeChannelCallbacks(channel, users, message, index + 1)
                }
    }

    /**
     * Invokes all of the registered callbacks on a broadcast message
     */
    private fun invokeBroadcastCallbacks(users: List<String>, message: MessageImpl, index: Int = 0)
            : CompletableFuture<Unit> {
        if (users.size <= index)
            return CompletableFuture.completedFuture(Unit)
        return invokeUserCallbacks(users[index], message)
                .thenCompose {
                    invokeBroadcastCallbacks(users, message, index + 1)
                }
    }

    /**
     * Attempts to delete a message. A message should be deleted if it's done pending and is not a channel message
     *
     * @param username: last username who read the message
     * @param message: message that will potentially be deleted
     */
    private fun tryDeleteMessage(username: String, message: MessageImpl): CompletableFuture<Unit> {
        if (!message.isDonePending() || message.sender!![0] == '#') {
            return CompletableFuture.completedFuture(Unit)
        }
        // need to delete message - check whether message was sent as a *broadcast* or *privately*

        // assume broadcast message and change if necessary
        var messageDoc = messagesRoot.document("broadcast_message")
        if (message.sender!![0] == '@')
        // private message
            messageDoc = usersRoot.document(username)

        return messageDoc.readList("messages")
                .thenApply { deserializeToMessagesList(it) }
                .thenApply { removeMessageById(it, message.id) }
                .thenApply { serializeMessagesList(it) }
                .thenCompose { updatedMsgsList ->
                    messageDoc.set("messages", updatedMsgsList)
                            .update()
                }.thenCompose {
                    updateMessagesCount(message.sender!!, -1)
                }
    }

    /**
     * @param msgsList: list of messages
     * @param msgId: id of some message
     * @return [msgsList] without the message of id [msgId]
     */
    private fun removeMessageById(msgsList: MutableList<MessageImpl>, msgId: Long): MutableList<MessageImpl> {
        return msgsList.asSequence()
                .filter { it.id != msgId }
                .toMutableList()
    }

    /**
     * Invokes all of [username]'s callbacks on [message], so long as that message is not a channel message that the user should not invoke
     */
    private fun invokeUserCallbacks(username: String, message: MessageImpl): CompletableFuture<Unit> {
        val userCallbacks = messageListeners[username]
        if (userCallbacks == null || userCallbacks.isEmpty())
            return CompletableFuture.completedFuture(Unit)
        message.received = LocalDateTime.now()
        message.usersCount -= 1
        return usersRoot.document(username)
                .readList("channels")
                .thenApply { it ?: listOf() }
                .thenCompose { userChannels ->
                    usersRoot.document(username)
                            .read("last_message_read")
                            .thenApply { it?.toLong() ?: 0 }
                            .thenCompose { lastReadId ->
                                invokeUserCallbacksAux(userCallbacks, userChannels, username, message, lastReadId)
                                        .thenCompose { maxReadMessageId ->
                                            if (maxReadMessageId > lastReadId) {
                                                usersRoot.document(username)
                                                        .set(Pair("last_message_read", maxReadMessageId.toString()))
                                                        .update()
                                            } else {
                                                CompletableFuture.completedFuture(Unit)
                                            }
                                        }
                            }
                }.thenCompose {
                    tryDeleteMessage(username, message)
                }
    }

    private fun invokeUserCallbacksAux(userCallbacks: List<ListenerCallback>, userChannels: List<String>,
                                       username: String, message: MessageImpl, lastReadId: Long,
                                       index: Int = 0, maxReadMessageId: Long = 0): CompletableFuture<Long> {
        if (userCallbacks.size <= index)
            return CompletableFuture.completedFuture(maxReadMessageId)
        if (message.sender!![0] == '#') {
            val channelName = message.sender!!.substringBefore('@')
            if (!userChannels.contains(channelName))
                return CompletableFuture.completedFuture(maxReadMessageId)
        }

        if (message.id <= lastReadId)
            return invokeUserCallbacksAux(userCallbacks, userChannels, username, message, lastReadId, index + 1)

        return userCallbacks[index](message.sender!!, message)
                .thenCompose {
                    invokeUserCallbacksAux(
                            userCallbacks, userChannels, username, message, lastReadId, index + 1, message.id)
                }

    }

    /**
     * Uploads a message to the database: concat to list & update counters
     */
    private fun uploadMessage(messageToSend: MessageImpl, messageDoc: DocumentReference): CompletableFuture<Unit> {
        return messageDoc.readList("messages")
                .thenApply {
                    val msgsList = deserializeToMessagesList(it)
                    msgsList.add(messageToSend)
                    msgsList
                }.thenCompose { msgsList ->
                    messageDoc.set("messages", serializeMessagesList(msgsList))
                            .update()
                }.thenCompose {
                    updateMessagesCount(messageToSend.sender!!)
                }
    }

    /**
     * Updates the appropriate messages' counter by [change]
     */
    private fun updateMessagesCount(sender: String, change: Int = 1): CompletableFuture<Unit> {
        if (sender[0] == '#' && change == 1)
        // channel send
            return getChannelMessages()
                    .thenCompose {
                        messagesMetadataRoot.set(Pair("channels_pending_messages_count", (it + change).toString()))
                                .update()
                    }
        // broadcast / private send OR delete
        return getPendingMessages()
                .thenCompose {
                    messagesMetadataRoot.set(Pair("pending_messages_count", (it + change).toString()))
                            .update()
                }
    }

    /**
     * @param stringList: list of serialized messages
     * @return list of messages that were deserialized from [stringList]
     */
    private fun deserializeToMessagesList(stringList: List<String>?): MutableList<MessageImpl> {
        return stringList?.asSequence()
                ?.map { MessageImpl.deserialize(it) }
                ?.toMutableList() ?: mutableListOf()
    }

    /**
     * @param msgsList: list of messages
     * @return list of messages that were serialized from [msgsList]
     */
    private fun serializeMessagesList(msgsList: MutableList<MessageImpl>): MutableList<String> {
        return msgsList.asSequence()
                .map { it.serialize() }
                .toMutableList()
    }

    /**
     * Returns whether a given user is a member of a given channel or not
     */
    private fun isMemberOfChannel(username: String, channel: String): CompletableFuture<Boolean> {
        return usersRoot.document(username)
                .readList("channels")
                .thenApply { channels ->
                    channels != null && channels.contains(channel)
                }
    }

    /**
     * Returns whether or not a given user is an administrator
     */
    private fun isAdmin(username: String): CompletableFuture<Boolean> {
        return usersRoot.document(username)
                .read("isAdmin")
                .thenApply { isAdmin ->
                    isAdmin == "true"
                }
    }

    /**
     * Translates a token to its corresponding user
     *
     * @throws InvalidTokenException if the token does not belong to any user
     */
    private fun tokenToUser(token: String): CompletableFuture<String> {
        return tokensRoot.document(token)
                .read("username")
                .thenApply { tokenUsername ->
                    tokenUsername ?: throw InvalidTokenException("token does not match any active user")
                }
    }
}