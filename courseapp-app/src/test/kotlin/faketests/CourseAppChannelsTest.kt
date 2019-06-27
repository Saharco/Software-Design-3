import com.authzee.kotlinguice4.getInstance
import com.google.inject.Guice
import il.ac.technion.cs.softwaredesign.CourseApp
import il.ac.technion.cs.softwaredesign.CourseAppInitializer
import il.ac.technion.cs.softwaredesign.CourseAppModule
import il.ac.technion.cs.softwaredesign.exceptions.*
import il.ac.technion.cs.softwaredesign.storage.SecureStorageModule
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CourseAppChannelsTest {

    private val injector = Guice.createInjector(CourseAppFakeTestModule())
    private val courseAppInitializer = injector.getInstance<CourseAppInitializer>()

    init {
        courseAppInitializer.setup().join()
    }

    private val app = injector.getInstance<CourseApp>()


    @Test
    internal fun `joining a channel with an invalid name should throw NameFormatException`() {
        val adminToken = app.login("sahar", "a very strong password").join()

        assertThrows<NameFormatException> {
            app.channelJoin(adminToken, "badName").joinException()
        }

        assertThrows<NameFormatException> {
            app.channelJoin(adminToken, "#ba\$dName").joinException()
        }

        assertThrows<NameFormatException> {
            app.channelJoin(adminToken, "#bad Name").joinException()
        }
    }

    @Test
    internal fun `creating a new channel without administrator authorization should throw UserNotAuthorizedException`() {
        app.login("sahar", "a very strong password").join()
        val notAdminToken = app.login("yuval", "weak password").join()

        assertThrows<UserNotAuthorizedException> {
            app.channelJoin(notAdminToken, "#TakeCare").joinException()
        }
    }

    @Test
    internal fun `administrator can successfully create new channels`() {
        val adminToken = app.login("sahar", "a very strong password").join()
        app.channelJoin(adminToken, "#TakeCare").join()
    }

    @Test
    internal fun `users can successfully join an existing channel`() {
        val adminToken = app.login("sahar", "a very strong password").join()
        val notAdminToken = app.login("yuval", "weak password").join()
        app.channelJoin(adminToken, "#TakeCare").join()
        app.channelJoin(notAdminToken, "#TakeCare").join()
    }

    @Test
    internal fun `attempting to leave a channel a user is not a member of should throw NoSuchEntityException`() {
        val adminToken = app.login("sahar", "a very strong password").join()
        val notAdminToken = app.login("yuval", "weak password").join()
        app.channelJoin(adminToken, "#TakeCare").join()
        assertThrows<NoSuchEntityException> {
            app.channelPart(notAdminToken, "#TakeCare").joinException()
        }
    }

    @Test
    internal fun `attempting to leave a channel that does not exist should throw NoSuchEntityException`() {
        val token = app.login("sahar", "a very strong password").join()
        assertThrows<NoSuchEntityException> {
            app.channelPart(token, "#TakeCare").joinException()
        }
    }

    @Test
    internal fun `channel is destroyed when last user leaves it`() {
        val adminToken = app.login("sahar", "a very strong password").join()
        val notAdminToken = app.login("yuval", "weak password").join()
        app.channelJoin(adminToken, "#TakeCare").join()
        app.channelPart(adminToken, "#TakeCare").join()

        assertThrows<UserNotAuthorizedException> {
            app.channelJoin(notAdminToken, "#TakeCare").joinException()
        }
    }

    @Test
    internal fun `joining a channel more than once has no effect`() {
        val adminToken = app.login("sahar", "a very strong password").join()
        for (i in 1..5) {
            app.channelJoin(adminToken, "#TakeCare").join()
        }
        app.channelPart(adminToken, "#TakeCare").join()

        assertThrows<NoSuchEntityException> {
            app.channelPart(adminToken, "#TakeCare").joinException()
        }
    }

    @Test
    internal fun `trying to appoint an operator as user who's neither operator nor administrator should throw UserNotAuthorizedException`() {
        val adminToken = app.login("sahar", "a very strong password").join()
        val notAdminToken = app.login("yuval", "a weak password").join()
        app.login("victor", "big").join()
        app.channelJoin(adminToken, "#TakeCare").join()

        assertThrows<UserNotAuthorizedException> {
            app.channelMakeOperator(notAdminToken, "#TakeCare", "victor").joinException()
        }
    }

    @Test
    internal fun `trying to appoint another user to an operator as an admin who's not an operator should throw UserNotAuthorizedException`() {
        val adminToken = app.login("sahar", "a very strong password").join()
        val otherToken = app.login("yuval", "a weak password").join()
        app.login("victor", "big").join()
        app.makeAdministrator(adminToken, "yuval").join()
        app.channelJoin(otherToken, "#TakeCare").join()

        assertThrows<UserNotAuthorizedException> {
            app.channelMakeOperator(adminToken, "#TakeCare", "victor").joinException()
        }
    }

    @Test
    internal fun `trying to appoint an operator as a user who's not a member of the channel should throw UserNotAuthorizedException`() {
        val adminToken = app.login("sahar", "a very strong password").join()
        val otherToken = app.login("yuval", "a weak password").join()
        app.makeAdministrator(adminToken, "yuval").join()
        app.channelJoin(otherToken, "#TakeCare").join()

        assertThrows<UserNotAuthorizedException> {
            app.channelMakeOperator(adminToken, "#TakeCare", "sahar").joinException()
        }
    }

    @Test
    internal fun `operator can appoint other channel members to be operators`() {
        val adminToken = app.login("sahar", "a very strong password").join()
        val otherToken = app.login("yuval", "a weak password").join()
        val lastToken = app.login("victor", "big").join()
        app.channelJoin(adminToken, "#TakeCare").join()
        app.channelJoin(otherToken, "#TakeCare").join()
        app.channelJoin(lastToken, "#TakeCare").join()

        assertThrows<UserNotAuthorizedException> {
            app.channelMakeOperator(otherToken, "#TakeCare", "victor").joinException()
        }
        app.channelMakeOperator(adminToken, "#TakeCare", "yuval").join()

        app.channelMakeOperator(otherToken, "#TakeCare", "victor").join()
    }

    @Test
    internal fun `attempting to kick a member from a channel without operator privileges should throw UserNotAuthorizedException`() {
        val adminToken = app.login("sahar", "a very strong password").join()
        val otherToken = app.login("yuval", "a weak password").join()
        app.makeAdministrator(adminToken, "yuval").join()

        app.channelJoin(otherToken, "#TakeCare").join()
        app.channelJoin(adminToken, "#TakeCare").join()

        assertThrows<UserNotAuthorizedException> {
            app.channelKick(adminToken, "#TakeCare", "yuval").joinException()
        }
    }

    @Test
    internal fun `attempting to kick from a channel a member that does not exist should throw NoSuchEntityException`() {
        val adminToken = app.login("sahar", "a very strong password").join()
        app.channelJoin(adminToken, "#TakeCare").join()

        assertThrows<NoSuchEntityException> {
            app.channelKick(adminToken, "#TakeCare", "yuval").joinException()
        }
    }

    @Test
    internal fun `attempting to kick from a channel a member who's not a member of the same channel should throw NoSuchEntityException`() {
        val adminToken = app.login("sahar", "a very strong password").join()
        val otherToken = app.login("yuval", "a weak password").join()
        app.makeAdministrator(adminToken, "yuval").join()

        app.channelJoin(adminToken, "#TakeCare").join()
        app.channelJoin(otherToken, "#TakeCare2").join()

        assertThrows<NoSuchEntityException> {
            app.channelKick(adminToken, "#TakeCare", "yuval").joinException()
        }
    }

    @Test
    internal fun `operator can kick members from a channel`() {
        val adminToken = app.login("sahar", "a very strong password").join()
        val otherToken = app.login("yuval", "a weak password").join()
        app.channelJoin(adminToken, "#TakeCare").join()
        app.channelJoin(otherToken, "#TakeCare").join()

        app.channelKick(adminToken, "#TakeCare", "yuval").join()

        app.isUserInChannel(adminToken, "#TakeCare", "yuval").join()?.let { assertFalse(it) }
    }

    @Test
    internal fun `attempting to kick a member from a channel with operator privileges for another channel should throw UserNotAuthorizedException`() {
        val adminToken = app.login("sahar", "a very strong password").join()
        val otherToken = app.login("yuval", "a weak password").join()
        app.makeAdministrator(adminToken, "yuval").join()

        app.channelJoin(adminToken, "#TakeCare").join()
        app.channelJoin(otherToken, "#TakeCare2").join()

        assertThrows<UserNotAuthorizedException> {
            app.channelKick(adminToken, "#TakeCare2", "yuval").joinException()
        }
    }

    @Test
    internal fun `member & admin can query the total number of members in the channel`() {
        val adminToken = app.login("sahar", "a very strong password").join()
        val token2 = app.login("yuval", "a weak password").join()
        val token3 = app.login("victor", "big").join()
        val channel = "#TakeCare"

        app.channelJoin(adminToken, channel).join()
        assertEquals(1, app.numberOfTotalUsersInChannel(adminToken, channel).join())

        app.channelJoin(token2, channel).join()
        assertEquals(2, app.numberOfTotalUsersInChannel(token2, channel).join())

        app.channelJoin(token3, channel).join()
        assertEquals(3, app.numberOfTotalUsersInChannel(token3, channel).join())

        app.channelPart(adminToken, channel).join()
        assertEquals(2, app.numberOfTotalUsersInChannel(adminToken, channel).join())

        app.channelPart(token3, channel).join()
        assertEquals(1, app.numberOfTotalUsersInChannel(adminToken, channel).join())
    }

    @Test
    internal fun `member & admin can query the number of active members in the channel`() {
        val adminToken = app.login("sahar", "a very strong password").join()
        val token2 = app.login("yuval", "a weak password").join()
        val channelOne = "#TakeCare"
        val channelTwo = "#TakeCare2"

        app.channelJoin(adminToken, channelOne).join()
        app.channelJoin(adminToken, channelTwo).join()

        app.channelJoin(token2, channelOne).join()
        app.channelJoin(token2, channelTwo).join()

        assertEquals(2, app.numberOfActiveUsersInChannel(adminToken, channelOne).join())
        assertEquals(2, app.numberOfActiveUsersInChannel(adminToken, channelTwo).join())

        app.channelPart(adminToken, channelOne).join()

        assertEquals(1, app.numberOfActiveUsersInChannel(adminToken, channelOne).join())
        assertEquals(2, app.numberOfActiveUsersInChannel(adminToken, channelTwo).join())

        app.logout(token2).join()

        assertEquals(0, app.numberOfActiveUsersInChannel(adminToken, channelOne).join())
        assertEquals(1, app.numberOfActiveUsersInChannel(adminToken, channelTwo).join())
    }

    @Test
    internal fun `trying to query number of members in the channel when user is not a member of the channel nor an admin should throw UserNotAuthorizedException`() {
        val nonAdminToken = app.login("alon", "rotubardo")
                .thenCompose { adminToken -> app.channelJoin(adminToken, "#FishNuggets") }
                .thenCompose { app.login("yuval", "the stupido") }.join()

        assertThrows<UserNotAuthorizedException> {
            app.numberOfTotalUsersInChannel(nonAdminToken,"#FishNuggets").joinException()
        }

        assertThrows<UserNotAuthorizedException> {
            app.numberOfActiveUsersInChannel(nonAdminToken,"#FishNuggets").joinException()
        }
    }

    @Test
    fun `checking if user is member of a channel when the user does not exist should return null`() {
        val isYuvalMember = app.login("alon", "rotubardo")
                .thenCompose { adminToken ->
                    app.channelJoin(adminToken, "#FishNuggets").thenApply { adminToken }
                }.thenCompose { adminToken ->
                    app.isUserInChannel(adminToken, "#FishNuggets", "yuval")
                }.join()

        assertNull(isYuvalMember)
    }
}
