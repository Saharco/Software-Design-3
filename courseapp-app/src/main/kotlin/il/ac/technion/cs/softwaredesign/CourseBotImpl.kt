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
import il.ac.technion.cs.softwaredesign.utils.DatabaseAbstraction
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
        private val KEY_MAP_SURVEY = "surveyMap"
        // Map: userName -> HashMap(id -> (answer, date))
        private val KEY_MAP_SURVEY_VOTERS = "surveyVotersMap"

        private val KEY_KEYWORDS_TRACKER = "keywordsTracker"
    }

    val dbAbstraction = DatabaseAbstraction(db, "bots", name)

    override fun join(channelName: String): CompletableFuture<Unit> {
        if (!validChannelName(channelName))
            return CompletableFuture.supplyAsync { throw UserNotAuthorizedException() }
        return app.channelJoin(token, channelName).thenCompose {
            dbAbstraction.readSerializable(KEY_LIST_CHANNELS, ArrayList<String>())
        }.thenCompose { channelsList ->
            if (channelsList.contains(channelName))
                CompletableFuture.completedFuture(false)
            else {
                channelsList.add(channelName)
                dbAbstraction.writeSerializable(KEY_LIST_CHANNELS, channelsList)
                        .thenApply { true }
            }
        }.thenCompose { isAdded ->
            if (isAdded) {
                db.document("metadata")
                        .find(channelName, listOf("bots"))
                        .execute()
                        .thenApply {
                            val botsList = it?.getAsList("bots") as ArrayList? ?: arrayListOf()
                            botsList.add(name)
                            botsList
                        }
            } else {
                CompletableFuture.completedFuture(null as ArrayList<String>?)
            }
        }.thenCompose { list ->
            if (list != null) {
                db.document("metadata")
                        .create(channelName)
                        .set("bots" to list)
                        .execute()
                        .thenApply { Unit }
            } else {
                CompletableFuture.completedFuture(Unit)
            }
        }
    }

    override fun part(channelName: String): CompletableFuture<Unit> {
        return app.channelPart(token, channelName).thenCompose {
            dbAbstraction.removeFromList(KEY_LIST_CHANNELS, channelName)
        }.thenCompose {
            dbAbstraction.removeFromMap<String, ArrayList<String>>(KEY_MAP_LEDGER, channelName)
        }.thenCompose {
            dbAbstraction.removeFromMap<String, ArrayList<String>>(KEY_MAP_USER_MESSAGE_COUNTER, channelName)
        }.thenCompose {
            dbAbstraction.removeFromMap<String, String?>(KEY_MAP_CHANNEL_MOST_ACTIVE_USER, channelName)
        }.thenCompose {
            dbAbstraction.removeFromMap<String, Long?>(KEY_MAP_CHANNEL_MOST_ACTIVE_USER_MESSAGE_COUNTER, channelName)
        }.thenCompose {
            dbAbstraction.readSerializable(KEY_MAP_SURVEY_VOTERS, hashMapOf<String, HashMap<String, Pair<String, LocalDateTime>>>())
        }.thenCompose { surveyVotersMap ->
            for ((userName, userVotes) in surveyVotersMap) {
                for ((id, answerAndDate) in userVotes) {
                    if (id.startsWith("$channelName/$name")) {
                        userVotes.remove(id)
                    }
                }
            }
            dbAbstraction.writeSerializable(KEY_MAP_SURVEY_VOTERS, surveyVotersMap)
        }.thenCompose {
            dbAbstraction.readSerializable(KEY_MAP_SURVEY, hashMapOf<String, ArrayList<Pair<String, Long>>>())
                    .thenCompose {surveyMap ->
                        val channelSurveys = surveyMap[channelName]
                        if (channelSurveys != null) {
                            for ((i, pair) in channelSurveys.withIndex()) {
                                val answer = pair.first
                                channelSurveys[i] = Pair(answer, 0)
                            }
                            dbAbstraction.writeSerializable(KEY_MAP_SURVEY, surveyMap)
                        } else
                            CompletableFuture.completedFuture(Unit)
                    }
        }.thenCompose {
            dbAbstraction.readSerializable(KEY_KEYWORDS_TRACKER, KeywordsTracker())
        }.thenApply {
            it ?: KeywordsTracker()
        }.thenCompose {
            it.remove(channelName)
            dbAbstraction.writeSerializable(KEY_KEYWORDS_TRACKER, it)
        }.thenCompose {
            db.document("metadata")
                    .find(channelName, listOf("bots"))
                    .execute()
                    .thenApply {
                        val botsList = it?.getAsList("bots") ?: arrayListOf()
                        botsList.remove(name)
                        botsList
                    }
        }.thenCompose { list ->
            if (list != null) {
                db.document("metadata")
                        .create(channelName)
                        .set("bots" to list)
                        .execute()
                        .thenApply { Unit }
            } else {
                CompletableFuture.completedFuture(Unit)
            }
        }
    }

    override fun channels(): CompletableFuture<List<String>> {
        return dbAbstraction.readSerializable(KEY_LIST_CHANNELS, ArrayList<String>()).thenApply {
            it as List<String>
        }
    }

    override fun beginCount(channel: String?, regex: String?, mediaType: MediaType?): CompletableFuture<Unit> {
        return dbAbstraction.readSerializable(KEY_KEYWORDS_TRACKER, KeywordsTracker()).thenApply {
            it ?: KeywordsTracker()
        }.thenCompose {
            it[channel, mediaType] = regex
            dbAbstraction.writeSerializable(KEY_KEYWORDS_TRACKER, it)
        }
    }

    override fun count(channel: String?, regex: String?, mediaType: MediaType?): CompletableFuture<Long> {
        return dbAbstraction.readSerializable(KEY_KEYWORDS_TRACKER, KeywordsTracker()).thenApply {
            it ?: KeywordsTracker()
        }.thenApply {
            it[channel, mediaType, regex]
        }
    }

    override fun setCalculationTrigger(trigger: String?): CompletableFuture<String?> {
        return dbAbstraction.readString(KEY_TRIGGER_CALCULATOR).thenCompose {
            val prevTrigger = it
            dbAbstraction.writePrimitive(KEY_TRIGGER_CALCULATOR, trigger).thenApply { prevTrigger }
        }
    }

    override fun setTipTrigger(trigger: String?): CompletableFuture<String?> {
        return dbAbstraction.readString(KEY_TRIGGER_TIPPING).thenCompose {
            val prevTrigger = it
            dbAbstraction.writePrimitive(KEY_TRIGGER_TIPPING, trigger).thenApply { prevTrigger }
        }
    }

    override fun seenTime(user: String): CompletableFuture<LocalDateTime?> {
        return dbAbstraction.readSerializable(KEY_MAP_USER_LAST_MESSAGE, HashMap<String, LocalDateTime>()).thenApply {
            it[user]
        }
    }

    override fun mostActiveUser(channel: String): CompletableFuture<String?> {
        return dbAbstraction.readSerializable(KEY_LIST_CHANNELS, ArrayList<String>()).thenApply {
            if (!it.contains(channel))
                throw NoSuchEntityException()
        }.thenCompose {
            dbAbstraction.readSerializable(KEY_MAP_CHANNEL_MOST_ACTIVE_USER, HashMap<String, String?>()).thenApply {
                it[channel]
            }
        }
    }

    override fun richestUser(channel: String): CompletableFuture<String?> {
        return dbAbstraction.readSerializable(KEY_LIST_CHANNELS, ArrayList<String>()).thenApply {
            if (!it.contains(channel))
                throw NoSuchEntityException()
        }.thenCompose {
            dbAbstraction.readSerializable(KEY_MAP_LEDGER, HashMap<String, HashMap<String, Long>>())
        }.thenApply { ledgerMap ->
            val channelLedger = ledgerMap[channel]
            if (channelLedger == null) {
                null
            } else {
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
        return dbAbstraction.readSerializable(KEY_LIST_CHANNELS, ArrayList<String>()).thenCompose {
            if (!it.contains(channel))
                throw NoSuchEntityException()
            msgFactory.create(MediaType.TEXT, question.toByteArray(charset)).thenCompose { message ->
                //TODO: Send this message in the channel!!!
                app.channelSend(token, channel, message).thenCompose {
                    val identifier = generateSurveyIdentifier(channel)
                    val newSurveyList = arrayListOf<Pair<String, Long>>()
                    for (answer in answers)
                        newSurveyList.add(Pair(answer, 0L))
                    dbAbstraction.readSerializable(KEY_MAP_SURVEY, HashMap<String, ArrayList<Pair<String, Long>>>()).thenApply {
                        it[identifier] = newSurveyList
                        it
                    }.thenApply {
                        dbAbstraction.writeSerializable(KEY_MAP_SURVEY, it)
                        identifier
                    }
                }
            }
        }
    }

    private fun generateSurveyIdentifier(channel: String) = "$channel/$name/${LocalDateTime.now()}"

    override fun surveyResults(identifier: String): CompletableFuture<List<Long>> {
        return dbAbstraction.readSerializable(KEY_MAP_SURVEY, HashMap<String, ArrayList<Pair<String, Long>>>()).thenApply { surveyMap ->
            val resultPairs = surveyMap[identifier] ?: throw NoSuchEntityException()
            val countResultList = arrayListOf<Long>()
            for ((_, count) in resultPairs) {
                countResultList.add(count)
            }
            countResultList
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