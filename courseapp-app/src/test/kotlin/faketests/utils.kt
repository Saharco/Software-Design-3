import il.ac.technion.cs.softwaredesign.CourseApp
import fakes.CourseAppStatistics
import org.junit.jupiter.api.Assertions
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

/**
 *  Data class for representing a user in the system.
 *  Instances of this class are numbered by their creation order
 */
data class User(val username: String, val password: String,
                var token: String? = null,
                val channels: MutableList<String> = mutableListOf(),
                var isAdmin: Boolean = false) {

    companion object {
        var creationCounter: Int = 0
    }

    val count = creationCounter

    init {
        creationCounter++
    }
}

/**
 *   Data class for representing a channel in the system
 *   Instances of this class are numbered by their creation order
 */
data class Channel(val name: String, var totalUsersCount: Int = 1, var onlineUsersCount: Int = 1) {
    companion object {
        var creationCounter: Int = 0
    }

    val count = creationCounter

    init {
        creationCounter++
    }
}

/**
 * Chooses a random amount min<=x<max such that random users will leave random channels x times in total
 */
fun CourseApp.leaveRandomChannels(users: ArrayList<User>,
                                  channels: ArrayList<Channel>, min: Int = 0, max: Int = 20) {
    var leaveAmount = kotlin.random.Random.nextInt(min, max)
    while (leaveAmount > 0) {
        val userIndex = kotlin.random.Random.nextInt(users.size)
        if (users[userIndex].channels.isEmpty()) continue
        val channelIndex = kotlin.random.Random.nextInt(users[userIndex].channels.size)

        if (users[userIndex].token == null ||
                !users[userIndex].channels.contains(channels[channelIndex].name)) continue

        channelPart(users[userIndex].token!!, channels[channelIndex].name).join()

        users[userIndex].channels.remove(channels[channelIndex].name)
        channels[channelIndex].totalUsersCount--
        channels[channelIndex].onlineUsersCount--
        leaveAmount--
    }
}

/**
 * Chooses a random amount min<=x<max such that x logged out users will re-log
 * @param loggedOutUsers: list of indices of the logged out users. Re-logging users' indices will be removed from this list
 * @param users: list of app's users
 * @param channels: list app's channels
 */
fun CourseApp.performRandomRelog(loggedOutUsers: ArrayList<Int>, users: ArrayList<User>,
                                 channels: ArrayList<Channel>, min: Int = 0, max: Int = 12) {
    val relogAmount = kotlin.random.Random.nextInt(min, Math.min(max, loggedOutUsers.size))
    for (i in 0 until relogAmount) {
        val loggedOutUserIndex = kotlin.random.Random.nextInt(loggedOutUsers.size)
        val user = users[loggedOutUsers[loggedOutUserIndex]]

        user.token = login(user.username, user.password).join()

        for (channel in channels)
            if (user.channels.contains(channel.name))
                channel.onlineUsersCount++

        loggedOutUsers.removeAt(loggedOutUserIndex)
    }
}

/**
 * Chooses a random amount min<=x<max such that x users will log out.
 * @return the indices of the users that logged out
 */
fun CourseApp.performRandomLogout(users: ArrayList<User>,
                                  channels: ArrayList<Channel>, min: Int = 12,
                                  max: Int = 25): ArrayList<Int> {
    val loggedOutUsersIndices = ArrayList<Int>()
    var logoutAmount = kotlin.random.Random.nextInt(min, max)
    while (logoutAmount > 0) {
        val chosenUserIndex = kotlin.random.Random.nextInt(0, users.size)
        if (users[chosenUserIndex].token == null) continue

        logout(users[chosenUserIndex].token!!).join()

        users[chosenUserIndex].token = null
        for (channel in channels)
            if (users[chosenUserIndex].channels.contains(channel.name))
                channel.onlineUsersCount--

        loggedOutUsersIndices.add(chosenUserIndex)
        logoutAmount--
    }
    return loggedOutUsersIndices
}

/**
 * Construct a maximum heap from a given collection of items
 * @param collection: collection of items
 * @param comparator: compares two items in the collection
 * @param removePredicate: predicate such that once an i-th maximum does not fulfil it:
 *  rest of the items will not be inserted to the heap
 * @param limit: maximum amount of items in the new heap
 * @return the constructed maximum heap, with a size upto [limit]
 */
fun <T> createMaxHeap(collection: Collection<T>, comparator: Comparator<T>,
                      removePredicate: (T) -> Boolean = { false },
                      limit: Int = 10): PriorityQueue<T> {
    val bigHeap = PriorityQueue<T>(comparator.reversed())
    bigHeap.addAll(collection)
    val heap = PriorityQueue<T>(comparator.reversed())
    var i = limit
    while (i > 0 && bigHeap.isNotEmpty()) {
        val element = bigHeap.poll()
        if (removePredicate(element))
            break
        heap.add(element)
        i--
    }
    return heap
}

/**
 * Chooses a random amount min<=x<max such that random users will join random channels x times in total
 */
