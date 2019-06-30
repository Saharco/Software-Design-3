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
import il.ac.technion.cs.softwaredesign.lib.db.Database
import il.ac.technion.cs.softwaredesign.wrappers.KeywordsTracker

class CourseBotImpl @Inject constructor(private val app: CourseApp, private val db: Database, private val msgFactory: MessageFactory,
                                        private val name: String, private val token: String) : CourseBot {

    companion object {
        private val charset = Charsets.UTF_8
        private val calculatorEngine = ScriptEngineManager().getEngineByName("JavaScript")

        // List(channel)
        private val KEY_LIST_CHANNELS = "channelsList"

        // String?
        private val KEY_TRIGGER_CALCULATOR = "calculatorTrigger"
        // String?
        private val KEY_TRIGGER_TIPPING = "tippingTrigger"

        // Map: channel -> (username, count)
        private val KEY_MAP_LEDGER = "ledgerMap"
        // Map: channel -> (username, count)
        private val KEY_MAP_USER_LAST_MESSAGE = "userLastMessageMap"
        // Map: channel -> List(${counter}+'/'+${username})
        private val KEY_MAP_USER_MESSAGE_COUNTER = "userMessageCounterMap"
        // Map: channel -> username?
        private val KEY_MAP_CHANNEL_MOST_ACTIVE_USER = "mostActiveUser"
        // Map: channel -> count?
        private val KEY_MAP_CHANNEL_MOST_ACTIVE_USER_MESSAGE_COUNTER = "channelMostActiveUserMessageCountMap"
        // Map: channel -> List(answer, counter)
        private val KEY_MAP_SURVERY = "surveyMap"
        // Map: userName -> (id -> answer)
        private val KEY_MAP_SURVEY_VOTERS = "surveyVotersMap"

        private val KEY_KEYWORDS_TRACKER = "keywordsTracker"
    }

    init {
        app.addListener(token, ::lastMessageCallback).thenCompose {
            app.addListener(token, ::messageCounterCallback)
        }.thenCompose {
            app.addListener(token, ::keywordTrackingCallback)
        }.thenCompose {
            app.addListener(token, ::calculatorCallback)
        }.thenCompose {
            app.addListener(token, ::tippingCallback)
        }.thenCompose {
            app.addListener(token, ::surveyCallback)
        }.join()
    }

    override fun join(channelName: String): CompletableFuture<Unit> {
        if (!validChannelName(channelName))
            return CompletableFuture.supplyAsync { throw UserNotAuthorizedException() }
        return app.channelJoin(token, channelName).thenCompose {
            db.document("bots")
                    .find(name, listOf("channelsList"))
                    .execute()
        }.thenCompose {
            val channelsList = it?.getAsList("channelsList") ?: mutableListOf()
            if (channelsList.contains(channelName))
                CompletableFuture.completedFuture(false)
            else {
                channelsList.add(channelName)
                db.document("bots")
                        .update(name)
                        .set("channelsList" to channelsList)
                        .execute()
                        .thenApply { true }
            }
        }.thenCompose { isAdded ->
            if (isAdded) {
                db.document("metadata")
                        .find(channelName, listOf("bots"))
                        .execute()
                        .thenApply {
                            val botsList = it?.getAsList("bots") ?: mutableListOf()
                            botsList.add(name)
                            botsList
                        }
            } else {
                CompletableFuture.completedFuture(null as MutableList<String>?)
            }
        }.thenCompose { list ->
            if (list != null) {
                db.document("metadata")
                        .update(channelName)
                        .set("bots" to list)
                        .execute()
                        .thenApply { Unit }
            } else {
                CompletableFuture.completedFuture(Unit)
            }
        }
    }

    //            channelsList.remove(channelName)
//            //TODO: remove the channel from survey maps!
//            ledgerMap.remove(channelName)
//            userMessageCounterMap.remove(channelName)
//            channelMostActiveUserMap.remove(channelName)
//            channelMostActiveUserMessageCountMap.remove(channelName)
//            keywordsTracker.remove(channelName)
    override fun part(channelName: String): CompletableFuture<Unit> {
        return app.channelPart(token, channelName).thenCompose {
            removeFromList<String>(KEY_LIST_CHANNELS, channelName)
        }.thenCompose {
            removeFromMap<String, ArrayList<String>>(KEY_MAP_LEDGER, channelName)
        }.thenCompose {
            removeFromMap<String, ArrayList<String>>(KEY_MAP_USER_MESSAGE_COUNTER, channelName)
        }.thenCompose {
            removeFromMap<String, String?>(KEY_MAP_CHANNEL_MOST_ACTIVE_USER, channelName)
        }.thenCompose {
            removeFromMap<String, Long?>(KEY_MAP_CHANNEL_MOST_ACTIVE_USER_MESSAGE_COUNTER, channelName)
        }.thenCompose {
            readFromDocument<KeywordsTracker>(KEY_KEYWORDS_TRACKER)
        }.thenApply {
            it ?: KeywordsTracker()
        }.thenCompose {
            writeToDocument()
        }
    }



    override fun channels(): CompletableFuture<List<String>> {
        return CompletableFuture.completedFuture(channelsList)
    }

    override fun beginCount(channel: String?, regex: String?, mediaType: MediaType?): CompletableFuture<Unit> {
        return CompletableFuture.supplyAsync { keywordsTracker[channel, mediaType] = regex }
    }

    override fun count(channel: String?, regex: String?, mediaType: MediaType?): CompletableFuture<Long> {
        return CompletableFuture.completedFuture(keywordsTracker[channel, mediaType, regex])
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

    override fun mostActiveUser(channel: String): CompletableFuture<String?> {
        return CompletableFuture.supplyAsync {
            if (!channelsList.contains(channel))
                throw NoSuchEntityException()
            channelMostActiveUserMap[channel]
        }
    }

    override fun richestUser(channel: String): CompletableFuture<String?> {
        return CompletableFuture.supplyAsync {
            if (!channelsList.contains(channel))
                throw NoSuchEntityException()
            val channelLedger = ledgerMap[channel]
            if (channelLedger == null)
                null
            else {
                var max = 1000L
                var richestUser: String? = null
                for ((user, money) in channelLedger) {
                    if (money > max) {
                        max = money
                        richestUser = user
                    } else if (money == max) {
                        richestUser = null
                    }
                }
                richestUser
            }
        }
    }

    override fun runSurvey(channel: String, question: String, answers: List<String>): CompletableFuture<String> {
        return msgFactory.create(MediaType.TEXT, question.toByteArray(charset)).thenApply { message ->
            if (!channelsList.contains(channel))
                throw NoSuchEntityException()
            val identifier = generateSurveyIdentifier(channel)
            val newSurveyList = ArrayList<Pair<String, Long>>()
            for (answer in answers) {
                newSurveyList.add(Pair(answer, 0L))
            }
            surveyMap[identifier] = newSurveyList
            app.channelSend(token, channel, message)
            identifier
        }
    }

    private fun generateSurveyIdentifier(channel: String) = "$channel/$name/${LocalDateTime.now()}"

    override fun surveyResults(identifier: String): CompletableFuture<List<Long>> {
        return CompletableFuture.supplyAsync {
            val resultPairs = surveyMap[identifier] ?: throw NoSuchEntityException()
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
                    userLastMessageMap[extractSenderUsername(source)] = newSeenTime
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
        return CompletableFuture.completedFuture(
                keywordsTracker.track(extractChannelName(source), msg.media, msg.contents.toString(charset)))
    }

    private fun calculatorCallback(source: String, msg: Message): CompletableFuture<Unit> {
        if (!isChannelMessage(source)
                || msg.media != MediaType.TEXT
                || !messageStartsWithTrigger(calculatorTrigger, msg)) {
            return CompletableFuture.completedFuture(Unit)
        }

        val calculationResult = calculateExpression(msg) ?: return CompletableFuture.completedFuture(Unit)

        return msgFactory.create(MediaType.TEXT, calculationResult.toString().toByteArray()).thenCompose { message ->
            app.channelSend(token, extractChannelName(source)!!, message)
        }
    }

    private fun tippingCallback(source: String, msg: Message): CompletableFuture<Unit> {
        return CompletableFuture.supplyAsync {
            if (isChannelMessage(source)
                    && msg.media == MediaType.TEXT
                    && messageStartsWithTrigger(tippingTrigger, msg))
                msg.contents.toString(charset).substringAfter("${tippingTrigger!!} ")
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
                val channelName = extractChannelName(source)!!
                if (ledgerMap[channelName] == null) {
                    channelLedgerMap = mutableMapOf()
                    ledgerMap[channelName] = channelLedgerMap
                } else
                    channelLedgerMap = ledgerMap[channelName]!!

                val contentSuffix = msg.contents.toString(charset).substringAfter("${tippingTrigger!!} ")
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
                ledgerMap[channelName] = channelLedgerMap
            }
        }
    }

    private fun surveyCallback(source: String, msg: Message): CompletableFuture<Unit> {
        return CompletableFuture.supplyAsync {
            if (isChannelMessage(source)
                    && msg.media == MediaType.TEXT) {
                val messageChannelName = extractChannelName(source)!!
                val userName = extractSenderUsername(source)

                val voterList: MutableMap<String, String> = surveyVoters[userName] ?: mutableMapOf() // dont forget
                for ((id, surveyListOfAnswers) in surveyMap) {
                    //remove first answer of the user if he answered this survey
                    if (voterList.containsKey(id)) {
                        for ((i, pair) in surveyListOfAnswers.withIndex()) {
                            val answer = pair.first
                            val counter = pair.second
                            if (answer == voterList[id]) {
                                surveyListOfAnswers[i] = Pair(answer, counter - 1)
                                voterList.remove(id)
                            }
                        }
                    }
                    //add the user answer
                    if (id.startsWith(messageChannelName)) {
                        for ((i, pair) in surveyListOfAnswers.withIndex()) {
                            val answer = pair.first
                            val counter = pair.second
                            if (answer == msg.contents.toString(charset))
                                surveyListOfAnswers[i] = Pair(answer, counter + 1)
                            voterList[id] = answer
                        }
                    }
                }
                surveyVoters[userName] = voterList
            }
        }
    }

    private fun messageStartsWithTrigger(trigger: String?, msg: Message): Boolean {
        trigger ?: return false
        val msgContent = msg.contents.toString(charset)
        return msgContent.startsWith(trigger)
    }

    private fun calculateExpression(msg: Message): Int? {
        val expression = msg.contents.toString(charset).substringAfter("${calculatorTrigger!!} ")
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
        val currentMostActiveCount = channelMostActiveUserMessageCountMap[channel]
        if (currentMostActiveCount == null || count > currentMostActiveCount) {
            channelMostActiveUserMap[channel] = otherUser
            channelMostActiveUserMessageCountMap[channel] = count
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

    private fun fetchFromDocument(key: String): CompletableFuture<Any?> {
        return db.document("bots")
                .find(name, listOf(key))
                .execute()
                .thenApply { it?.get(key) }
    }

    private fun writeToDocument(key: String, value: Any): CompletableFuture<Unit> {
        return db.document("bots")
                .update(name)
                .set(key to value)
                .execute()
                .thenApply { Unit }
    }

    private fun <T> readFromDocument(key: String): CompletableFuture<T?> {
        return fetchFromDocument(key).thenApply {
            it as T?
        }
    }

    private fun <T> readListFromDocument(key: String): CompletableFuture<MutableList<T>> {
        return readFromDocument<MutableList<T>>(key).thenApply {
           it ?: mutableListOf()
        }
    }

    private fun <K, V> readMapFromDocument(key: String): CompletableFuture<MutableMap<K, V>> {
        return readFromDocument<MutableMap<K, V>>(key).thenApply {
            it ?: mutableMapOf()
        }
    }
    private fun <T> removeFromList(listName: String, value: T): T {
        return readListFromDocument<T>(listName).thenApply {
            it.remove(value)
        }.thenApply {

        }
    }
}