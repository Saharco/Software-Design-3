package il.ac.technion.cs.softwaredesign

import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.lib.db.Database
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.Message
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import il.ac.technion.cs.softwaredesign.utils.DatabaseAbstraction
import il.ac.technion.cs.softwaredesign.wrappers.KeywordsTracker
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import javax.script.ScriptEngineManager

class CourseBotsImpl @Inject constructor(private val app: CourseApp, private val msgFactory: MessageFactory,
                                         private val db: Database)
    : CourseBots {

    companion object {
        private const val password = "BEEP BOOP I AM A RoBoTz"

        private val charset = Charsets.UTF_8
        private val calculatorEngine = ScriptEngineManager().getEngineByName("JavaScript")

        // String?
        private val KEY_TRIGGER_CALCULATOR = "calculatorTrigger"
        // String?
        private val KEY_TRIGGER_TIPPING = "tippingTrigger"

        // Map: channel -> (username -> count)
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

    override fun prepare(): CompletableFuture<Unit> {
        return CompletableFuture.completedFuture(Unit) //TODO: change this to store metadata if necessary
    }

    override fun start(): CompletableFuture<Unit> {
        return getSystemBots().thenCompose {
            startBots(it)
        }
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
                            }.thenCompose { bot ->
                                getBotToken(botName).thenCompose { token ->
                                    attachListeners(botName, token)
                                }.thenApply { bot }
                            }
                }
    }

    override fun bots(channel: String?): CompletableFuture<List<String>> {
        if (channel == null)
            return getSystemBots()
        return db.document("metadata")
                .find(channel, listOf("bots"))
                .execute()
                .thenApply { document ->
                    if (document == null)
                        ArrayList()
                    else
                        document.getAsList("bots")
                }
    }

    private fun startBots(botNames: List<String>, index: Int = 0): CompletableFuture<Unit> {
        if (index >= botNames.size)
            return CompletableFuture.completedFuture(Unit)
        return startBot(botNames[index]).thenCompose {
            startBots(botNames, index + 1)
        }
    }

    private fun startBot(botName: String): CompletableFuture<Unit> {
        return getBotToken(botName).thenCompose { token ->
            attachListeners(botName, token)
        }
    }

    private fun getSystemBots(): CompletableFuture<List<String>> {
        return db.document("metadata")
                .find("metadata", listOf("all_bots"))
                .execute()
                .thenApply { document ->
                    if (document == null)
                        ArrayList()
                    else
                        document.getAsList("all_bots")
                }
    }

    private fun attachListeners(botName: String, token: String): CompletableFuture<Unit> {
        val dbBotAbstraction = DatabaseAbstraction(db, "bots", botName)
        val callbacks = createBotCallbacks(dbBotAbstraction, token)
        return addListeners(callbacks, token)
    }

    private fun createBotCallbacks(dbAbstraction: DatabaseAbstraction, token: String): List<ListenerCallback> {
        return listOf(
                lastMessageCallbackCreator(dbAbstraction), // Done
                calculatorCallbackCreator(dbAbstraction, token), // Done
                keywordTrackingCallbackCreator(dbAbstraction), // Done
                messageCounterCallbackCreator(dbAbstraction),
                tippingCallbackCreator(dbAbstraction, token),
                surveyCallbackCreator(dbAbstraction))
    }


    private fun addListeners(callbacks: List<ListenerCallback>, token: String, index: Int = 0)
            : CompletableFuture<Unit> {
        if (index >= callbacks.size)
            return CompletableFuture.completedFuture(Unit)
        return app.addListener(token, callbacks[index])
                .thenCompose {
                    addListeners(callbacks, token, index + 1)
                }
    }

    private fun getBotToken(botName: String): CompletableFuture<String> {
        return db.document("bots")
                .find(botName, listOf("token"))
                .execute()
                .thenApply { it?.getAsString("token")!! }
    }

    private fun loadExistingBot(botName: String): CompletableFuture<CourseBot> {
        return getBotToken(botName).thenApply { token ->
            CourseBotImpl(app, db, msgFactory, botName, token)
        }
    }

    private fun createNewBot(botName: String): CompletableFuture<CourseBot> {
        return updateBotsMetadata(botName).thenCompose {
            app.login(botName, password)
        }.thenCompose { token ->
            initializeBotDocument(botName, token).thenApply { token }
        }.thenApply { token ->
            CourseBotImpl(app, db, msgFactory, botName, token)
        }
    }

    private fun initializeBotDocument(botName: String, token: String): CompletableFuture<Unit> {
        return db.document("bots")
                .create(botName, mapOf(
                        "token" to token))
                .execute()
                .thenApply { Unit }
    }

    private fun updateBotsMetadata(botName: String): CompletableFuture<Unit> {
        return db.document("metadata")
                .find("metadata", listOf("bot_counter"))
                .execute()
                .thenApply { it?.getInteger("bot_counter") ?: 0 }
                .thenCompose { counter ->
                    db.document("metadata")
                            .create("metadata")
                            .set("bot_counter" to counter + 1)
                            .execute()
                }.thenCompose {
                    db.document("metadata")
                            .find("metadata", listOf("all_bots"))
                            .execute()
                }.thenApply { document ->
                    val botsList = document?.getAsList("all_bots") ?: ArrayList()
                    botsList.add(botName)
                    botsList
                }.thenCompose { botsList ->
                    db.document("metadata")
                            .create("metadata")
                            .set("all_bots" to botsList)
                            .execute()
                            .thenApply { Unit }
                }
    }

    private fun calculatorCallbackCreator(dbAbstraction: DatabaseAbstraction, token: String): ListenerCallback {
        return object : ListenerCallback {
            override fun invoke(source: String, msg: Message): CompletableFuture<Unit> {
                return dbAbstraction.readString(KEY_TRIGGER_CALCULATOR).thenCompose { trigger ->
                    if (!isChannelMessage(source)
                            || msg.media != MediaType.TEXT
                            || !messageStartsWithTrigger(trigger, msg)) {
                        CompletableFuture.completedFuture(Unit)
                    } else {
                        val calculationResult = calculateExpression(msg, trigger!!)
                        if (calculationResult == null) {
                            CompletableFuture.completedFuture(Unit)
                        } else {
                            msgFactory.create(MediaType.TEXT, calculationResult.toString().toByteArray()).thenCompose {
                                app.channelSend(token, extractChannelName(source)!!, it)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun keywordTrackingCallbackCreator(dbAbstraction: DatabaseAbstraction): ListenerCallback {
        return object : ListenerCallback {
            override fun invoke(source: String, msg: Message): CompletableFuture<Unit> {
                return dbAbstraction.readSerializable(KEY_KEYWORDS_TRACKER, KeywordsTracker()).thenCompose {
                    it.track(extractChannelName(source), msg.media, msg.contents.toString(charset))
                    dbAbstraction.writeSerializable(KEY_KEYWORDS_TRACKER, it)
                }
            }
        }
    }

    //TODO: incomplete callback; need to implement [incrementChannelCounterList]
    private fun messageCounterCallbackCreator(dbAbstraction: DatabaseAbstraction): ListenerCallback {
        return object : ListenerCallback {
            override fun invoke(source: String, msg: Message): CompletableFuture<Unit> {
                if (!isChannelMessage(source))
                    return CompletableFuture.completedFuture(Unit)
                val channelName = extractChannelName(source)!!
                val userName = extractSenderUsername(source)

                return dbAbstraction.readSerializable(KEY_MAP_USER_MESSAGE_COUNTER, HashMap<String, ArrayList<String>>())
                        .thenCompose { userMessageCounterMap ->
                            val channelCounterList = userMessageCounterMap[channelName] ?: arrayListOf()
                            incrementChannelCounterList(dbAbstraction, channelName, channelCounterList, userName).thenApply {
                                userMessageCounterMap[channelName] = channelCounterList
                                dbAbstraction.writeSerializable(KEY_MAP_USER_MESSAGE_COUNTER, userMessageCounterMap)
                            }.thenApply { Unit }
                        }
            }
        }
    }

    private fun incrementChannelCounterList(dbAbstraction: DatabaseAbstraction, channelName: String,
                                            channelCounterList: ArrayList<String>, userName: String)
            : CompletableFuture<Unit> {
        // channel -> List(${counter}+'/'+${username})
        // TODO: implement this
        return dbAbstraction.readSerializable(KEY_MAP_CHANNEL_MOST_ACTIVE_USER, hashMapOf<String, String?>()
        ).thenCompose { mostActiveUser ->
            dbAbstraction.readSerializable(KEY_MAP_CHANNEL_MOST_ACTIVE_USER_MESSAGE_COUNTER, hashMapOf<String, Long?>())
                    .thenCompose { mostActiveUserMessageCount ->
                        var existsFlag = false
                        for (i in 0 until channelCounterList.size) {
                            val (count, otherUser) = parseChannelCounterEntry(channelCounterList[i])
                            if (otherUser != userName) continue
                            existsFlag = true
                            val newCount = count + 1
                            channelCounterList[i] = "$newCount/$userName"
                            tryUpdateMostActiveUser(channelName, newCount, userName, mostActiveUser, mostActiveUserMessageCount)
                        }
                        if (!existsFlag) {
                            channelCounterList.add("1/$userName")
                            tryUpdateMostActiveUser(channelName, 1L, userName, mostActiveUser, mostActiveUserMessageCount)
                        }
                        dbAbstraction.writeSerializable(KEY_MAP_CHANNEL_MOST_ACTIVE_USER_MESSAGE_COUNTER, mostActiveUserMessageCount)
                                .thenApply {
                                    dbAbstraction.writeSerializable(KEY_MAP_CHANNEL_MOST_ACTIVE_USER, mostActiveUser)
                                }.thenApply { Unit }
                    }

        }
    }

    private fun tryUpdateMostActiveUser(channel: String, count: Long, otherUser: String,
                                        channelMostActiveUserMap: HashMap<String, String?>,
                                        channelMostActiveUserMessageCountMap: HashMap<String, Long?>) {
        val currentMostActiveCount = channelMostActiveUserMessageCountMap[channel]
        if (count == currentMostActiveCount) {
            channelMostActiveUserMap[channel] = null
            channelMostActiveUserMessageCountMap[channel] = count
        }
        if (currentMostActiveCount == null || count > currentMostActiveCount) {
            channelMostActiveUserMap[channel] = otherUser
            channelMostActiveUserMessageCountMap[channel] = count
        }
    }

    private fun lastMessageCallbackCreator(dbAbstraction: DatabaseAbstraction): ListenerCallback {
        return object : ListenerCallback {
            override fun invoke(source: String, msg: Message): CompletableFuture<Unit> {
                if (!isChannelMessage(source))
                    return CompletableFuture.completedFuture(Unit)
                return dbAbstraction.readSerializable(KEY_MAP_USER_LAST_MESSAGE, HashMap<String, LocalDateTime?>())
                        .thenCompose { userLastMessageMap ->
                            val currentSeenTime = userLastMessageMap[source]
                            val newSeenTime = msg.created
                            if (currentSeenTime == null || currentSeenTime.isBefore(newSeenTime)) {
                                userLastMessageMap[extractSenderUsername(source)] = newSeenTime
                                dbAbstraction.writeSerializable(KEY_MAP_USER_LAST_MESSAGE, userLastMessageMap)
                            } else {
                                CompletableFuture.completedFuture(Unit)
                            }
                        }
            }
        }
    }

    private fun tippingCallbackCreator(dbAbstraction: DatabaseAbstraction, token: String): ListenerCallback {
        return object : ListenerCallback {
            override fun invoke(source: String, msg: Message): CompletableFuture<Unit> {
                return dbAbstraction.readString(KEY_TRIGGER_TIPPING).thenCompose { trigger ->
                    if (!isChannelMessage(source)
                            || msg.media != MediaType.TEXT
                            || !messageStartsWithTrigger(trigger, msg)) {
                        CompletableFuture.completedFuture(Unit)
                    } else {
                        val msgSuffix = msg.contents.toString(charset).substringAfter("${trigger!!} ")
                        val receiver = msgSuffix.substringAfter(' ')
                        app.isUserInChannel(token, extractChannelName(source)!!, receiver).thenCompose { isMember ->
                            if (isMember == null || !isMember) {
                                CompletableFuture.completedFuture(Unit)
                            } else {
                                dbAbstraction.readSerializable(KEY_MAP_LEDGER, HashMap<String, HashMap<String, Long>>()).thenCompose { ledgerMap ->
                                    performTipping(source, ledgerMap, msg, trigger)
                                    dbAbstraction.writeSerializable(KEY_MAP_LEDGER, ledgerMap)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun performTipping(source: String, ledgerMap: HashMap<String, HashMap<String, Long>>, msg: Message, trigger: String) {
        val channelLedgerMap: HashMap<String, Long>
        val channelName = extractChannelName(source)!!
        if (ledgerMap[channelName] == null) {
            channelLedgerMap = hashMapOf()
            ledgerMap[channelName] = channelLedgerMap
        } else
            channelLedgerMap = ledgerMap[channelName]!!

        val contentSuffix = msg.contents.toString(charset).substringAfter("$trigger ")
        val amount = contentSuffix.substringBefore(' ').toLong()
        val receiverName = contentSuffix.substringAfter(' ')
        var senderBalance = channelLedgerMap[extractSenderUsername(source)]
        var receiverBalance = channelLedgerMap[receiverName]

        if (senderBalance == null)
            senderBalance = 1000L

        if (receiverBalance == null)
            receiverBalance = 1000L
        if (amount in 1..senderBalance) {
            senderBalance -= amount
            receiverBalance += amount
        }
        channelLedgerMap[extractSenderUsername(source)] = senderBalance
        channelLedgerMap[receiverName] = receiverBalance
        ledgerMap[channelName] = channelLedgerMap
    }


    private fun surveyCallbackCreator(dbAbstraction: DatabaseAbstraction): ListenerCallback {
        return object : ListenerCallback {
            override fun invoke(source: String, msg: Message): CompletableFuture<Unit> {
                if (!isChannelMessage(source)
                        || msg.media != MediaType.TEXT) {
                    return CompletableFuture.completedFuture(Unit)
                }
                val messageChannelName = extractChannelName(source)!!
                val userName = extractSenderUsername(source)

                return dbAbstraction.readSerializable(KEY_MAP_SURVEY_VOTERS, hashMapOf<String, HashMap<String, Pair<String, LocalDateTime>>>())
                        .thenCompose { surveyVoters ->
                            val voterList = surveyVoters[userName] ?: hashMapOf()
                            dbAbstraction.readSerializable(KEY_MAP_SURVEY, hashMapOf<String, HashMap<String, ArrayList<Pair<String, Long>>>>())
                                    .thenCompose { surveyMap ->
                                        val channelSurveyMap = surveyMap[messageChannelName]
                                        //change to compose
                                        if (channelSurveyMap != null) {
                                            for ((id, surveyListOfAnswers) in channelSurveyMap) {
                                                if (voterList.containsKey(id)) {
                                                    for ((i, pair) in surveyListOfAnswers.withIndex()) {
                                                        val answer = pair.first
                                                        val counter = pair.second
                                                        if (answer == voterList[id]?.first) {
                                                            surveyListOfAnswers[i] = Pair(answer, counter - 1)
                                                            voterList.remove(id)
                                                        }
                                                    }
                                                }
                                                //surveyMap[id] = surveyListOfAnswers
                                            }
                                            for ((id, surveyListOfAnswers) in channelSurveyMap) {
                                                if (id.startsWith(messageChannelName + "/" + dbAbstraction.id)) {
                                                    for ((i, pair) in surveyListOfAnswers.withIndex()) {
                                                        val answer = pair.first
                                                        val counter = pair.second
                                                        if (answer == msg.contents.toString(charset)) {
                                                            surveyListOfAnswers[i] = Pair(answer, counter + 1)
                                                            voterList[id] = Pair(answer, msg.created)
                                                        }
                                                    }
                                                }
                                            }
                                            surveyMap[messageChannelName] = channelSurveyMap
                                        }
                                        surveyVoters[userName] = voterList
                                        dbAbstraction.writeSerializable(KEY_MAP_SURVEY_VOTERS, surveyVoters)
                                                .thenCompose { dbAbstraction.writeSerializable(KEY_MAP_SURVEY, surveyMap) }
                                    }.thenApply { Unit }
                        }
            }
        }
    }
    private fun messageStartsWithTrigger(trigger: String?, msg: Message): Boolean {
        trigger ?: return false
        val msgContent = msg.contents.toString(charset)
        return msgContent.startsWith(trigger)
    }

    private fun calculateExpression(msg: Message, trigger: String): Int? {
        val expression = msg.contents.toString(charset).substringAfter("${trigger!!} ")
        return try {
            calculatorEngine.eval(expression) as Int
        } catch (e: Exception) {
            null
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
}