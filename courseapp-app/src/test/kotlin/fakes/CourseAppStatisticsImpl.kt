package fakes

import com.google.inject.Inject
import fakes.library.utils.DatabaseMapper
import fakes.managers.database.AuthenticationManager
import fakes.managers.database.ChannelsManager
import fakes.managers.database.MessagesManager
import java.util.concurrent.CompletableFuture

/**
 * Implementation of CourseApp querying functionality
 * @see CourseAppStatistics
 */
class CourseAppStatisticsImpl @Inject constructor(dbMapper: DatabaseMapper) : CourseAppStatistics {

    private val auth = AuthenticationManager(dbMapper)
    private val channelsManager = ChannelsManager(dbMapper)
    private val messagesManager = MessagesManager(dbMapper)

    override fun totalUsers(): CompletableFuture<Long> {
        return auth.getTotalUsers()
    }

    override fun loggedInUsers(): CompletableFuture<Long> {
        return auth.getLoggedInUsers()
    }

    override fun pendingMessages(): CompletableFuture<Long> {
        return messagesManager.getPendingMessages()
    }

    override fun channelMessages(): CompletableFuture<Long> {
        return messagesManager.getChannelMessages()
    }

    override fun top10ChannelsByUsers(): CompletableFuture<List<String>> {
        return channelsManager.topKChannelsByUsers()
    }

    override fun top10ActiveChannelsByUsers(): CompletableFuture<List<String>> {
        return channelsManager.topKChannelsByActiveUsers()
    }

    override fun top10UsersByChannels(): CompletableFuture<List<String>> {
        return channelsManager.topKUsersByChannels()
    }

    override fun top10ChannelsByMessages(): CompletableFuture<List<String>> {
        return messagesManager.topKChannelsByMessages()
    }
}