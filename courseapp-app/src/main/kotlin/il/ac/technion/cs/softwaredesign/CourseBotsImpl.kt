package il.ac.technion.cs.softwaredesign

import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.lib.db.Database
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import java.util.concurrent.CompletableFuture

class CourseBotsImpl @Inject constructor(private val app: CourseApp, private val msgFactory: MessageFactory,
                                         private val db: Database)
    : CourseBots {

    companion object {
        private const val password = "BEEP BOOP I AM A RoBoTz"
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
                            .thenApply { Pair(botName, it != null) }
                }.thenCompose { (botName, botExists) ->
                    if (botExists) {
                        loadExistingBot(botName)
                    } else {
                        createNewBot(botName)
                    }
                }
    }

    private fun loadExistingBot(botName: String): CompletableFuture<CourseBot> {
        return db.document("bots")
                .find(botName, listOf("token"))
                .execute()
                .thenApply { it?.getAsString("token")!! }
                .thenApply { token ->
                    CourseBotImpl(app, msgFactory, botName, token)
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
            CourseBotImpl(app, msgFactory, botName, token)
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
}