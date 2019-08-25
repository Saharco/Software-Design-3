package il.ac.technion.cs.softwaredesign.tests

import com.natpryce.hamkrest.*
import il.ac.technion.cs.softwaredesign.CourseApp
import il.ac.technion.cs.softwaredesign.CourseBot
import il.ac.technion.cs.softwaredesign.CourseBots
import il.ac.technion.cs.softwaredesign.ListenerCallback
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.Message
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import il.ac.technion.cs.softwaredesign.tests.GlobalUtils.callbackMap
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.function.ThrowingSupplier
import java.io.BufferedReader
import java.io.FileReader
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import kotlin.streams.toList

// This should be standard.
val isTrue = equalTo(true)
val isFalse = equalTo(false)

object GlobalUtils{
    internal val callbackMap = HashMap<String, ListenerCallback>()
}

fun <T> containsElementsInOrder(vararg elements: T): Matcher<Collection<T>> {
    val perElementMatcher = object : Matcher.Primitive<Collection<T>>() {
        override fun invoke(actual: Collection<T>): MatchResult {
            elements.zip(actual).forEach {
                if (it.first != it.second)
                    return MatchResult.Mismatch("${it.first} does not equal ${it.second}")
            }
            return MatchResult.Match
        }

        override val description = "is ${describe(elements)}"
        override val negatedDescription = "is not ${describe(elements)}"
    }
    return has(Collection<T>::size, equalTo(elements.size)) and perElementMatcher
}

// This is a tiny wrapper over assertTimeoutPreemptively which makes the syntax slightly nicer.
fun <T> runWithTimeout(timeout: Duration, executable: () -> T): T =
        assertTimeoutPreemptively(timeout, ThrowingSupplier(executable))

/**
 * Perform [CompletableFuture.join], and if an exception is thrown, unwrap the [CompletionException] and throw the
 * causing exception.
 */
fun <T> CompletableFuture<T>.joinException(): T {
    try {
        return this.join()
    } catch (e: CompletionException) {
        throw e.cause!!
    }
}


enum class UserCsvIdx(val idx: Int) {
    USER_NAME_IDX(0),
    PASS_IDX(1),
    IS_LOGGED_OUT_IDX(2),
    HAS_LISTENER_IDX(3)
}

data class User(val id: String,
                val pass: String,
                val isLoggedOut: Boolean,
                val hasListener: Boolean) {
    var token: String = ""
}

data class ChannelUserEntry(val channel: String,
                            val user: String,
                            val isOperator: Boolean)

enum class ChannelUserCsvIdx(val idx: Int) {
    CHANNEL_IDX(0),
    USER_NAME_IDX(1),
    IS_OPERATOR_IDX(2)
}


data class UserMapEntry(val token: String, val messages : ArrayList<Pair<String, Message>>)


data class TestData(val userMap : Map<String,UserMapEntry>,val bots : Map<String,CourseBot>)

data class MessageEntry(val source: String, val mediaType : MediaType,
                        val target: String,
                        val content : String)

data class BotEntry(val name : String, val channels : ArrayList<String>)

fun readUsersCsv(fileName: String): List<User> {
    var userData = ArrayList<User>()
    var line: String?
    var fileReader = BufferedReader(FileReader(fileName))
    fileReader.readLine()
    line = fileReader.readLine()
    while (line != null) {

        val tokens = line.split(',') // TODO: Use a CSV parsing library. opencsv looks OK: https://mvnrepository.com/artifact/com.opencsv/opencsv/4.5
        if (tokens.size > 0) {
            val user = User(
                    id = tokens[UserCsvIdx.USER_NAME_IDX.idx],
                    pass = tokens[UserCsvIdx.PASS_IDX.idx],
                    isLoggedOut = tokens[UserCsvIdx.IS_LOGGED_OUT_IDX.idx] == "1",
                    hasListener = tokens[UserCsvIdx.HAS_LISTENER_IDX.idx] == "1"
            )
            userData.add(user)
        }
        line = fileReader.readLine()
    }
    return userData
}

