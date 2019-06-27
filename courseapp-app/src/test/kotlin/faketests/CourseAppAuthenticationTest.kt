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

class CourseAppAuthenticationTest {

    private val injector = Guice.createInjector(CourseAppFakeTestModule())
    private val courseAppInitializer = injector.getInstance<CourseAppInitializer>()

    init {
        courseAppInitializer.setup().join()
    }

    private val app = injector.getInstance<CourseApp>()

    @Test
    internal fun `user successfully logged in after login`() {
        val token = app.login("sahar", "a very strong password").join()

        assertEquals(app.isUserLoggedIn(token, "sahar").join(), true)
    }

    @Test
    internal fun `attempting login twice without logout should throw UserAlreadyLoggedInException`() {
        app.login("sahar", "a very strong password").join()

        assertThrows<UserAlreadyLoggedInException> {
            app.login("sahar", "a very strong password").joinException()
        }
    }

    @Test
    internal fun `creating two users with same username should throw NoSuchEntityException`() {
        app.login("sahar", "a very strong password").join()
        assertThrows<NoSuchEntityException> {
            app.login("sahar", "weak password").joinException()
        }
    }

    @Test
    internal fun `using token to check login session after self's login session expires should throw InvalidTokenException`() {
        val token = app.login("sahar", "a very strong password")
                .thenCompose { token -> app.login("yuval", "popcorn").thenApply { token } }
                .thenCompose { token -> app.logout(token).thenApply { token } }.join()

        assertThrows<InvalidTokenException> {
            app.isUserLoggedIn(token, "yuval").joinException()
        }
    }

    @Test
    internal fun `logging out with an invalid token should throw InvalidTokenException`() {
        var token = "invalid token"
        assertThrows<InvalidTokenException> {
            app.logout(token).joinException()
        }

        token = app.login("sahar", "a very strong password").join()
        app.logout(token).join()

        assertThrows<InvalidTokenException> {
            app.logout(token).joinException()
        }
    }

    @Test
    internal fun `two different users should have different tokens`() {
        val token1 = app.login("sahar", "a very strong password").join()
        val token2 = app.login("yuval", "popcorn").join()
        assertTrue(token1 != token2)
    }

    @Test
    internal fun `checking if user is logged in when they are not should return false`() {
        val token = app.login("sahar", "a very strong password").join()
        val otherToken = app.login("yuval", "popcorn").join()
        app.logout(otherToken).join()
        assertEquals(app.isUserLoggedIn(token, "yuval").join(), false)
    }

    @Test
    internal fun `checking if user is logged in when they dont exist should return null`() {
        val token = app.login("sahar", "a very strong password").join()
        assertNull(app.isUserLoggedIn(token, "yuval").join())
    }

    @Test
    internal fun `load test - system can hold lots of distinct users and tokens`() {
        val strings = ArrayList<String>()
        populateWithRandomStrings(strings, amount = 30)
        val users = strings.distinct()
        val systemSize = users.size
        val tokens = HashSet<String>()

        for (i in 0 until systemSize) {
            // Dont care about exact values here: username & password are the same for each user
            val token = app.login(users[i], users[i]).join()
            tokens.add(token)
        }

        assertEquals(tokens.size, users.size)

        for (token in tokens) {
            app.logout(token).join()
        }
    }

    @Test
    internal fun `first logged in user is an administrator by default`() {
        val token = app.login("sahar", "a very strong password").join()
        app.login("yuval", "weak password").join()

        assertDoesNotThrow {
            app.makeAdministrator(token, "yuval").joinException()
        }
    }

    @Test
    internal fun `administrator can appoint other users to be administrators`() {
        val token1 = app.login("sahar", "a very strong password").join()
        val token2 = app.login("yuval", "weak password").join()
        app.login("victor", "big").join()

        app.makeAdministrator(token1, "yuval").join()
        app.makeAdministrator(token2, "victor").join()
    }

    @Test
    internal fun `trying to appoint others to be administrators without a valid token should throw InvalidTokenException`() {
        app.login("sahar", "a very strong password")
                .thenCompose { app.login("yuval", "weak password") }.join()

        assertThrows<InvalidTokenException> {
            app.makeAdministrator("badToken", "yuval").joinException()
        }
    }

    @Test
    internal fun `trying to appoint others to be administrators without authorization should throw UserNotAuthorizedException`() {
        app.login("sahar", "a very strong password").join()
        val nonAdminToken = app.login("yuval", "weak password").join()
        app.login("victor", "big").join()

        assertThrows<UserNotAuthorizedException> {
            app.makeAdministrator(nonAdminToken, "victor").joinException()
        }
    }

    @Test
    internal fun `trying to appoint a non-existing user to be an administrator should NoSuchEntityException`() {
        val adminToken = app.login("sahar", "a very strong password").join()

        assertThrows<NoSuchEntityException> {
            app.makeAdministrator(adminToken, "yuval").joinException()
        }
    }
}