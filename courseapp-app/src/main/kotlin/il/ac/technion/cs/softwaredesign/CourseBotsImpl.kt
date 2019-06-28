package il.ac.technion.cs.softwaredesign

import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import java.util.concurrent.CompletableFuture

class CourseBotsImpl @Inject constructor(private val app: CourseApp, private val msgFactory: MessageFactory)
    : CourseBots {

    companion object {
        private const val password = "BEEP BOOP I AM A RoBoTz"
    }

    private var botCount: Long = 0
    private val botsMap = mutableMapOf<String, CourseBot>()

    override fun prepare(): CompletableFuture<Unit> {
        return CompletableFuture.completedFuture(Unit) //TODO: change this to store metadata
    }

    override fun start(): CompletableFuture<Unit> {
        return CompletableFuture.completedFuture(Unit) //TODO: change this to load all of the bot's configuration details from storage
    }

    override fun bot(name: String?): CompletableFuture<CourseBot> {
        val botName = name ?: "Anna$botCount"
        val storedBot = botsMap[botName]
        if (storedBot != null)
            return CompletableFuture.completedFuture(storedBot)
        return app.login(botName, password).thenApply { token ->
            botCount ++
            val bot = CourseBotImpl(app, msgFactory, name ?: "Anna0", token)
            botsMap[botName] = bot
            bot
        }
    }

    override fun bots(channel: String?): CompletableFuture<List<String>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}