fun CourseApp.joinRandomChannels(users: ArrayList<User>,
                                 channels: ArrayList<Channel>, min: Int = 200, max: Int = 400) {
    var joinCount = kotlin.random.Random.nextInt(min, max)
    while (joinCount > 0) {
        val chosenUserIndex = kotlin.random.Random.nextInt(0, users.size)
        val chosenChannelIndex = kotlin.random.Random.nextInt(0, channels.size)
        val chosenChannelName = channels[chosenChannelIndex].name
        if (users[chosenUserIndex].channels.contains(chosenChannelName) ||
                users[chosenUserIndex].token == null) continue

        channelJoin(users[chosenUserIndex].token!!, chosenChannelName).join()

        channels[chosenChannelIndex].totalUsersCount++
        channels[chosenChannelIndex].onlineUsersCount++
        users[chosenUserIndex].channels.add(chosenChannelName)
        joinCount--
    }
}

/**
 * Create channels with random names
 * @param users: list of users in the app. First user in the list is the admin
 * @param channelsAmount: amount of channels to be created
 * @return list of all created channels
 */
fun CourseApp.createRandomChannels(users: ArrayList<User>, channelsAmount: Int = 50):
        ArrayList<Channel> {
    val adminToken = users[0].token!! // assumes the first user in the list to be the admin
    val channels = ArrayList<Channel>()
    for (i in 0 until channelsAmount) {
        val name = "#${UUID.randomUUID().toString().replace('-', '_')}"

        channelJoin(adminToken, name).join()

        channels.add(i, Channel(name))
        users[0].channels.add(name)
    }
    return channels
}

/**
 * Creates & logs in users with a random username+password. All created users' passwords are equal to their username
 * @param usersAmount: amount of users to be created & signed up
 * @return list of all created users
 */
fun CourseApp.performRandomUsersLogin(usersAmount: Int = 80): ArrayList<User> {
    val users = ArrayList<User>()
    for (i in 0 until usersAmount) {
        val name = UUID.randomUUID().toString() // this is both the username & the password
        users.add(i, User(name, name, login(name, name).join()))
    }
    return users
}

/**
 * Verifies the correctness of [CourseAppStatistics] top10 methods
 * @param statistics: course app statistics instance
 * @param users: the users of the app
 * @param channels: the channels of the app
 */
fun verifyQueriesCorrectness(statistics: CourseAppStatistics, users: ArrayList<User>,
                             channels: ArrayList<Channel>) {

    val compareChannelsByUsers = Comparator<Channel> { ch1, ch2 ->
        when {
            ch1.totalUsersCount > ch2.totalUsersCount -> 1
            ch1.totalUsersCount < ch2.totalUsersCount -> -1
            else -> ch2.count - ch1.count
        }
    }

    val compareChannelsByOnlineUsers = Comparator<Channel> { ch1, ch2 ->
        when {
            ch1.onlineUsersCount > ch2.onlineUsersCount -> 1
            ch1.onlineUsersCount < ch2.onlineUsersCount -> -1
            else -> ch2.count - ch1.count
        }
    }
    val compareUsersByChannels = Comparator<User> { user1, user2 ->
        when {
            user1.channels.size > user2.channels.size -> 1
            user1.channels.size < user2.channels.size -> -1
            else -> user2.count - user1.count
        }
    }
    val channelByUsersPredicate: (Channel) -> Boolean = { it.totalUsersCount <= 0 }

    val expectedTop10ChannelsByUsers = ArrayList<String>()
    val heap1 = createMaxHeap(channels, compareChannelsByUsers, channelByUsersPredicate)
    while (heap1.isNotEmpty()) {
        expectedTop10ChannelsByUsers.add(heap1.poll().name)
    }

    val expectedTop10ChannelsByActiveUsers = ArrayList<String>()
    val heap2 = createMaxHeap(channels, compareChannelsByOnlineUsers)
    while (heap2.isNotEmpty()) {
        expectedTop10ChannelsByActiveUsers.add(heap2.poll().name)
    }

    val expectedTop10UsersByChannels = ArrayList<String>()
    val heap3 = createMaxHeap(users, compareUsersByChannels)
    while (heap3.isNotEmpty()) {
        expectedTop10UsersByChannels.add(heap3.poll().username)
    }

    Assertions.assertEquals(expectedTop10ChannelsByUsers, statistics.top10ChannelsByUsers().join())
    Assertions.assertEquals(expectedTop10ChannelsByActiveUsers, statistics.top10ActiveChannelsByUsers().join())
    Assertions.assertEquals(expectedTop10UsersByChannels, statistics.top10UsersByChannels().join())
}

/**
 * Fills an empty list with random strings
 * @param list: empty list of strings to be filled
 * @param amount: number of strings to be created
 * @param maxSize: maximum length of any generated string
 * @param charPool: pool of characters to generate strings with. Alphanumeric + '/' by default
 */
fun populateWithRandomStrings(list: ArrayList<String>, amount: Int = 100,
                              maxSize: Int = 30, charPool: List<Char>? = null) {
    val pool = charPool ?: ('a'..'z') + ('A'..'Z') + ('0'..'9') + '/'
    for (i in 0 until amount) {
        val randomString = (1..maxSize)
                .map { kotlin.random.Random.nextInt(0, pool.size) }
                .map(pool::get)
                .joinToString("")
        list.add(randomString)
    }
}

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