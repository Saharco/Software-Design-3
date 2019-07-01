package il.ac.technion.cs.softwaredesign

import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.lib.db.Database
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.Message
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import il.ac.technion.cs.softwaredesign.utils.DatabaseAbstraction
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
        val callbacks = listOf(
                lastMessageCallbackCreator(dbBotAbstraction),
                messageCounterCallbackCreator(dbBotAbstraction),
                keywordTrackingCallbackCreator(dbBotAbstraction),
                calculatorCallbackCreator(dbBotAbstraction),
                tippingCallbackCreator(dbBotAbstraction),
                surveyCallbackCreator(dbBotAbstraction))

        return removeListeners(callbacks, token).exceptionally {
            // it's okay if an exception is thrown here
        }.thenCompose {
            addListeners(callbacks, token)
        }
    }

    private fun addListeners(callbacks: List<ListenerCallback>, token: String, index: Int = 0)
            : CompletableFuture<Unit> {
        if (index >= callbacks.size)
            return CompletableFuture.completedFuture(Unit)
        return app.addListener(token, callbacks[index])
                .thenCompose {
                    removeListeners(callbacks, token, index + 1)
                }
    }

    private fun removeListeners(callbacks: List<ListenerCallback>, token: String, index: Int = 0)
            : CompletableFuture<Unit> {
        if (index >= callbacks.size)
            return CompletableFuture.completedFuture(Unit)
        return app.removeListener(token, callbacks[index])
                .thenCompose {
                    removeListeners(callbacks, token, index + 1)
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
            app.login("$password$botName", password)
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

    private fun updateBotsMetadata(botName: String): CompletableFuture<Unit> {
        return db.document("metadata")
                .find("metadata", listOf("bot_counter"))
                .execute()
                .thenApply { it?.getInteger("bot_counter") ?: 0 }
                .thenCompose { counter ->
                    db.document("metadata")
                            .update("metadata")
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
                            .update("metadata")
                            .set("all_bots" to botsList)
                            .execute()
                            .thenApply { Unit }
                }
    }

    private fun calculatorCallbackCreator(dbAbstraction: DatabaseAbstraction): ListenerCallback {
        return object : ListenerCallback {
            override fun invoke(source: String, msg: Message): CompletableFuture<Unit> {
                return CompletableFuture.completedFuture(Unit) //TODO: add this callback
            }
        }
    }

    private fun keywordTrackingCallbackCreator(dbAbstraction: DatabaseAbstraction): ListenerCallback {
        return object : ListenerCallback {
            override fun invoke(source: String, msg: Message): CompletableFuture<Unit> {
                return CompletableFuture.completedFuture(Unit) //TODO: add this callback
            }
        }
    }

    private fun messageCounterCallbackCreator(dbAbstraction: DatabaseAbstraction): ListenerCallback {
        return object : ListenerCallback {
            override fun invoke(source: String, msg: Message): CompletableFuture<Unit> {
                return CompletableFuture.completedFuture(Unit) //TODO: add this callback
            }
        }
    }

    private fun lastMessageCallbackCreator(dbAbstraction: DatabaseAbstraction): ListenerCallback {
        return object : ListenerCallback {
            override fun invoke(source: String, msg: Message): CompletableFuture<Unit> {
                if (!isChannelMessage(source))
                    return CompletableFuture.completedFuture(Unit)
                return dbAbstraction.readMapFromDocument<String, LocalDateTime?>(KEY_MAP_USER_LAST_MESSAGE)
                        .thenCompose { userLastMessageMap ->
                            val currentSeenTime = userLastMessageMap[source]
                            val newSeenTime = msg.created
                            if (currentSeenTime == null || currentSeenTime.isBefore(newSeenTime)) {
                                userLastMessageMap[extractSenderUsername(source)] = newSeenTime
                                dbAbstraction.writeSerializableToDocument(KEY_MAP_USER_LAST_MESSAGE, userLastMessageMap)
                            } else {
                                CompletableFuture.completedFuture(Unit)
                            }
                        }
            }
        }
    }

    private fun tippingCallbackCreator(dbAbstraction: DatabaseAbstraction): ListenerCallback {
        return object : ListenerCallback {
            override fun invoke(source: String, msg: Message): CompletableFuture<Unit> {
                return CompletableFuture.completedFuture(Unit) //TODO: add this callback
            }
        }
    }

    private fun surveyCallbackCreator(dbAbstraction: DatabaseAbstraction): ListenerCallback {
        return object : ListenerCallback {
            override fun invoke(source: String, msg: Message): CompletableFuture<Unit> {
                return CompletableFuture.completedFuture(Unit) //TODO: add this callback
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
}