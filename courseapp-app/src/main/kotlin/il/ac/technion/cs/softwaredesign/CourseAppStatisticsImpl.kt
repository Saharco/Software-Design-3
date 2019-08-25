package il.ac.technion.cs.softwaredesign

import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.annotations.*
import il.ac.technion.cs.softwaredesign.constants.EConstants.*
import java.util.concurrent.CompletableFuture

class CourseAppStatisticsImpl @Inject constructor(private val dataStore : IStorage,
                                                  @DictionaryType("channelUsers") private val channelsTotalUsers : IDictionary,
                                                  @DictionaryType("channelActiveUsers") private val channelsActiveUsers : IDictionary,
                                                  @DictionaryType("channelMsgs") private val channelMsgs : IDictionary,
                                                  @DictionaryType("userChannels") private val userChannels : IDictionary):CourseAppStatistics{

    override fun totalUsers(): CompletableFuture<Long> {
       return CompletableFuture.completedFuture(dataStore.getCounterValue(TOTAL_USERS.ordinal))
    }

    override fun loggedInUsers(): CompletableFuture<Long> {
        return CompletableFuture.completedFuture(dataStore.getCounterValue(ACTIVE_USERS_COUNTER.ordinal))
    }

    override fun top10ChannelsByUsers(): CompletableFuture<List<String>> {
        val result =  channelsTotalUsers.getDataSortedByKeysInDescendingOrder(10)
                .map{ dataStore.readFromMap("${CHANNEL_ID_TO_NAME.ordinal},$it")!! }
        return CompletableFuture.completedFuture(result)
    }

    override fun top10ActiveChannelsByUsers(): CompletableFuture<List<String>> {
        val result =  channelsActiveUsers.getDataSortedByKeysInDescendingOrder(10)
                .map{ dataStore.readFromMap("${CHANNEL_ID_TO_NAME.ordinal},$it")!! }
        return CompletableFuture.completedFuture(result)
    }

    override fun top10UsersByChannels(): CompletableFuture<List<String>> {
        val result =  userChannels.getDataSortedByKeysInDescendingOrder(10)
        return CompletableFuture.completedFuture(result)
    }

    override fun pendingMessages(): CompletableFuture<Long> {
        val pendingPrivateMsgs = dataStore.getCounterValue(PENDING_PRIVATE_MESSAGES.ordinal)
        val pendingBroadcastMsgs = dataStore.getCounterValue(PENDING_BROADCAST_MESSAGES.ordinal)
        return CompletableFuture.completedFuture(pendingPrivateMsgs+pendingBroadcastMsgs)
    }

    override fun channelMessages(): CompletableFuture<Long> {
        val channelMsgs = dataStore.getCounterValue(CHANNEL_MESSAGES_COUNTER.ordinal)
        return CompletableFuture.completedFuture(channelMsgs)
    }

    override fun top10ChannelsByMessages(): CompletableFuture<List<String>> {
        val result =  channelMsgs.getDataSortedByKeysInDescendingOrder(10)
                .map{ dataStore.readFromMap("${CHANNEL_ID_TO_NAME.ordinal},$it")!! }
        return CompletableFuture.completedFuture(result)
    }

}