fun readChannelsCsv(fileName: String): List<ChannelUserEntry> {
    var channelUserData = ArrayList<ChannelUserEntry>()
    var line: String?
    var fileReader = BufferedReader(FileReader(fileName))
    fileReader.readLine()
    line = fileReader.readLine()
    while (line != null) {

        val tokens = line.split(',')
        if (tokens.size > 0) {
            val user = ChannelUserEntry(
                    channel = tokens[ChannelUserCsvIdx.CHANNEL_IDX.idx],
                    user = tokens[ChannelUserCsvIdx.USER_NAME_IDX.idx],
                    isOperator = tokens[ChannelUserCsvIdx.IS_OPERATOR_IDX.idx] == "1"
            )
            channelUserData.add(user)
        }
        line = fileReader.readLine()
    }
    return channelUserData
}

fun readMessagesCsv(fileName: String) : List<MessageEntry>{
    val messagesData = ArrayList<MessageEntry>()
    var line: String?
    var messageEntry : MessageEntry? = null
    val fileReader = BufferedReader(FileReader(fileName))
    line = fileReader.readLine()
    var tokens: List<String>
    line = fileReader.readLine()
    while (line != null) {

        tokens = line.split(',')
        if (tokens.size > 0) {
            messageEntry = MessageEntry(source = tokens[0],
                    mediaType = MediaType.valueOf(tokens[1]),
                    target = tokens[2],
                    content=tokens[3])

            messagesData.add(messageEntry)
        }
        line = fileReader.readLine()
    }

    return messagesData
}

fun readBotCsv(fileName: String) : List<BotEntry>
{


    val botMap = HashMap<String, ArrayList<String>>()
    var line: String?
    var botEntry : BotEntry? = null
    val fileReader = BufferedReader(FileReader(fileName))
    line = fileReader.readLine()
    var tokens: List<String>
    val botList = ArrayList<String>()
    line = fileReader.readLine()
    while (line != null) {

        tokens = line.split(',')
        if (tokens.size > 0) {
            val name = tokens[0]
            if(!botMap.containsKey(name))
            {
                botList.add(name)
                botMap[name] = ArrayList()
            }
            botMap[name]?.add(tokens[1])
        }
        line = fileReader.readLine()
    }

   return botList.stream().map{name->BotEntry(name,botMap[name]!!)}.toList()
}

fun getPathOfFile(fileName: String): String {
    return object {}.javaClass.classLoader.getResource(fileName).path
}

/**
 * Load the data from specified testfile without sending the messages
 */
fun loadDataForTestWithoutMessages(courseApp: CourseApp, baseTestName: String, courseBots: CourseBots):  TestData {
    val userData = readUsersCsv(getPathOfFile(baseTestName + "_users.csv"))
    val channelUserData = readChannelsCsv(getPathOfFile(baseTestName + "_channels_to_user.csv"))
    val botData = readBotCsv(getPathOfFile(baseTestName + "_bots.csv"))

    val tokenMap = HashMap<String, String>()
    val messageMap = HashMap<String, ArrayList<Pair<String, Message>>>()

    tokenMap.put("MainAdmin", courseApp.login("MainAdmin", "Password").join())

    initChannels(channelUserData, tokenMap, courseApp, messageMap)

    loginUsers(userData, courseApp, tokenMap)

    addUsersToChannels(channelUserData, courseApp, tokenMap)

    val bots = insertBots(botData, courseBots)

    doLogouts(userData, courseApp, tokenMap, messageMap)

    val retMap = buildUserEntryMap(tokenMap, messageMap)
    val testMap = TestData(retMap, bots)
    callbackMap.clear()
    return testMap

}
private fun insertBots(botData : List<BotEntry>, courseBots: CourseBots):HashMap<String,CourseBot>
{
    val bots = HashMap<String,CourseBot>()
    for(bot in botData)
    {
        val newBot = courseBots.bot(bot.name).join()
        bot.channels.forEach{chn->newBot.join(chn).join()}
        bots[bot.name] = newBot
    }
    return bots
}


