package il.ac.technion.cs.softwaredesign

import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.exceptions.UserNotAuthorizedException
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.Message
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

class CourseBotImpl @Inject constructor(private val app: CourseApp, private val name: String, private val token: String)
    : CourseBot {

    private val channelsList = ArrayList<String>()

    private val channelCalculatorTriggerMap = mutableMapOf<String, String>()
    private val channelTippingTriggerMap = mutableMapOf<String, String>()

    private val ledgerMap = mutableMapOf<Pair<String, String>, Long>()
    private val keywordTrackerMap = mutableMapOf<Triple<String?, Regex?, MediaType?>, Long>()
    private val userLastMessageMap = mutableMapOf<String, LocalDateTime?>()
    // Map of this format: channel -> ${counter}+'/'+${username}
    private val userMessageCounterMap = mutableMapOf<String, ArrayList<String>>()
    private val surveyMap = mutableMapOf<String, ArrayList<Pair<String, Long>>>()

    init {
        app.addListener(token, ::lastMessageCallback).thenCompose {
            app.addListener(token, ::messageCounterCallback)
        }.join()
    }

    override fun join(channelName: String): CompletableFuture<Unit> {
        if (!validChannelName(channelName))
            return CompletableFuture.supplyAsync { throw UserNotAuthorizedException() }
        if (channelsList.contains(channelName))
            return CompletableFuture.completedFuture(Unit)
        return app.channelJoin(token, channelName).thenApply {
            channelsList.add(channelName)
            Unit
        }
    }

    override fun part(channelName: String): CompletableFuture<Unit> {
        return app.channelPart(token, channelName).thenApply {
            channelsList.remove(channelName)
            Unit
        }
    }

    override fun channels(): CompletableFuture<List<String>> {
        return CompletableFuture.completedFuture(channelsList)
    }

    override fun beginCount(channel: String?, regex: String?, mediaType: MediaType?): CompletableFuture<Unit> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun count(channel: String?, regex: String?, mediaType: MediaType?): CompletableFuture<Long> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun setCalculationTrigger(trigger: String?): CompletableFuture<String?> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun setTipTrigger(trigger: String?): CompletableFuture<String?> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun seenTime(user: String): CompletableFuture<LocalDateTime?> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun mostActiveUser(channel: String): CompletableFuture<String?> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun richestUser(channel: String): CompletableFuture<String?> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun runSurvey(channel: String, question: String, answers: List<String>): CompletableFuture<String> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun surveyResults(identifier: String): CompletableFuture<List<Long>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    /**
     * Checks channels' names validity.
     * A channel's name is valid only if the following hold:
     *  - first letter is '#'
     *  - contains *only* a-z, A-Z, 0-9, '#' or '_' characters
     *
     * @param channel: name of the channel
     * @return true if the channel's name is valid, false otherwise
     */
    private fun validChannelName(channel: String): Boolean {
        if (channel[0] != '#') return false
        val validCharPool = ('a'..'z') + ('A'..'Z') + ('0'..'9') + '#' + '_'
        for (c in channel)
            if (!validCharPool.contains(c)) return false
        return true
    }

    private fun lastMessageCallback(source: String, msg: Message): CompletableFuture<Unit> {
        return CompletableFuture.supplyAsync {
            if (!isChannelMessage(source))
                Unit
            else {
                val currentSeenTime = userLastMessageMap[source]
                val newSeenTime = msg.created
                if (currentSeenTime == null || currentSeenTime.isBefore(newSeenTime)) {
                    userLastMessageMap[source] = newSeenTime
                }
            }
        }
    }

    private fun messageCounterCallback(source: String, msg: Message): CompletableFuture<Unit> {
        return CompletableFuture.supplyAsync {
            if (!isChannelMessage(source))
                Unit
            else {
                val channelName = extractChannelName(source)!!
                val userName = extractSenderUsername(source)
                val channelCounterList = userMessageCounterMap[channelName] ?: ArrayList()
                incrementChannelCounterList(channelCounterList, userName)
                userMessageCounterMap[channelName] = channelCounterList
            }
        }
    }

    private fun incrementChannelCounterList(channelCounterList: ArrayList<String>, userName: String) {
        var existsFlag = false
        for (i in 0 until channelCounterList.size) {
            val (count, otherUser) = parseChannelCounterEntry(channelCounterList[i])
            if (otherUser != userName) continue
            existsFlag = true
            val newCount = count + 1
            channelCounterList[i] = "$newCount/$userName"
        }

        if (existsFlag) {
            channelCounterList.add("1/$userName")
        }
    }

    private fun parseChannelCounterEntry(entry: String): Pair<Long, String> {
        return Pair(entry.substringBefore('/').toLong(), entry.substringAfter('/'))
    }

    private fun isBroadcastMessage(source: String): Boolean {
        return source == "BROADCAST"
    }

    private fun isChannelMessage(source: String): Boolean {
        return source[0] == '#'
    }

    private fun isPrivateMessage(source: String): Boolean {
        return source[0] == '@'
    }

    private fun extractSenderUsername(source: String): String {
        return source.substringAfter('@')
    }

    private fun extractChannelName(source: String): String? {
        if (!isChannelMessage(source))
            return null
        return source.substringBefore('@')
    }
}