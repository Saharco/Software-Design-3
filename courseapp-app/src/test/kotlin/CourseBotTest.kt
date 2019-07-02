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
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration.ofSeconds
import java.time.LocalDateTime
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
        val token = courseApp.login("sahar", "a very strong password").join()

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
    fun `Asking the bot when a user who has not sent any messages was last seen should return null`() {
        courseApp.login("admin", "admin").join()

        val bot = bots.bot().join()

        assertNull(bot.seenTime("admin").join())
    }

    @Test
    fun `Bot accurately keeps track of the richest user in a channel`() {
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

        val bot = bots.bot("cool bot").join()
        bot.join(channel).thenCompose {
            bot.setTipTrigger(tipTrigger)
        }.thenCompose {
            courseApp.channelSend(adminToken, channel, messageFactory.create(
                    MediaType.TEXT, "$tipTrigger 100 Victor".toByteArray()).join())
        }.thenCompose {
            courseApp.channelSend(otherToken2, channel, messageFactory.create(
                    MediaType.TEXT, "$tipTrigger 50 Yuval".toByteArray()).join())
        }.thenCompose {
            courseApp.channelSend(otherToken2, channel, messageFactory.create(
                    MediaType.TEXT, "$tipTrigger 200 Sahar".toByteArray()).join())
        }.join()

        val richestUser = bots.bot("cool bot").thenCompose {
            it.richestUser("#TakeCare")
        }.join()

        assertEquals("Sahar", richestUser)
    }

    @Test
    fun `Bot accurately tracks the most active user in a channel`() {
        val adminToken = courseApp.login("Sahar", "a very strong password").join()
        val otherToken = courseApp.login("Yuval", "a very weak password").join()
        val otherToken2 = courseApp.login("Victor", "anak").join()
        val channel = "#TakeCare"

        courseApp.channelJoin(adminToken, channel).thenCompose {
            courseApp.channelJoin(otherToken, channel)
        }.thenCompose {
            courseApp.channelJoin(otherToken2, channel)
        }.join()

        val bot = bots.bot("cool bot").join()
        bot.join(channel).thenCompose {
            courseApp.channelSend(adminToken, channel, messageFactory.create(
                    MediaType.TEXT, "my first message!".toByteArray()).join())
        }.thenCompose {
            courseApp.channelSend(otherToken, channel, messageFactory.create(
                    MediaType.TEXT, "my first message!".toByteArray()).join())
        }.thenCompose {
            courseApp.channelSend(otherToken2, channel, messageFactory.create(
                    MediaType.TEXT, "my first message!".toByteArray()).join())
        }.thenCompose {
            courseApp.channelSend(adminToken, channel, messageFactory.create(
                    MediaType.TEXT, "my second message!".toByteArray()).join())
        }.join()

        val mostActiveUser = bots.bot("cool bot").thenCompose {
            it.mostActiveUser("#TakeCare")
        }.join()

        assertEquals("Sahar", mostActiveUser)
    }

    @Test
    fun `Bot accurately tracks a user's last activity throughout all channels`() {
        val time1 = LocalDateTime.now()
        Thread.sleep(200)

        val adminToken = courseApp.login("sahar", "a very strong password").join()
        courseApp.channelJoin(adminToken, "#TakeCare").thenCompose {
            courseApp.channelJoin(adminToken, "#TakeCare2")
        }.join()
        val bot = bots.bot().thenCompose { bot ->
            bot.join("#TakeCare").thenApply { bot }
        }.thenCompose { bot ->
            bot.join("#TakeCare2").thenApply { bot }
        }.join()

        courseApp.channelSend(adminToken, "#TakeCare", messageFactory.create(
                MediaType.TEXT, "first message".toByteArray()).join()).join()

        val time2 = bot.seenTime("sahar").join()!!
        assertTrue(time2.toString() > time1.toString())

        courseApp.channelSend(adminToken, "#TakeCare2", messageFactory.create(
                MediaType.TEXT, "second message".toByteArray()).join()).join()

        val time3 = bot.seenTime("sahar").join()!!
        assertTrue(time3.toString() > time2.toString())
    }

    @Test
    fun `Can list bots in a channel`() {
        courseApp.login("sahar", "a very strong password")
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

        courseApp.login("sahar", "a very strong password")
                .thenCompose { adminToken ->
                    courseApp.channelJoin(adminToken, "#channel")
                            .thenCompose {
                                bots.bot().thenCompose { bot ->
                                    bot.join("#channel")
                                            .thenApply { bot.setCalculationTrigger("calculate") }
                                }
                            }
                            .thenCompose { courseApp.login("yuval", "a very weak password") }
                            .thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                            .thenCompose { token -> courseApp.addListener(token, listener).thenApply { token } }
                            .thenCompose { token -> courseApp.channelSend(token, "#channel", messageFactory.create(MediaType.TEXT, "calculate 20 * 2 +2".toByteArray()).join()) }
                }.join()

        verify {
            listener.invoke("#channel@yuval", any())
            listener.invoke("#channel@Anna0", match { String(it.contents).toInt() == 42 })
        }
    }

    @Test
    fun `A user in the channel can tip another user`() {
        courseApp.login("sahar", "a very strong password")
                .thenCompose { adminToken ->
                    courseApp.channelJoin(adminToken, "#channel")
                            .thenCompose {
                                bots.bot()
                                        .thenCompose { bot -> bot.join("#channel").thenApply { bot } }
                                        .thenCompose { bot -> bot.setTipTrigger("tip") }
                            }
                            .thenCompose { courseApp.login("yuval", "a very weak password") }
                            .thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                            .thenCompose { token -> courseApp.channelSend(token, "#channel", messageFactory.create(MediaType.TEXT, "tip 10 sahar".toByteArray()).join()) }
                }.join()

        assertThat(runWithTimeout(ofSeconds(10)) {
            bots.bot("Anna0")
                    .thenCompose { it.richestUser("#channel") }
                    .join()
        }, present(equalTo("sahar")))
    }

    @Test
    fun `The bot accurately tracks keywords`() {
        val regex = ".*ello.*[wW]orl.*"
        val channel = "#channel"
        courseApp.login("sahar", "a very strong password")
                .thenCompose { adminToken ->
                    courseApp.channelJoin(adminToken, channel)
                            .thenCompose {
                                bots.bot()
                                        .thenCompose { bot -> bot.join(channel).thenApply { bot } }
                                        .thenCompose { bot -> bot.beginCount(regex = regex) }
                            }
                            .thenCompose { courseApp.login("yuval", "a very weak password") }
                            .thenCompose { token -> courseApp.channelJoin(token, channel).thenApply { token } }
                            .thenCompose { token -> courseApp.channelSend(token, channel, messageFactory.create(MediaType.TEXT, "hello, world!".toByteArray()).join()) }
                }.join()

        assertThat(runWithTimeout(ofSeconds(10)) {
            bots.bot("Anna0").thenCompose { bot -> bot.count(regex = regex) }.join()
        }, equalTo(1L))
    }

    @Test
    fun `A user in the channel can ask the bot to do a survey`() {
        val adminToken = courseApp.login("sahar", "a very strong password")
                .thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                .join()
        val regularUserToken = courseApp.login("yuval", "a very weak password")
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
            courseApp.channelSend(adminToken, "#channel", messageFactory.create(MediaType.TEXT, "Chocolate-chip Mint".toByteArray()).join()).join()
            courseApp.channelSend(regularUserToken, "#channel", messageFactory.create(MediaType.TEXT, "Chocolate-chip Mint".toByteArray()).join()).join()
            courseApp.channelSend(adminToken, "#channel", messageFactory.create(MediaType.TEXT, "Chocolate-chip Mint".toByteArray()).join()).join()
            bot.surveyResults(survey).join()
        }, containsElementsInOrder(0L, 0L, 2L))
    }

    @Test
    fun `Bots' names are generated correctly and in order`() {
        courseApp.login("sahar", "a very strong password").join()
        val bots = bots.bot().thenCompose {
            bots.bot()
        }.thenCompose {
            bots.bot("Yossi")
        }.thenCompose {
            bots.bot()
        }.thenCompose {
            bots.bots()
        }.join()

        assertEquals(listOf("Anna0", "Anna1", "Yossi", "Anna3"), bots)
    }

    @Test
    fun `Setting tip trigger to null returns the previous tip trigger`() {
        val previousTrigger = courseApp.login("sahar", "a very strong password").thenCompose {
            bots.bot()
        }.thenCompose { bot ->
            bot.setTipTrigger("Hello")
                    .thenApply { bot }
        }.thenCompose { bot ->
            bot.setTipTrigger(null)
        }.join()

        assertEquals(previousTrigger, "Hello")
    }

    @Test
    fun `Setting calculation trigger to null returns the previous tip trigger`() {
        val previousTrigger = courseApp.login("sahar", "a very strong password").thenCompose {
            bots.bot()
        }.thenCompose { bot ->
            bot.setCalculationTrigger("Hello")
                    .thenApply { bot }
        }.thenCompose { bot ->
            bot.setCalculationTrigger(null)
        }.join()

        assertEquals(previousTrigger, "Hello")
    }

    @Test
    fun `Bot ignores malicious users that try to tip others for negative amounts`() {
        val (adminToken, otherToken) = courseApp.login("sahar", "a very strong password").thenCompose { adminToken ->
            courseApp.login("yuval", "a very weak password").thenApply { Pair(adminToken, it) }
        }.join()
        val channel = "#TakeCare"
        val tipTrigger = "hello i\'d like to tip: "
        val bot = courseApp.channelJoin(adminToken, channel).thenCompose {
            courseApp.channelJoin(otherToken, channel)
        }.thenCompose {
            bots.bot()
        }.thenCompose { bot ->
            bot.join(channel).thenCompose {
                bot.setTipTrigger(tipTrigger).thenApply { bot }
            }
        }.join()

        // this should not work!
        courseApp.channelSend(adminToken, channel, messageFactory.create(
                MediaType.TEXT, "$tipTrigger -100 yuval".toByteArray()).join()).join()

        assertNull(bot.richestUser(channel))
    }

    @Test
    fun `Bot ignores users that try to tip more bits than they own`() {
        val (adminToken, otherToken) = courseApp.login("sahar", "a very strong password").thenCompose { adminToken ->
            courseApp.login("yuval", "a very weak password").thenApply { Pair(adminToken, it) }
        }.join()
        val channel = "#TakeCare"
        val tipTrigger = "hello i\'d like to tip: "
        val bot = courseApp.channelJoin(adminToken, channel).thenCompose {
            courseApp.channelJoin(otherToken, channel)
        }.thenCompose {
            bots.bot()
        }.thenCompose { bot ->
            bot.join(channel).thenCompose {
                bot.setTipTrigger(tipTrigger).thenApply { bot }
            }
        }.join()

        // this should not work!
        courseApp.channelSend(adminToken, channel, messageFactory.create(
                MediaType.TEXT, "$tipTrigger 1100 yuval".toByteArray()).join()).join()

        assertNull(bot.richestUser(channel))
    }

    @Test
    fun `Two bots keep separate ledgers when tracking users in the same channel even if trigger word is identical for both`() {
        val (adminToken, otherToken) = courseApp.login("sahar", "a very strong password").thenCompose { adminToken ->
            courseApp.login("yuval", "a very weak password").thenApply { Pair(adminToken, it) }
        }.join()
        val channel = "#TakeCare"
        val tipTrigger = "hello i\'d like to tip: "
        courseApp.channelJoin(adminToken, channel).thenCompose {
            courseApp.channelJoin(otherToken, channel)
        }.join()

        val (bot1, bot2) = bots.bot().thenCompose { bot1 ->
            bot1.setTipTrigger(tipTrigger).thenCompose {
                bot1.join(channel).thenCompose {
                    bots.bot().thenCompose { bot2 ->
                        bot2.setTipTrigger(tipTrigger).thenApply {
                            Pair(bot1, bot2)
                        }
                    }
                }
            }
        }.join()

        // bot1 is in the channel; bot2 isnt

        courseApp.channelSend(adminToken, channel, messageFactory.create(
                MediaType.TEXT, "$tipTrigger 800 yuval".toByteArray()).join()).join()

        bot2.join(channel).join()

        assertEquals("yuval", bot1.richestUser(channel))
        assertNull(bot2.richestUser(channel))

        courseApp.channelSend(otherToken, channel, messageFactory.create(
                MediaType.TEXT, "$tipTrigger 200 sahar".toByteArray()).join()).join()

        assertEquals("yuval", bot1.richestUser(channel))
        assertEquals("sahar", bot2.richestUser(channel))
    }

    @Test
    fun `Bot keeps a separate ledger for the same user through multiple channels`() {
        val (adminToken, channel1Token, channel2Token) =
                courseApp.login("sahar", "a very strong password").thenCompose { adminToken ->
                    courseApp.login("yuval", "a very weak password").thenCompose { channel1Token ->
                        courseApp.login("victor", "anak").thenApply {
                            Triple(adminToken, channel1Token, it)
                        }
                    }
                }.join()
        val channel1 = "#TakeCare"
        val channel2 = "#TakeCare2"
        val tipTrigger = "hello i\'d like to tip: "
        courseApp.channelJoin(adminToken, channel1).thenCompose {
            courseApp.channelJoin(channel1Token, channel1)
        }.thenCompose {
            courseApp.channelJoin(adminToken, channel2)
        }.thenCompose {
            courseApp.channelJoin(channel2Token, channel2)
        }.join()

        // channel1 members: adminToken (sahar), channel1Token (yuval)
        // channel2 members: adminToken (sahar), channel2Token (victor)

        val bot = bots.bot().thenCompose { bot ->
            bot.setTipTrigger(tipTrigger).thenCompose {
                bot.join(channel1).thenCompose {
                    bot.join(channel2).thenApply { bot }
                }
            }
        }.join()

        courseApp.channelSend(adminToken, channel1, messageFactory.create(
                MediaType.TEXT, "$tipTrigger 800 yuval".toByteArray()).join()).join()

        courseApp.channelSend(channel2Token, channel2, messageFactory.create(
                MediaType.TEXT, "$tipTrigger 200 sahar".toByteArray()).join()).join()

        // channel1 bits: adminToken (sahar): 200, channel1Token (yuval): 1800
        // channel2 bits: adminToken (sahar): 1200, channel2Token (victor): 800

        assertEquals("yuval", bot.richestUser(channel1))
        assertEquals("sahar", bot.richestUser(channel2))
    }

    @Test
    fun `Bots' callbacks operate only on text messages, except for counting messages & keywords tracking`() {
        val (adminToken, otherToken) = courseApp.login("sahar", "a very strong password").thenCompose { adminToken ->
            courseApp.login("yuval", "a very weak password").thenApply { Pair(adminToken, it) }
        }.join()
        val channel = "#TakeCare"
        val trigger = "!hello:"
        val bot = bots.bot().thenCompose { bot ->
            bot.setTipTrigger(trigger).thenCompose {
                bot.setCalculationTrigger(trigger).thenCompose {
                    bot.join(channel).thenApply { bot }
                }
            }
        }.join()

        courseApp.channelSend(adminToken, channel, messageFactory.create(
                MediaType.PICTURE, "$trigger 100 yuval".toByteArray()).join()).join()
        courseApp.channelSend(adminToken, channel, messageFactory.create(
                MediaType.PICTURE, "$trigger (40+40)/4".toByteArray()).join()).join()
        
        TODO("CONTINUE THIS!")
    }

    @Test
    fun `Bot's channel statistics are deleted upon leaving a channel`() {
        TODO("Need to write this test case")
    }
}