private fun buildUserEntryMap(tokenMap: HashMap<String, String>, messageMap: HashMap<String, ArrayList<Pair<String, Message>>>): HashMap<String, UserMapEntry> {
    val retMap = HashMap<String, UserMapEntry>()
    tokenMap.forEach { userId, token ->
        retMap.put(userId, UserMapEntry(token, messageMap.getOrDefault(userId, ArrayList<Pair<String, Message>>())))
    }
    return retMap
}

private fun addListeners(userList : List<User>, tokenMap: HashMap<String, String>, messageMap: HashMap<String, ArrayList<Pair<String, Message>>>, courseApp: CourseApp) {
    var cmpl = CompletableFuture.completedFuture(Unit)

    for(user in userList)
    {
        if(!user.hasListener)
            continue
        messageMap[user.id] =  ArrayList()
        cmpl = cmpl.thenCompose{
            courseApp.addListener(tokenMap[user.id]!!, getListenerForUser(user, messageMap))

        }
    }

    cmpl.join()
}

private fun getListenerForUser(user : User, messageMap: HashMap<String, ArrayList<Pair<String, Message>>>) : ListenerCallback
{
    if(!callbackMap.containsKey(user.id))
        callbackMap[user.id]= {
            source,message ->
            messageMap[user.id]!!.add(Pair(source,message))
            CompletableFuture.completedFuture(Unit)
        }

    return callbackMap[user.id]!!
}

private fun getListenerForChannelAdmin(channel: String, messageMap: HashMap<String, ArrayList<Pair<String, Message>>>) : ListenerCallback
{
    val channelAdmin = getAdminOfChannel(channel)
    if(!callbackMap.containsKey(channelAdmin))
    {
        if(!messageMap.containsKey(channelAdmin))
        {
            messageMap[channelAdmin]=ArrayList()
        }
        callbackMap[channelAdmin]={
            source,message->
            val sender = source.split("@".toRegex())[1].substring(0,4)
            if(sender.compareTo("User") != 0)
            {
                messageMap[channelAdmin]?.add(Pair(source,message))
            }
            CompletableFuture.completedFuture(Unit)
        }
    }
    return callbackMap[channelAdmin]!!
}

fun doSurvey(surveyName : String,  messageFactory: MessageFactory, courseApp: CourseApp,tokenMap: HashMap<String, String>)
{
    sendMessagesByFile(surveyName+"_survey.csv",messageFactory,courseApp,tokenMap)
}

fun sendMessagesByTest(testName : String, messageFactory: MessageFactory, courseApp: CourseApp, userEntryMap: Map<String, UserMapEntry>)
{
    val tokenMap = HashMap<String,String>()
    userEntryMap.keys.forEach{userId->tokenMap[userId]=userEntryMap[userId]!!.token}
    sendMessagesByTest(testName,messageFactory,courseApp,tokenMap)
}

fun sendMessagesByTest(testName : String, messageFactory: MessageFactory, courseApp: CourseApp, tokenMap: HashMap<String, String>)
{
    sendMessagesByFile(testName+"_messages.csv",messageFactory,courseApp,tokenMap)
}

fun sendTipsByTest(testName : String, messageFactory: MessageFactory, courseApp: CourseApp, userEntryMap: Map<String, UserMapEntry>)
{
    val tokenMap = HashMap<String,String>()
    userEntryMap.keys.forEach{userId->tokenMap[userId]=userEntryMap[userId]!!.token}
    sendTipsByTest(testName,messageFactory,courseApp,tokenMap)
}

fun sendTipsByTest(testName : String, messageFactory: MessageFactory, courseApp: CourseApp, tokenMap: HashMap<String, String>)
{
    sendMessagesByFile(testName+"_tips.csv",messageFactory,courseApp,tokenMap)
}

fun sendMessagesByFile(fileName : String, messageFactory: MessageFactory, courseApp: CourseApp,userEntryMap: Map<String,UserMapEntry>)
{
    val messages = readMessagesCsv(getPathOfFile(fileName))
    val tokenMap = HashMap<String,String>()
    userEntryMap.keys.forEach{userId->tokenMap[userId]=userEntryMap[userId]!!.token}
    return sendMessages(messages , messageFactory,courseApp, tokenMap)
}

