import com.authzee.kotlinguice4.getInstance
import com.google.inject.Guice
import il.ac.technion.cs.softwaredesign.CourseApp
import il.ac.technion.cs.softwaredesign.CourseAppInitializer
import fakes.CourseAppStatistics
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class CourseAppStatisticsTest {
    private val injector = Guice.createInjector(CourseAppFakeTestModule())
    private val courseAppInitializer = injector.getInstance<CourseAppInitializer>()
    private val messageFactory = injector.getInstance<MessageFactory>()
    private val app = injector.getInstance<CourseApp>()
    private val statistics = injector.getInstance<CourseAppStatistics>()

    init {
        courseAppInitializer.setup().join()
    }

    @Test
    internal fun `can query total users when no users exist yet`() {
        assertEquals(0, statistics.totalUsers().join())
    }

    @Test
    internal fun `can query logged in users when no users are logged in`() {
        assertEquals(0, statistics.loggedInUsers().join())
    }

    @Test
    internal fun `adding distinct users to the system properly increases total number of users`() {
        val token = app.login("sahar", "a very strong password").join()
        assertEquals(1, statistics.totalUsers().join())

        app.login("yuval", "a weak password").join()
        assertEquals(2, statistics.totalUsers().join())

        app.logout(token).join()

        assertEquals(2, statistics.totalUsers().join())
    }

    @Test
    internal fun `logging in & out changes the amount of logged in users accordingly`() {
        val token1 = app.login("sahar", "a very strong password").join()
        val token2 = app.login("yuval", "a weak password").join()

        assertEquals(2, statistics.loggedInUsers().join())

        app.logout(token1).join()
        assertEquals(1, statistics.loggedInUsers().join())

        app.logout(token2).join()
        assertEquals(0, statistics.loggedInUsers().join())

        app.login("sahar", "a very strong password").join()
        assertEquals(1, statistics.loggedInUsers().join())
    }

    @Test
    internal fun `querying top 10 when there are no channels should return an empty list`() {
        val list1 = statistics.top10ChannelsByUsers().join()
        val list2 = statistics.top10ActiveChannelsByUsers().join()
        val list3 = statistics.top10UsersByChannels().join()
        val list4 = statistics.top10ChannelsByMessages().join()

        assertEquals(0, list1.size)
        assertEquals(0, list2.size)
        assertEquals(0, list3.size)
        assertEquals(0, list4.size)
    }

    @Test
    internal fun `top 10 channel list does secondary sorting by creation order`() {
        val adminToken = app.login("sahar", "sahar").join()
        val nonAdminToken = app.login("yuval", "bobcorn").join()
        app.makeAdministrator(adminToken, "yuval").join()

        app.channelJoin(adminToken, "#TakeCare").join()
        app.channelJoin(nonAdminToken, "#TakeCare2").join()

        assertEquals(listOf("#TakeCare", "#TakeCare2"), statistics.top10ChannelsByUsers().join())
    }

    @Test
    internal fun `top 10 channel list counts only logged in users`() {
        val adminToken = app.login("sahar", "sahar").join()
        val nonAdminToken = app.login("yuval", "bobcorn").join()
        app.makeAdministrator(adminToken, "yuval").join()

        app.channelJoin(adminToken, "#TakeCare").join()
        app.channelJoin(nonAdminToken, "#TakeCare2").join()
        app.logout(nonAdminToken).join()

        assertEquals(listOf("#TakeCare", "#TakeCare2"), statistics.top10ActiveChannelsByUsers().join())
    }

    @Test
    internal fun `top 10 user list does secondary sorting by registration order`() {
        val adminToken = app.login("sahar", "sahar").join()
        val nonAdminToken = app.login("yuval", "bobcorn").join()
        app.makeAdministrator(adminToken, "yuval").join()
        app.channelJoin(adminToken, "#TakeCare").join()
        app.channelJoin(nonAdminToken, "#TakeCare2").join()

        assertEquals(listOf("sahar", "yuval"), statistics.top10UsersByChannels().join())
    }

    /**
     * Tests correctness of the following functions via extensive load testing:
     * - [CourseAppStatistics.top10ChannelsByUsers]
     * - [CourseAppStatistics.top10ActiveChannelsByUsers]
     * - [CourseAppStatistics.top10UsersByChannels]
     * Assertions are made with [verifyQueriesCorrectness]
     */
    @Test
    internal fun `load test - top 10 queries return the expected results`() {
        // create a lot of users
        val users = app.performRandomUsersLogin()

        // create a lot of channels
        val channels = app.createRandomChannels(users)

        // make some users join some channels & check correctness
        app.joinRandomChannels(users, channels)
        verifyQueriesCorrectness(statistics, users, channels)

        // log out with some portion of users & check correctness

        val loggedOutUsersIndices = app.performRandomLogout(users, channels)
        verifyQueriesCorrectness(statistics, users, channels)

        // log back in with some users & check correctness
        app.performRandomRelog(loggedOutUsersIndices, users, channels)
        verifyQueriesCorrectness(statistics, users, channels)

        // leave channels with some portion of users & check correctness
        app.leaveRandomChannels(users, channels)
        verifyQueriesCorrectness(statistics, users, channels)
    }

    @Test
    internal fun `can query pending messages when there are no pending messages`() {
        assertEquals(0, statistics.pendingMessages().join())
    }

    @Test
    internal fun `can query channel messages when there are no channel messages`() {
        assertEquals(0, statistics.channelMessages().join())
    }

    @Test
    internal fun `top 10 channels by messages does secondary sort by creation time`() {
        val adminToken = app.login("alon", "******").join()
        app.channelJoin(adminToken, "#channel1").thenCompose {
            app.channelJoin(adminToken, "#channel2")
        }.thenCompose {
            app.channelJoin(adminToken, "#channel3")
        }.join()

        messageFactory.create(MediaType.PICTURE, "Fish.jpg".toByteArray())
                .thenCompose { message ->
                    app.channelSend(adminToken, "#channel3", message)
                }.join()

        messageFactory.create(MediaType.PICTURE, "Fish(2).jpg".toByteArray())
                .thenCompose { message ->
                    app.channelSend(adminToken, "#channel3", message)
                }.join()

        messageFactory.create(MediaType.PICTURE, "Fish(3).jpg".toByteArray())
                .thenCompose { message ->
                    app.channelSend(adminToken, "#channel2", message)
                }.join()

        messageFactory.create(MediaType.PICTURE, "Fish(4).jpg".toByteArray())
                .thenCompose { message ->
                    app.channelSend(adminToken, "#channel1", message)
                }.join()

        messageFactory.create(MediaType.PICTURE, "Fish(5).jpg".toByteArray())
                .thenCompose { message ->
                    app.channelSend(adminToken, "#channel1", message)
                }.join()

        assertEquals(listOf("#channel1", "#channel3", "#channel2"), statistics.top10ChannelsByMessages().join())
    }
}