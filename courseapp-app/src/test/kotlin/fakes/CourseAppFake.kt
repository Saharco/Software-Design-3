package fakes

import com.google.inject.Inject
import fakes.managers.database.AuthenticationManager
import fakes.managers.database.ChannelsManager
import fakes.managers.database.MessagesManager
import fakes.library.utils.DatabaseMapper
import il.ac.technion.cs.softwaredesign.CourseApp
import il.ac.technion.cs.softwaredesign.ListenerCallback
import il.ac.technion.cs.softwaredesign.messages.Message
import java.util.concurrent.CompletableFuture

/**
 * Implementation of CourseApp functionality
 * @see CourseApp
 */
class CourseAppFake @Inject constructor(dbMapper: DatabaseMapper) : CourseApp {
    private val auth = AuthenticationManager(dbMapper)
    private val channelsManager = ChannelsManager(dbMapper)
    private val messagesManager = MessagesManager(dbMapper)

    override fun login(username: String, password: String): CompletableFuture<String> {
        return auth.performLogin(username, password)
    }

    override fun logout(token: String): CompletableFuture<Unit> {
        return auth.performLogout(token)
    }

    override fun isUserLoggedIn(token: String, username: String): CompletableFuture<Boolean?> {
        return auth.isUserLoggedIn(token, username)
    }

    override fun makeAdministrator(token: String, username: String): CompletableFuture<Unit> {
        return auth.makeAdministrator(token, username)
    }

    override fun channelJoin(token: String, channel: String): CompletableFuture<Unit> {
        return channelsManager.channelJoin(token, channel)
    }

    override fun channelPart(token: String, channel: String): CompletableFuture<Unit> {
        return channelsManager.channelPart(token, channel)
    }

    override fun channelMakeOperator(token: String, channel: String, username: String): CompletableFuture<Unit> {
        return channelsManager.channelMakeOperator(token, channel, username)
    }

    override fun channelKick(token: String, channel: String, username: String): CompletableFuture<Unit> {
        return channelsManager.channelKick(token, channel, username)
    }

    override fun isUserInChannel(token: String, channel: String, username: String): CompletableFuture<Boolean?> {
        return channelsManager.isUserInChannel(token, channel, username)
    }

    override fun numberOfActiveUsersInChannel(token: String, channel: String): CompletableFuture<Long> {
        return channelsManager.numberOfActiveUsersInChannel(token, channel)
    }

    override fun numberOfTotalUsersInChannel(token: String, channel: String): CompletableFuture<Long> {
        return channelsManager.numberOfTotalUsersInChannel(token, channel)
    }

    override fun addListener(token: String, callback: ListenerCallback): CompletableFuture<Unit> {
        return messagesManager.addListener(token, callback)
    }

    override fun removeListener(token: String, callback: ListenerCallback): CompletableFuture<Unit> {
        return messagesManager.removeListener(token, callback)
    }

    override fun channelSend(token: String, channel: String, message: Message): CompletableFuture<Unit> {
        return messagesManager.channelSend(token, channel, message)
    }

    override fun broadcast(token: String, message: Message): CompletableFuture<Unit> {
        return messagesManager.broadcast(token, message)
    }

    override fun privateSend(token: String, user: String, message: Message): CompletableFuture<Unit> {
        return messagesManager.privateSend(token, user, message)
    }

    override fun fetchMessage(token: String, id: Long): CompletableFuture<Pair<String, Message>> {
        return messagesManager.fetchMessage(token, id)
    }
}