fun sendMessagesByFile(fileName : String, messageFactory: MessageFactory, courseApp: CourseApp,tokenMap: HashMap<String, String>)
{
    val messages = readMessagesCsv(getPathOfFile(fileName))
    return sendMessages(messages , messageFactory,courseApp, tokenMap)
}

internal fun sendMessages(messages: List<MessageEntry>, messageFactory: MessageFactory, courseApp: CourseApp, tokenMap: HashMap<String, String>) {
    var cmpl: CompletableFuture<Unit> = CompletableFuture.completedFuture(Unit)
    messages.stream().forEach {
        val target = it.target
        val message = messageFactory.create(it.mediaType, it.content.toByteArray(Charsets.UTF_8)).join()
        val current = it
        if (target[0] == '#')
            cmpl = cmpl.thenCompose { courseApp.channelSend(tokenMap[current.source]!!, current.target, message) }
        else
            cmpl = cmpl.thenCompose { courseApp.privateSend(tokenMap[current.source]!!, current.target, message) }
    }
    cmpl.join()
}



private fun addUsersToChannels(channelUserData: List<ChannelUserEntry>, courseApp: CourseApp, tokenMap: HashMap<String, String>) {
    channelUserData.stream().forEach {
        courseApp.channelJoin(tokenMap[it.user]!!, it.channel).join()
        if (it.isOperator) {
            val channelFirstUserName = getAdminOfChannel(it.channel)
            courseApp.channelMakeOperator(tokenMap[channelFirstUserName]!!, it.channel, it.user).join()
        }
    }
}

private fun loginUsers(userData: List<User>, courseApp: CourseApp, tokenMap: HashMap<String, String>) {
    for (user in userData) {
        val token = courseApp.login(user.id, user.pass).join()
        tokenMap.put(user.id, token)
    }
}

private fun initChannels(channelUserData: List<ChannelUserEntry>, tokenMap: HashMap<String, String>, courseApp: CourseApp
    ,messageMap: HashMap<String, ArrayList<Pair<String, Message>>>) {
    val uniqueChannels = channelUserData.stream().map { entry -> entry.channel }.distinct().toList()
    uniqueChannels.stream().forEach {
        initChannel(it, tokenMap, courseApp)
        courseApp.addListener(tokenMap[getAdminOfChannel(it)]!!,getListenerForChannelAdmin(it,messageMap))

    }
}

private fun doLogouts(userData: List<User>, courseApp: CourseApp, tokenMap: HashMap<String, String>, messageMap : HashMap<String, ArrayList<Pair<String, Message>>>) {
    userData.stream().filter({ u -> u.isLoggedOut }).forEach { u ->

        val cmpl = if (u.hasListener) {
            courseApp.removeListener(tokenMap[u.id]!!, getListenerForUser(u, messageMap))
        } else
            CompletableFuture.completedFuture(Unit)
        cmpl.thenCompose{courseApp.logout(tokenMap[u.id]!!)}.join()
    }
}

private fun initChannel(channel: String, tokenMap: HashMap<String, String>, courseApp: CourseApp) {
    val channelFirstUserName = getAdminOfChannel(channel)
    tokenMap.put(channelFirstUserName, courseApp.login(channelFirstUserName, "Password2").join())
    courseApp.makeAdministrator(tokenMap["MainAdmin"]!!, channelFirstUserName).join()
    courseApp.channelJoin(tokenMap[channelFirstUserName]!!, channel).join()

}

fun getAdminOfChannel(channel: String) = channel.substring(1) + "_Admin"

fun getDefaultCallback() : ListenerCallback = {_,_->CompletableFuture.completedFuture(Unit)}


fun assertWithTimeout(executable: () -> Unit, timeout: Duration): Unit =
        runWithTimeout(timeout, executable)

fun assertWithTimeout(executable: () -> Unit): Unit =
        runWithTimeout(Duration.ofSeconds(1000), executable)

fun byteArrayToString(byteArray : ByteArray?) : String?
{
    return String(byteArray!!,Charsets.UTF_8)
}
fun stringToByteArray(string : String) : ByteArray
{
    return string.toByteArray(Charsets.UTF_8)
}