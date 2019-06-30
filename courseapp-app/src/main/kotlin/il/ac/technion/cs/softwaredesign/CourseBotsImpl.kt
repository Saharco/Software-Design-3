package il.ac.technion.cs.softwaredesign

import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.lib.db.Database
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.Message
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import java.util.concurrent.CompletableFuture
import javax.script.ScriptEngineManager

class CourseBotsImpl @Inject constructor(private val app: CourseApp, private val msgFactory: MessageFactory,
                                         private val db: Database)
    : CourseBots {

    companion object {
        private const val password = "BEEP BOOP I AM A RoBoTz"

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
        private val KEY_MAP_SURVEY = "surveyMap"
        // Map: userName -> (id -> answer)
        private val KEY_MAP_SURVEY_VOTERS = "surveyVotersMap"

        private val KEY_KEYWORDS_TRACKER = "keywordsTracker"
    }

    private val botsMap = mutableMapOf<String, CourseBot>()

    override fun prepare(): CompletableFuture<Unit> {
        return CompletableFuture.completedFuture(Unit) //TODO: change this to store metadata
    }

    override fun start(): CompletableFuture<Unit> {
        return CompletableFuture.completedFuture(Unit) //TODO: change this to load all of the bot's configuration details from storage
    }

    override fun bot(name: String?): CompletableFuture<CourseBot> {
        return db.document("metadata")
                .find("metadata", listOf("bot_counter"))
                .execute()
                .thenApply { it?.getInteger("bot_counter") ?: 0 }
                .thenCompose { botsCounter ->
                    val botName = name ?: "Anna$botsCounter"
                    db.document("bots")
                            .find(botName, listOf("token"))
                            .execute()
                            .thenApply { it != null }
                            .thenCompose { botExists ->
                                if (botExists)
                                    loadExistingBot(botName)
                                else
                                    createNewBot(botName)
                            }




                            .thenApply { Pair(botName, it != null) }
                }.thenCompose { (botName, botExists) ->
                    if (botExists) {
                        loadExistingBot(botName).thenApply { Pair(botName, it) }
                    } else {
                        createNewBot(botName).thenApply { Pair(botName, it) }
                    }
                }.thenCompose { (botName, bot) ->
                    attachListeners(botName)
                }
    }

    private fun loadExistingBot(botName: String): CompletableFuture<CourseBot> {
        return db.document("bots")
                .find(botName, listOf("token"))
                .execute()
                .thenApply { it?.getAsString("token")!! }
                .thenApply { token ->
                    CourseBotImpl(app, db, msgFactory, botName, token)
                }
    }

    private fun createNewBot(botName: String): CompletableFuture<CourseBot> {
        return updateBotsMetadata().thenCompose {
            app.login(botName, password)
        }.thenCompose { token ->
            db.document("bots")
                    .create(botName, mapOf(
                            "token" to token))
                    .execute()
                    .thenApply { token }
        }.thenApply { token ->
            CourseBotImpl(app, db, msgFactory, botName, token)
        }
    }

    private fun updateBotsMetadata(): CompletableFuture<Unit> {
        return db.document("metadata")
                .find("metadata", listOf("bot_counter"))
                .execute()
                .thenApply { it?.getInteger("bot_counter") ?: 0 }
                .thenCompose { counter ->
                    db.document("metadata")
                            .update("metadata")
                            .set("bot_counter" to counter + 1)
                            .execute()
                            .thenApply { Unit }
                }
    }

    override fun bots(channel: String?): CompletableFuture<List<String>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private fun calculatorCallbackCreator(botName: String): ListenerCallback {
        return object : ListenerCallback {
            override fun invoke(source: String, msg: Message): CompletableFuture<Unit> {
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
        }
    }

    private fun keywordTrackingCallbackCreator(botName: String): ListenerCallback {
        return object : ListenerCallback {
            override fun invoke(source: String, msg: Message): CompletableFuture<Unit> {
                return CompletableFuture.completedFuture(
                        keywordsTracker.track(extractChannelName(source), msg.media, msg.contents.toString(CourseBotImpl.charset)))
            }
        }
    }

    private fun messageCounterCallbackCreator(botName: String): ListenerCallback {
        return object : ListenerCallback {
            override fun invoke(source: String, msg: Message): CompletableFuture<Unit> {
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
        }
    }

    private fun lastMessageCallbackCreator(botName: String): ListenerCallback {
        return object : ListenerCallback {
            override fun invoke(source: String, msg: Message): CompletableFuture<Unit> {
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
        }
    }

    private fun tippingCallbackCreator(botName: String): ListenerCallback {
        return object : ListenerCallback {
            override fun invoke(source: String, msg: Message): CompletableFuture<Unit> {
                return CompletableFuture.supplyAsync {
                    if (isChannelMessage(source)
                            && msg.media == MediaType.TEXT
                            && messageStartsWithTrigger(tippingTrigger, msg))
                        msg.contents.toString(CourseBotImpl.charset).substringAfter("${tippingTrigger!!} ")
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

                        val contentSuffix = msg.contents.toString(CourseBotImpl.charset).substringAfter("${tippingTrigger!!} ")
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
        }
    }

    private fun surveyCallback(botName: String): ListenerCallback {
        return object : ListenerCallback {
            override fun invoke(source: String, msg: Message): CompletableFuture<Unit> {
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
                                    if (answer == msg.contents.toString(CourseBotImpl.charset))
                                        surveyListOfAnswers[i] = Pair(answer, counter + 1)
                                    voterList[id] = answer
                                }
                            }
                        }
                        surveyVoters[userName] = voterList
                    }
                }
            }
        }
    }

    private fun parseChannelCounterEntry(entry: String): Pair<Long, String> {
        return Pair(entry.substringBefore('/').toLong(), entry.substringAfter('/'))
    }


    private fun isChannelMessage(source: String): Boolean {
        return source[0] == '#'
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

    private fun writeToDocument(key: String, value: Any?): CompletableFuture<Unit> {
        if (value != null) {
            return db.document("bots")
                    .update(name)
                    .set(key to value)
                    .execute()
                    .thenApply { Unit }
        }
        return db.document("bots")
                .delete(name, listOf(key))
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

    private fun <T> removeFromList(listName: String, value: T): CompletableFuture<Unit> {
        return readListFromDocument<T>(listName).thenApply {
            it.remove(value)
            it
        }.thenCompose {
            writeToDocument(listName, it)
        }
    }

    private fun <K, V> removeFromMap(mapName: String, key: K): CompletableFuture<Unit> {
        return readMapFromDocument<K, V>(mapName).thenApply {
            it.remove(key)
            it
        }.thenCompose {
            writeToDocument(mapName, it)
        }
    }
}