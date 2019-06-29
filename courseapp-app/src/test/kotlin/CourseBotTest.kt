import com.authzee.kotlinguice4.getInstance
import com.google.inject.Guice
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.present
import il.ac.technion.cs.softwaredesign.*
import il.ac.technion.cs.softwaredesign.exceptions.NoSuchEntityException
import il.ac.technion.cs.softwaredesign.exceptions.UserNotAuthorizedException
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration.ofSeconds
import java.util.concurrent.CompletableFuture

class CourseBotTest {
    private val injector = Guice.createInjector(CourseAppTestModule(), CourseBotModule())

    init {
        injector.getInstance<CourseAppInitializer>().setup().join()
    }

    private val courseApp = injector.getInstance<CourseApp>()
    private val bots = injector.getInstance<CourseBots>()
    private val messageFactory = injector.getInstance<MessageFactory>()

    init {
        bots.prepare().join()
        bots.start().join()
    }

    @Test
    fun `Can create a bot and make it join channels`() {
        val token = courseApp.login("gal", "hunter2").join()

        assertThat(runWithTimeout(ofSeconds(10)) {
            val bot = courseApp.channelJoin(token, "#channel")
                    .thenCompose { bots.bot() }
                    .join()
            bot.join("#channel").join()
            bot.channels().join()
        }, equalTo(listOf("#channel")))
    }

    @Test
    fun `Bot count should return 0 when no suitable keywords were tracked in any channel message`() {
        val adminToken = courseApp.login("sahar", "a very strong password").join()
        val bot = courseApp.channelJoin(adminToken, "#TakeCare").thenCompose {
            bots.bot()
        }.thenCompose { bot ->
            bot.join("#TakeCare").thenApply { bot }
        }.thenCompose { bot ->
            bot.beginCount("#TakeCare", "bad").thenApply { bot }
        }.join()

        val count = courseApp.channelSend(adminToken, "#TakeCare", messageFactory.create(
                MediaType.TEXT, "good".toByteArray()).join()).thenCompose {
            bot.count("#TakeCare", "bad")
        }.join()

        assertEquals(0L, count)
    }

    @Test
    fun `Bot count should return 0 when suitable keywords were tracked in another channel message`() {
        val adminToken = courseApp.login("sahar", "a very strong password").join()
        courseApp.channelJoin(adminToken, "#TakeCare").thenCompose {
            courseApp.channelJoin(adminToken, "#TakeCare2")
        }.join()
        val bot = bots.bot().thenCompose { bot ->
            bot.join("#TakeCare").thenApply { bot }
        }.thenCompose { bot ->
            bot.beginCount("#TakeCare", "bad").thenApply { bot }
        }.join()

        val count = courseApp.channelSend(adminToken, "#TakeCare2", messageFactory.create(
                MediaType.TEXT, "bad".toByteArray()).join()).thenCompose {
            bot.count("#TakeCare", "bad")
        }.join()

        assertEquals(0L, count)
    }

    @Test
    fun `Making the bot join a channel that does not exist should throw UserNotAuthorizedException`() {
        courseApp.login("admin", "admin").join() // necessary: otherwise bot is the admin!!
        assertThrows<UserNotAuthorizedException> {
            bots.bot().thenCompose { bot ->
                bot.join("#BadChannel")
            }.joinException()
        }
    }

    @Test
    fun `Making the bot join a channel with an invalid name should throw UserNotAuthorizedException`() {
        courseApp.login("admin", "admin").join()
        assertThrows<UserNotAuthorizedException> {
            bots.bot().thenCompose { bot ->
                bot.join("BadChannel")
            }.joinException()
        }
    }

    @Test
    fun `Asking the bot who is the most active user in a channel it is not a member of should throw NoSuchEntityException`() {
        courseApp.login("admin", "admin").join()
        assertThrows<NoSuchEntityException> {
            bots.bot().thenCompose { bot ->
                bot.mostActiveUser("#TakeCare")
            }.joinException()
        }
    }

    @Test
    fun `Asking the bot who is the richest user in a channel it is not a member of should throw NoSuchEntityException`() {
        courseApp.login("admin", "admin").join()
        assertThrows<NoSuchEntityException> {
            bots.bot().thenCompose { bot ->
                bot.richestUser("#TakeCare")
            }.joinException()
        }
    }

    @Test
    fun `Running a survey in a channel that the bot is not a member of should throw NoSuchEntityException`() {
        courseApp.login("admin", "admin").join()
        assertThrows<NoSuchEntityException> {
            bots.bot().thenCompose { bot ->
                bot.runSurvey("#TakeCare", "Will it fail?", listOf("yes", "no"))
            }.joinException()
        }
    }

    @Test
    fun `Asking the bot for a survey results with an invalid identifier should throw NoSuchEntityException`() {
        courseApp.login("admin", "admin").join()
        assertThrows<NoSuchEntityException> {
            bots.bot().thenCompose { bot ->
                bot.surveyResults(":)")
            }.joinException()
        }
    }

    @Test
    fun `Asking the bot who is the most active user in a channel with no active users should return null`() {
        courseApp.login("admin", "ad3min").thenCompose { adminToken ->
            courseApp.channelJoin(adminToken, "#TakeCare")
        }.join()

        val bot = bots.bot().thenCompose { bot ->
            bot.join("#TakeCare").thenApply { bot }
        }.join()

        assertNull(bot.mostActiveUser("#TakeCare").join())
    }

