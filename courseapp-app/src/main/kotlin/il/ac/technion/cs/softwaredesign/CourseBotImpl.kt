package il.ac.technion.cs.softwaredesign

import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.exceptions.NoSuchEntityException
import il.ac.technion.cs.softwaredesign.exceptions.UserNotAuthorizedException
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.Message
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import javax.script.ScriptEngineManager
import kotlin.collections.ArrayList

class CourseBotImpl @Inject constructor(private val app: CourseApp, private val msgFactory: MessageFactory,
                                        private val name: String, private val token: String) : CourseBot {

    private val channelsList = ArrayList<String>()

    private val calculatorEngine = ScriptEngineManager().getEngineByName("Javascript")
    private var calculatorTrigger: String? = null
    private var tippingTrigger: String? = null

    private val ledgerMap = mutableMapOf<String, MutableMap<String, Long>>()

    private val keywordTrackerMap = mutableMapOf<Pair<String?, MediaType?>, ArrayList<Pair<Regex, Long>>>()

    private val userLastMessageMap = mutableMapOf<String, LocalDateTime?>()
    // Map of this format: channel -> ${counter}+'/'+${username}
    private val userMessageCounterMap = mutableMapOf<String, ArrayList<String>>()
    private var mostActiveUser: MutableMap<String, String?> = mutableMapOf()
    private var mostActiveUserMessageCount: MutableMap<String, Long?> = mutableMapOf()

    private val surveyMap = mutableMapOf<String, ArrayList<Pair<String, Long>>>()

    init {
        app.addListener(token, ::lastMessageCallback).thenCompose {
            app.addListener(token, ::messageCounterCallback)
        }.thenCompose {
            app.addListener(token, ::keywordTrackingCallback)
        }.thenCompose {
            app.addListener(token, ::calculatorCallback)
        }.thenCompose{
            app.addListener(token, ::tippingCallback)
        }.join()
    }

    override fun join(channelName: String): CompletableFuture<Unit> {
        if (!validChannelName(channelName))
            return CompletableFuture.supplyAsync { throw UserNotAuthorizedException() }
        if (channelsList.contains(channelName))
            return CompletableFuture.completedFuture(Unit)
        return app.channelJoin(token, channelName).thenApply {
            channelsList.add(channelName)
            //TODO: add the channel to all data structures
            Unit
        }
    }

    override fun part(channelName: String): CompletableFuture<Unit> {
        return app.channelPart(token, channelName).thenApply {
            channelsList.remove(channelName)
            userLastMessageMap.remove(channelName)
            userMessageCounterMap.remove(channelName)
            //TODO: remove the channel from all data structures
            Unit
        }
    }

    override fun channels(): CompletableFuture<List<String>> {
        return CompletableFuture.completedFuture(channelsList)
    }

    override fun beginCount(channel: String?, regex: String?, mediaType: MediaType?): CompletableFuture<Unit> {
        if (regex == null && mediaType == null)
            return CompletableFuture.supplyAsync { throw IllegalArgumentException() }
        return CompletableFuture.supplyAsync {
            val actualRegex = if (regex == null) Regex("(?s).*") else Regex(regex)
            if (keywordTrackerMap[Pair(channel, mediaType)] == null)
                keywordTrackerMap[Pair(channel, mediaType)] = ArrayList()
            val channelTrackers = keywordTrackerMap[Pair(channel, mediaType)]
            for ((reg, count) in channelTrackers!!)
                if (reg == actualRegex)
                    channelTrackers.remove(Pair(reg, count))
            channelTrackers.add(Pair(actualRegex, 0L))
            keywordTrackerMap[Pair(channel, mediaType)] = channelTrackers
            Unit
        }
    }

    override fun count(channel: String?, regex: String?, mediaType: MediaType?): CompletableFuture<Long> {
        return CompletableFuture.supplyAsync {
            val channelTrackers = keywordTrackerMap[Pair(channel, mediaType)] ?: throw IllegalArgumentException()
            val actualRegex = if (regex == null) Regex("(?s).*") else Regex(regex)
            getRegexCount(channelTrackers, actualRegex)
        }
    }

    private fun getRegexCount(channelTrackers: ArrayList<Pair<Regex, Long>>, regex: Regex): Long {
        for ((reg, count) in channelTrackers) {
            if (reg == regex)
                return count
        }
        return 0L // we shouldn't get here
    }

    override fun setCalculationTrigger(trigger: String?): CompletableFuture<String?> {
        return CompletableFuture.supplyAsync {
            val prevTrigger = calculatorTrigger
            calculatorTrigger = trigger
            prevTrigger
        }
    }

    override fun setTipTrigger(trigger: String?): CompletableFuture<String?> {
        return CompletableFuture.supplyAsync {
            val prevTrigger = tippingTrigger
            tippingTrigger = trigger
            prevTrigger
        }
    }

    override fun seenTime(user: String): CompletableFuture<LocalDateTime?> {
        return CompletableFuture.completedFuture(userLastMessageMap[user])
    }

    /*
    override fun mostActiveUser(channel: String): CompletableFuture<String?> {
        return CompletableFuture.supplyAsync {
            val channelCounters = userMessageCounterMap[channel]
            if (channelCounters == null)
                null
            else {
                val topPair = getKthTopUserCounter(channelCounters)
                val secondPair = getKthTopUserCounter(channelCounters, k = 1)

                if (topPair == null || (secondPair != null && topPair.first == secondPair.first))
                    null
                else
                    topPair.second
            }
        }
    }

     */

    override fun mostActiveUser(channel: String): CompletableFuture<String?> {
        return CompletableFuture.supplyAsync {
            if (!channelsList.contains(channel))
                throw NoSuchEntityException()
            mostActiveUser[channel]
        }
    }
//
//    private fun getKthTopUserCounter(list: ArrayList<String>, k: Int = 0): Pair<Long, String>? {
//        if (k >= list.size)
//            return null
//        val compareCounterEntries = Comparator<String> { entry1, entry2 ->
//            val count1 = entry1.substringBefore('/').toInt()
//            val count2 = entry2.substringBefore('/').toInt()
//            count1 - count2
//        }
//
//        val listCopy = ArrayList(list)
//        listCopy.sortWith(compareCounterEntries)
//        return parseChannelCounterEntry(listCopy[listCopy.size - (k + 1)])
//    }

    override fun richestUser(channel: String): CompletableFuture<String?> {
        return CompletableFuture.supplyAsync {
            if (!channelsList.contains(channel))
                throw NoSuchEntityException()
            val channelLedger = ledgerMap[channel]!!
            var max = 1000L // TODO: check this
            var richestUser: String? = null
            for ((user, money) in channelLedger) {
                if (money > max) {
                    max = money
                    richestUser = user
                }
                if (money == max) {
                    richestUser = null
                }
            }
            richestUser
        }
    }

    //     private val surveyMap = mutableMapOf<String, ArrayList<Pair<String, Long>>>()
    override fun runSurvey(channel: String, question: String, answers: List<String>): CompletableFuture<String> {
        return CompletableFuture.supplyAsync {
            if (!channelsList.contains(channel))
                throw NoSuchEntityException()
            val identifier = generateSurveyIdentifier()
            val newSurveyList = ArrayList<Pair<String, Long>>()
            for (answer in answers) {
                newSurveyList.add(Pair(answer, 0L))
            }
            surveyMap[identifier] = newSurveyList
            identifier
        }
    }

    private fun generateSurveyIdentifier() = "$name${LocalDateTime.now()}"

    override fun surveyResults(identifier: String): CompletableFuture<List<Long>> {
        return CompletableFuture.supplyAsync {
            val resultPairs = surveyMap[identifier] ?: ArrayList()
            val countResultList = ArrayList<Long>()
            for ((_, count) in resultPairs) {
                countResultList.add(count)
            }
            countResultList
        }
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
                incrementChannelCounterList(channelName, channelCounterList, userName)
                userMessageCounterMap[channelName] = channelCounterList
            }
        }
    }

    private fun keywordTrackingCallback(source: String, msg: Message): CompletableFuture<Unit> {
        return CompletableFuture.supplyAsync {
            val messageChannelName: String? = extractChannelName(source)
            val messageMediaType = msg.media
            val pair = Pair(messageChannelName, messageMediaType)
            val channelTrackers = keywordTrackerMap[pair]
            if (channelTrackers != null) {
                for ((regex, counter) in channelTrackers) {
                    if (regex matches msg.contents.toString()) {
                        channelTrackers.remove(Pair(regex, counter))
                        channelTrackers.add(Pair(regex, counter + 1))
                    }
                }
                keywordTrackerMap[pair] = channelTrackers
            }
        }
    }

    private fun calculatorCallback(source: String, msg: Message): CompletableFuture<Unit> {
        return CompletableFuture.supplyAsync {
            if (isChannelMessage(source)
                    && msg.media == MediaType.TEXT
                    && messageStartsWithTrigger(calculatorTrigger, msg)) {
                calculateExpression(msg)
            } else {
                null
            }
        }.thenCompose { calculation ->
            if (calculation == null)
                null
            else {
                msgFactory.create(MediaType.TEXT, calculation.toString().toByteArray())
            }
        }.thenCompose { message ->
            if (message == null) {
                CompletableFuture.completedFuture(Unit)
            } else {
                app.channelSend(token, extractChannelName(source)!!, message)
            }
        }
    }

    private fun tippingCallback(source: String, msg: Message): CompletableFuture<Unit> {
        return CompletableFuture.supplyAsync {
            if (isChannelMessage(source)
                    && msg.media == MediaType.TEXT
                    && messageStartsWithTrigger(tippingTrigger, msg))
                msg.contents.toString().substringAfter("${tippingTrigger!!} ")
            else
                null
        }.thenCompose { msgSuffix ->
            if (msgSuffix == null)
                CompletableFuture.completedFuture(false)
            else {
                val receiver = msgSuffix.substringAfter(' ')
                app.isUserInChannel(token, extractChannelName(source)!!, receiver)
            }
        }.thenApply { isMember ->
            if (!isMember)
                Unit
            else {
                val channelLedgerMap: MutableMap<String, Long>
                if (ledgerMap[source] == null) {
                    channelLedgerMap = mutableMapOf()
                    ledgerMap[source] = channelLedgerMap
                } else
                    channelLedgerMap = ledgerMap[source]!!

                val contentSuffix = msg.contents.toString().substringAfter("${tippingTrigger!!} ")
                val amount = contentSuffix.substringBefore(' ').toLong()
                val receiverName = contentSuffix.substringAfter(' ')
                var senderBalance = channelLedgerMap[extractSenderUsername(source)]
                var receiverBalance = channelLedgerMap[receiverName]

                if (senderBalance == null)
                    senderBalance = 1000L

                if (receiverBalance == null)
                    receiverBalance = 1000L

                senderBalance -= amount
                receiverBalance += amount

                channelLedgerMap[extractSenderUsername(source)] = senderBalance
                channelLedgerMap[receiverName] = receiverBalance
                ledgerMap[source] = channelLedgerMap
            }
        }
    }

    private fun messageStartsWithTrigger(trigger: String?, msg: Message): Boolean {
        trigger ?: return false
        val msgContent = msg.contents.toString()
        return msgContent.startsWith(trigger)
    }

    private fun calculateExpression(msg: Message): Int? {
        val expression = msg.contents.toString().substringAfter("${calculatorTrigger!!} ")
        return try {
            calculatorEngine.eval(expression) as Int
        } catch (e: Exception) {
            null
        }
    }

    private fun incrementChannelCounterList(channel: String, channelCounterList: ArrayList<String>, userName: String) {
        var existsFlag = false
        for (i in 0 until channelCounterList.size) {
            val (count, otherUser) = parseChannelCounterEntry(channelCounterList[i])
            if (otherUser != userName) continue
            existsFlag = true
            val newCount = count + 1
            channelCounterList[i] = "$newCount/$userName"
            tryUpdateMostActiveUser(channel, newCount, userName)
        }

        if (existsFlag) {
            channelCounterList.add("1/$userName")
            tryUpdateMostActiveUser(channel, 1L, userName)
        }
    }

    private fun tryUpdateMostActiveUser(channel: String, count: Long, otherUser: String) {
        val currentMostActiveCount = mostActiveUserMessageCount[channel]
        if (currentMostActiveCount == null || count > currentMostActiveCount) {
            mostActiveUser[channel] = otherUser
            mostActiveUserMessageCount[channel] = count
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
}