    @Test
    fun `Asking the bot who is the most active user in a channel with no single richest user should return null`() {
        val adminToken = courseApp.login("Sahar", "a very strong password").join()
        val otherToken = courseApp.login("Yuval", "a very weak password").join()
        val otherToken2 = courseApp.login("Victor", "anak").join()
        val channel = "#TakeCare"
        val tipTrigger = "hello i\'d like to tip: "

        courseApp.channelJoin(adminToken, channel).thenCompose {
            courseApp.channelJoin(otherToken, channel)
        }.thenCompose {
            courseApp.channelJoin(otherToken2, channel)
        }.join()

        val bot = bots.bot().join()
        bot.join(channel).thenCompose {
            bot.setTipTrigger(tipTrigger)
        }.thenCompose {
            courseApp.channelSend(adminToken, channel, messageFactory.create(
                    MediaType.TEXT, "$tipTrigger 100 Victor".toByteArray()).join())
        }.thenCompose {
            courseApp.channelSend(otherToken2, channel, messageFactory.create(
                    MediaType.TEXT, "$tipTrigger 50 Yuval".toByteArray()).join())
        }.join()

        /*
         * Balance:
         *  Sahar: 900
         *  Yuval: 1050 <-
         *  Victor: 1050 <-
         */
        assertNull(bot.richestUser(channel).join())
    }

    @Test
    fun `Can list bots in a channel`() {
        courseApp.login("gal", "hunter2")
                .thenCompose { adminToken ->
                    courseApp.channelJoin(adminToken, "#channel")
                            .thenCompose { bots.bot().thenCompose { it.join("#channel") } }
                }.join()

        assertThat(runWithTimeout(ofSeconds(10)) {
            bots.bots("#channel").join()
        }, equalTo(listOf("Anna0")))
    }

    @Test
    fun `A user in the channel can ask the bot to do calculation`() {
        val listener = mockk<ListenerCallback>()
        every { listener(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        courseApp.login("gal", "hunter2")
                .thenCompose { adminToken ->
                    courseApp.channelJoin(adminToken, "#channel")
                            .thenCompose {
                                bots.bot().thenCompose { bot ->
                                    bot.join("#channel")
                                            .thenApply { bot.setCalculationTrigger("calculate") }
                                }
                            }
                            .thenCompose { courseApp.login("matan", "s3kr3t") }
                            .thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                            .thenCompose { token -> courseApp.addListener(token, listener).thenApply { token } }
                            .thenCompose { token -> courseApp.channelSend(token, "#channel", messageFactory.create(MediaType.TEXT, "calculate 20 * 2 +2".toByteArray()).join()) }
                }.join()

        verify {
            listener.invoke("#channel@matan", any())
            listener.invoke("#channel@Anna0", match { String(it.contents).toInt() == 42 })
        }
    }

    @Test
    fun `A user in the channel can tip another user`() {
        courseApp.login("gal", "hunter2")
                .thenCompose { adminToken ->
                    courseApp.channelJoin(adminToken, "#channel")
                            .thenCompose {
                                bots.bot()
                                        .thenCompose { bot -> bot.join("#channel").thenApply { bot } }
                                        .thenCompose { bot -> bot.setTipTrigger("tip") }
                            }
                            .thenCompose { courseApp.login("matan", "s3kr3t") }
                            .thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                            .thenCompose { token -> courseApp.channelSend(token, "#channel", messageFactory.create(MediaType.TEXT, "tip 10 gal".toByteArray()).join()) }
                }.join()

        assertThat(runWithTimeout(ofSeconds(10)) {
            bots.bot("Anna0")
                    .thenCompose { it.richestUser("#channel") }
                    .join()
        }, present(equalTo("gal")))
    }

    @Test
    fun `The bot accurately tracks keywords`() {
        val regex = ".*ello.*[wW]orl.*"
        val channel = "#channel"
        courseApp.login("gal", "hunter2")
                .thenCompose { adminToken ->
                    courseApp.channelJoin(adminToken, channel)
                            .thenCompose {
                                bots.bot()
                                        .thenCompose { bot -> bot.join(channel).thenApply { bot } }
                                        .thenCompose { bot -> bot.beginCount(regex = regex) }
                            }
                            .thenCompose { courseApp.login("matan", "s3kr3t") }
                            .thenCompose { token -> courseApp.channelJoin(token, channel).thenApply { token } }
                            .thenCompose { token -> courseApp.channelSend(token, channel, messageFactory.create(MediaType.TEXT, "hello, world!".toByteArray()).join()) }
                }.join()

        assertThat(runWithTimeout(ofSeconds(10)) {
            bots.bot("Anna0").thenCompose { bot -> bot.count(regex = regex) }.join()
        }, equalTo(1L))
    }

    @Test
    fun `A user in the channel can ask the bot to do a survey`() {
        val adminToken = courseApp.login("gal", "hunter2")
                .thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                .join()
        val regularUserToken = courseApp.login("matan", "s3kr3t")
                .thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                .join()
        val bot = bots.bot()
                .thenCompose { bot -> bot.join("#channel").thenApply { bot } }
                .join()

        assertThat(runWithTimeout(ofSeconds(10)) {
            val survey = bot.runSurvey("#channel", "What is your favorite flavour of ice-cream?",
                    listOf("Cranberry",
                            "Charcoal",
                            "Chocolate-chip Mint")).join()
            courseApp.channelSend(adminToken, "#channel", messageFactory.create(MediaType.TEXT, "Chocolate-chip Mint".toByteArray()).join())
            courseApp.channelSend(regularUserToken, "#channel", messageFactory.create(MediaType.TEXT, "Chocolate-chip Mint".toByteArray()).join())
            courseApp.channelSend(adminToken, "#channel", messageFactory.create(MediaType.TEXT, "Chocolate-chip Mint".toByteArray()).join())
            bot.surveyResults(survey).join()
        }, containsElementsInOrder(0L, 0L, 2L))
    }
}