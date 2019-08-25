package il.ac.technion.cs.softwaredesign.tests

import com.google.inject.Guice
import il.ac.technion.cs.softwaredesign.CourseAppModule
import il.ac.technion.cs.softwaredesign.CourseBotModule
import com.authzee.kotlinguice4.getInstance
import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import il.ac.technion.cs.softwaredesign.*
import il.ac.technion.cs.softwaredesign.exceptions.NoSuchEntityException
import il.ac.technion.cs.softwaredesign.exceptions.UserNotAuthorizedException
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.Message
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import il.ac.technion.cs.softwaredesign.storage.SecureStorageModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture


class CourseBotStaffTest {
    private val injector = Guice.createInjector(CourseAppModule(), CourseBotModule(), SecureStorageModule())

    private val courseApp = injector.getInstance<CourseApp>()
    private val bots = injector.getInstance<CourseBots>()
    private val messageFactory = injector.getInstance<MessageFactory>()

    init {
        injector.getInstance<CourseAppInitializer>().setup().join()
        bots.prepare().thenCompose { bots.start() }.join()
    }

    private fun sendMessagesOfTest(testName: String, testData: TestData) {
        sendMessagesByTest(testName, messageFactory, courseApp, testData.userMap)
    }

    private fun fetchTestData(testName: String) = loadDataForTestWithoutMessages(courseApp, testName, bots)

    private fun buildMessage(content: String): Message {
        return messageFactory.create(MediaType.TEXT, stringToByteArray(content)).join()
    }

    private fun getCalculatedNumbersFromChannel(testData: TestData, channel: String) =
            testData.userMap[getAdminOfChannel(channel)]!!.messages
                    .map { p -> byteArrayToString(p.second.contents)!!.toLong() }

    @Nested
    inner class BasicTest {

        @Test
        fun `join channel returns as expected`() {
            val adminToken = courseApp.login("admin", "pass").join()
            courseApp.channelJoin(adminToken, "#hagana").join()
            val bot = bots.bot().join()

            assertWithTimeout {
                assertThrows<UserNotAuthorizedException> { bot.join("hagana").joinException() }
                assertThrows<UserNotAuthorizedException> { bot.join("#dsad").joinException() }
                assertDoesNotThrow { bot.join("#hagana").join() }
            }
        }

        @Test
        fun `part channel returns as expected`() {
            val adminToken = courseApp.login("admin", "pass").join()
            courseApp.channelJoin(adminToken, "#opti").join()
            val bot = bots.bot().join()
            bot.join("#opti")

            assertWithTimeout {
                assertThrows<NoSuchEntityException> { bot.part("maman").joinException() }
                assertThrows<NoSuchEntityException> { bot.part("#dsad").joinException() }
                bot.part("#opti").join()
                assertThrows<NoSuchEntityException> { bot.part("#hagana").joinException() }
            }
        }

        @Test
        fun `channels count for small amount`() {
            val adminToken = courseApp.login("admin", "pass").join()
            courseApp.channelJoin(adminToken, "#opti")
                    .thenCompose { courseApp.channelJoin(adminToken, "#stats") }
                    .thenCompose { courseApp.channelJoin(adminToken, "#intro_to_network") }
            val bot = bots.bot().join()

            assertWithTimeout {
                assertThat(bot.channels().join().size, equalTo(0))
                bot.join("#stats").thenCompose { bot.join("#opti") }.join()
                assertThat(bot.channels().join(), containsElementsInOrder("#stats", "#opti"))
            }
        }

        @Test
        fun `seen time returns as expected`() {
            val adminToken = courseApp.login("admin", "pass").join()
            courseApp.channelJoin(adminToken, "#opti")
                    .thenCompose { courseApp.channelJoin(adminToken, "#stats") }
                    .thenCompose { courseApp.channelJoin(adminToken, "#intro_to_network") }.join()
            val bot = bots.bot().join()
            bot.join("#stats").thenCompose { bot.join("#opti") }.join()
            val msg1 = buildMessage("First one")
            val msg2 = buildMessage("Second!")
            val msg3 = buildMessage("!")
            assertWithTimeout {
                assertThat(bot.seenTime("admin").join(), absent())
                assertThat(bot.seenTime("dsad").join(), absent())
                courseApp.channelSend(adminToken, "#opti", msg2)
                        .thenCompose { courseApp.channelSend(adminToken, "#stats", msg1) }
                        .thenCompose { courseApp.channelSend(adminToken, "#intro_to_network", msg3) }.join()
                assertThat(bot.seenTime("admin").join(), equalTo(msg1.created))
            }
        }

        @Test
        fun `count mechanism in error scenarios`() {
            val adminToken = courseApp.login("admin", "pass").join()
            courseApp.channelJoin(adminToken, "#opti")
                    .thenCompose { courseApp.channelJoin(adminToken, "#compi") }
                    .thenCompose { courseApp.channelJoin(adminToken, "#theory_of_computation") }
            val bot = bots.bot().join()
            bot.join("#compi").thenCompose { bot.join("#theory_of_computation") }.join()

            assertWithTimeout {
                assertThrows<IllegalArgumentException> { bot.beginCount("#compi", null, null).joinException() }
                assertThrows<IllegalArgumentException> { bot.count("#compi", null, null).joinException() }
                assertDoesNotThrow { bot.beginCount(null, null, MediaType.TEXT).joinException() }
                assertThat(bot.count(null, null, MediaType.TEXT).join(), equalTo(0L))
            }
        }

        @Test
        fun `calc trigger returns as expected`() {
            val adminToken = courseApp.login("admin", "pass").join()
            courseApp.channelJoin(adminToken, "#oop").join()
            val bot = bots.bot().join()
            bot.join("#oop").join()
            assertWithTimeout {
                assertThat(bot.setCalculationTrigger("sometrig").join(), absent())
                assertThat(bot.setCalculationTrigger("another").join(), equalTo("sometrig"))
            }
        }

        @Test
        fun `tip trigger returns as expected`() {
            val adminToken = courseApp.login("admin", "pass").join()
            courseApp.channelJoin(adminToken, "#oop").join()
            val bot = bots.bot().join()
            bot.join("#oop").join()
            assertWithTimeout {
                assertThat(bot.setTipTrigger("sometrig").join(), absent())
                assertThat(bot.setTipTrigger("another").join(), equalTo("sometrig"))
            }
        }

        @Test
        fun `mostActiveUser returns as expected`() {
            val adminToken = courseApp.login("admin", "pass").join()
            courseApp.channelJoin(adminToken, "#oop").thenCompose { courseApp.channelJoin(adminToken, "#automatons") }.join()
            val bot = bots.bot().join()
            bot.join("#oop").join()
            assertWithTimeout {
                assertThat(bot.mostActiveUser("#oop").join(), absent())
                assertThrows<NoSuchEntityException> { bot.mostActiveUser("#automatons").joinException() }
                courseApp.channelSend(adminToken, "#oop", buildMessage("Some message")).join()
                assertThat(bot.mostActiveUser("#oop").join(), equalTo("admin"))
            }
        }

        @Test
        fun `mostActiveUser reset after leaving channel`() {
            val adminToken = courseApp.login("admin", "pass").join()
            courseApp.channelJoin(adminToken, "#oop").thenCompose { courseApp.channelJoin(adminToken, "#automatons") }.join()
            val bot = bots.bot().join()
            bot.join("#oop").join()
            courseApp.channelSend(adminToken, "#oop", buildMessage("Some message")).thenCompose {
                bot.part("#oop")
            }.join()

            assertWithTimeout {
                assertThrows<NoSuchEntityException> { bot.mostActiveUser("#oop").joinException() }
                bot.join("#oop").join()
                assertThat(bot.mostActiveUser("#oop").join(), absent())
            }
        }

        @Test
        fun `basic survey running returns as expected`() {
            val adminToken = courseApp.login("admin", "pass").join()
            courseApp.channelJoin(adminToken, "#opti")
                    .thenCompose { courseApp.channelJoin(adminToken, "#compi") }
                    .thenCompose { courseApp.channelJoin(adminToken, "#theory_of_computation") }
            val bot = bots.bot().join()
            bot.join("#compi").join()

            assertWithTimeout {
                assertThrows<NoSuchEntityException> { bot.runSurvey("#opti", "Q3", listOf("Does it matter?")).joinException() }
                assertThrows<NoSuchEntityException> { bot.runSurvey("#shmopti", "Q3", listOf("Does it matter?")).joinException() }
                assertDoesNotThrow { bot.runSurvey("#compi", "How hard is HW4?", listOf("Hard", "Very Hard")).joinException() }
            }
        }

        @Test
        fun `survey results return as expected`() {
            val adminToken = courseApp.login("admin", "pass").join()
            courseApp.channelJoin(adminToken, "#opti")
                    .thenCompose { courseApp.channelJoin(adminToken, "#compi") }
                    .thenCompose { courseApp.channelJoin(adminToken, "#theory_of_computation") }
            val bot = bots.bot().join()
            val surveyId = bot.join("#compi").thenCompose { bot.runSurvey("#compi", "How hard is HW4?", listOf("Hard", "Very Hard")) }.join()

            assertWithTimeout {
                assertThat(bot.surveyResults(surveyId).join(), containsElementsInOrder(0L, 0L))
                assertThrows<NoSuchEntityException> { bot.surveyResults(surveyId + "Garbage").joinException() }
                courseApp.channelSend(adminToken, "#compi", buildMessage("Hard")).join()
                assertThat(bot.surveyResults(surveyId).join(), containsElementsInOrder(1L, 0L))
                courseApp.channelSend(adminToken, "#compi", buildMessage("Very Hard")).join()
                assertThat(bot.surveyResults(surveyId).join(), containsElementsInOrder(0L, 1L))
            }
        }

        @Test
        fun `bot creation with default names`() {
            val adminToken = courseApp.login("admin", "pass").join()
            courseApp.channelJoin(adminToken, "#algo").join()
            bots.bot().thenCompose { bots.bot("Arno") }.thenCompose { bots.bot() }
                    .thenCompose { bot -> bot.join("#algo") }
                    .thenCompose { bots.bot("Ezio") }
                    .thenCompose { bot -> bot.join("#algo") }.join()
            assertWithTimeout {
                assertThat(bots.bots(null).join(), containsElementsInOrder("Anna0", "Arno", "Anna2", "Ezio"))
                assertThat(bots.bots("#algo").join(), containsElementsInOrder("Anna2", "Ezio"))
            }
        }
    }

    @Nested
    inner class MainSmallTest {

        @Test
        fun `count mechanism by multiple bots on same channel`() {
            val testData = fetchTestData("small_test")
            val bot0 = testData.bots["Bot_0"]!!
            val bot2 = testData.bots["Bot_2"]!!
            val bot4 = testData.bots["Bot_4"]!!

            bot0.beginCount("#channel_0", "(.*sR.*)|(.*UU.*)", MediaType.FILE).join()
            bot4.beginCount("#channel_0", "75916_[^_]*_7.*", null).join()
            bot2.beginCount("#channel_0", ".*33581\$", MediaType.TEXT).join()
            sendMessagesOfTest("small_test", testData)

            assertWithTimeout {
                assertThat(bot0.count("#channel_0", "(.*sR.*)|(.*UU.*)", MediaType.FILE).join()!!, equalTo(3L))
                assertThat(bot4.count("#channel_0", "75916_[^_]*_7.*", null).join()!!, equalTo(9L))
                assertThat(bot2.count("#channel_0", ".*33581\$", MediaType.TEXT).join(), equalTo(7L))
                bot0.beginCount("#channel_0", "(.*sR.*)|(.*UU.*)", MediaType.FILE).join()
                assertThat(bot0.count("#channel_0", "(.*sR.*)|(.*UU.*)", MediaType.FILE).join()!!, equalTo(0L))

            }

        }

        @Test
        fun `multiple count mechanism by single bot`() {
            val testData = fetchTestData("small_test")
            val bot4 = testData.bots["Bot_4"]!!
            bot4.beginCount("#channel_0", "(.*RU.*)|(^33581.*)", MediaType.AUDIO).join()
            bot4.beginCount("#channel_3", "(.*RU.*)|(^33581.*)", null).join()
            bot4.beginCount("#channel_4", "(.*RU.*)|(^33581.*)", MediaType.REFERENCE).join()
            bot4.beginCount(null, "(.*RU.*)|(^33581.*)", MediaType.STICKER).join()
            sendMessagesOfTest("small_test", testData)

            assertWithTimeout {
                assertThat(bot4.count("#channel_0", "(.*RU.*)|(^33581.*)", MediaType.AUDIO).join(), equalTo(8L))
                assertThat(bot4.count("#channel_3", "(.*RU.*)|(^33581.*)", null).join(), equalTo(63L))
                assertThat(bot4.count("#channel_4", "(.*RU.*)|(^33581.*)", MediaType.REFERENCE).join(), equalTo(7L))
                assertThat(bot4.count(null, "(.*RU.*)|(^33581.*)", MediaType.STICKER).join(), equalTo(36L))

            }
        }

        @Test
        fun `calc trigger test of multiple bots `() {
            val testData = fetchTestData("small_test")
            val bot1 = testData.bots["Bot_1"]!!
            val bot0 = testData.bots["Bot_0"]!!
            bot1.setCalculationTrigger("trigger1").thenCompose { bot0.setCalculationTrigger("trigger0") }.join()
            val user0token = testData.userMap["User0"]!!.token
            val user9token = testData.userMap["User9"]!!.token
            courseApp.channelSend(user0token, "#channel_3", buildMessage("trigger0 (5+7)/6"))
                    .thenCompose { courseApp.channelSend(user0token, "#channel_3", buildMessage("trigger1 (45*2)-20")) }
                    .thenCompose { courseApp.channelSend(user0token, "#channel_0", buildMessage("trigger0 70+4")) }
                    .thenCompose { courseApp.channelSend(user9token, "#channel_0", buildMessage("trigger0 23")) }
                    .thenCompose { courseApp.channelSend(user9token, "#channel_4", buildMessage("trigger1 35-5*2")) }
                    .thenCompose { courseApp.channelSend(user9token, "#channel_1", buildMessage("trigger0 46")) }
                    .thenCompose { courseApp.channelSend(user9token, "#channel_1", buildMessage("trigger1 734-40*2")) }.join()

            assertWithTimeout {
                assertThat(getCalculatedNumbersFromChannel(testData, "#channel_0"), containsElementsInOrder(74L, 23L))
                assertThat(getCalculatedNumbersFromChannel(testData, "#channel_1"), containsElementsInOrder(46L, 654L))
                assertThat(getCalculatedNumbersFromChannel(testData, "#channel_4"), containsElementsInOrder(25L))
                assertThat(getCalculatedNumbersFromChannel(testData, "#channel_3"), containsElementsInOrder(2L, 70L))
            }
        }

        @Test
        fun `calc trigger test with disable in the middle `() {
            val testData = fetchTestData("small_test")
            val bot1 = testData.bots["Bot_1"]!!
            val bot0 = testData.bots["Bot_0"]!!
            bot1.setCalculationTrigger("trigger1").thenCompose { bot0.setCalculationTrigger("trigger0") }.join()
            val user0token = testData.userMap["User0"]!!.token
            val user9token = testData.userMap["User9"]!!.token
            courseApp.channelSend(user0token, "#channel_3", buildMessage("trigger0 (5+7)/6"))
                    .thenCompose { courseApp.channelSend(user0token, "#channel_3", buildMessage("trigger1 (45*2)-20")) }
                    .thenCompose { courseApp.channelSend(user0token, "#channel_0", buildMessage("trigger0 30+4")) }
                    .thenCompose { bot0.setCalculationTrigger(null) }
                    .thenCompose { courseApp.channelSend(user9token, "#channel_0", buildMessage("trigger0 23")) }
                    .thenCompose { courseApp.channelSend(user0token, "#channel_3", buildMessage("trigger0 (15*7)-10")) }
                    .thenCompose { bot1.setCalculationTrigger(null) }
                    .thenCompose { courseApp.channelSend(user9token, "#channel_1", buildMessage("trigger0 46")) }
                    .thenCompose { courseApp.channelSend(user9token, "#channel_1", buildMessage("trigger1 734-40*2")) }
                    .thenCompose { courseApp.channelSend(user0token, "#channel_3", buildMessage("trigger1 15000+1")) }.join()


            assertWithTimeout {
                /*
                [34, 23]
                [46, 654]
                [2, 70, 95, 15001]
                 */
                assertThat(getCalculatedNumbersFromChannel(testData, "#channel_0"), containsElementsInOrder(34L))
                assertThat(getCalculatedNumbersFromChannel(testData, "#channel_1"), isEmpty)
                assertThat(getCalculatedNumbersFromChannel(testData, "#channel_3"), containsElementsInOrder(2L, 70L))
            }
        }

        @Test
        fun `tip trigger of single bot`() {
            val testData = fetchTestData("small_test")
            val bot4 = testData.bots["Bot_4"]!!
            bot4.setTipTrigger("tiptrigger4").join()
            sendTipsByTest("small_test", messageFactory, courseApp, testData.userMap)

            assertWithTimeout {
                assertThat(bot4.richestUser("#channel_0").join(), equalTo("User19"))
                assertThat(bot4.richestUser("#channel_3").join(), equalTo("User101"))
                assertThat(bot4.richestUser("#channel_4").join(), equalTo("User163"))
            }

        }

        @Test
        fun `tip trigger of multiple bots`() {
            val testData = fetchTestData("small_test")

            val bot1 = testData.bots["Bot_1"]!!
            val bot0 = testData.bots["Bot_0"]!!
            val bot3 = testData.bots["Bot_3"]!!
            bot0.setTipTrigger("tiptrigger0").join()
            bot1.setTipTrigger("tiptrigger1").join()
            bot3.setTipTrigger("tiptrigger3").join()
            sendTipsByTest("small_test", messageFactory, courseApp, testData.userMap)
            /*val u48token=testData.userMap["User48"]!!.token
            courseApp.channelSend(u48token,"#channel_3",buildMessage("tiptrigger0 7 User50"))
                    .thenCompose { courseApp.channelSend(u48token,"#channel_3",buildMessage("tiptrigger1 8 User134")) }
                    .thenCompose { courseApp.channelSend(u48token,"#channel_3",buildMessage("tiptrigger3 8 User60")) }
                    .thenCompose { courseApp.channelSend(u48token,"#channel_3",buildMessage("tiptrigger3 8 User133")) }
                    .join()*/
            assertWithTimeout {
                assertThat(bot0.richestUser("#channel_3").join(), equalTo("User50"))
                assertThat(bot1.richestUser("#channel_3").join(), equalTo("User134"))
                assertThat(bot3.richestUser("#channel_3").join(), absent())
            }
        }

        @Test
        fun `bots statistic test`() {
            val testData = fetchTestData("bots_test")


            assertWithTimeout {
                assertThat(bots.bots().join(), containsElementsInOrder("Bot_0", "Bot_1", "Bot_2", "Bot_3", "Bot_4", "Bot_5", "Bot_6", "Bot_7",
                        "Bot_8", "Bot_9", "Bot_10", "Bot_11", "Bot_12", "Bot_13", "Bot_14", "Bot_15", "Bot_16", "Bot_17", "Bot_18", "Bot_19"))
                assertThat(bots.bots("#channel_2").join(), containsElementsInOrder("Bot_0", "Bot_2", "Bot_3", "Bot_4", "Bot_5", "Bot_8", "Bot_10", "Bot_13",
                        "Bot_14", "Bot_15", "Bot_16", "Bot_17", "Bot_18", "Bot_19"))
                assertThat(bots.bots("#combi").join(), isEmpty)
            }
        }

        @Test
        fun `channels for each bot`() {
            val testData = fetchTestData("bots_test")
            val mainAdminToken = testData.userMap["MainAdmin"]!!.token
            courseApp.channelJoin(mainAdminToken, "#nafas").join()
            val bot10 = testData.bots["Bot_10"]!!
            val bot7 = testData.bots["Bot_7"]!!
            assertWithTimeout {
                assertThat(bot10.channels().join(), containsElementsInOrder("#channel_0", "#channel_1", "#channel_2",
                        "#channel_3", "#channel_4"))
                assertThat(bot7.channels().join(), containsElementsInOrder("#channel_0", "#channel_1", "#channel_4",
                        "#channel_3"))
                bot10.part("#channel_2").thenCompose { bot10.part("#channel_3") }
                        .thenCompose { bot10.join("#nafas") }.join()
                assertThat(bot10.channels().join(), containsElementsInOrder("#channel_0", "#channel_1", "#channel_4", "#nafas"))
            }
        }

        @Test
        fun `active user in channel`() {
            val testData = fetchTestData("small_test")
            val bot2 = testData.bots["Bot_2"]!!
            val bot3 = testData.bots["Bot_3"]!!
            sendMessagesOfTest("small_test", testData)
            val u131token = testData.userMap["User131"]!!.token
            val u57token = testData.userMap["User57"]!!.token
            val u1token = testData.userMap["User1"]!!.token
            courseApp.channelSend(u131token, "#channel_0", buildMessage("Looking for a partner")).join()
            bot3.join("#channel_0").thenCompose { courseApp.channelSend(u57token, "#channel_0", buildMessage("Any references for HW1?")) }
                    .thenCompose { courseApp.channelSend(u131token, "#channel_0", buildMessage("Looking for a partner again")) }
                    .thenCompose { courseApp.channelSend(u57token, "#channel_0", buildMessage("Any references for HW2 aswell?")) }
                    .thenCompose { courseApp.channelSend(u1token, "#channel_2", buildMessage("Any references for HW2 aswell?")) }
                    .join()

            assertWithTimeout {
                assertThat(bot2.mostActiveUser("#channel_0").join(), equalTo("User131"))
                assertThat(bot2.mostActiveUser("#channel_2").join(), equalTo("User1"))
                assertThat(bot3.mostActiveUser("#channel_0").join(), equalTo("User57"))
                assertThat(bot3.mostActiveUser("#channel_2").join(), equalTo("User1"))
            }
        }
    }


    @Nested
    inner class MainBigTest {

        @Test
        fun `multiple bots doing survey on a single channel`() {
            val testData = fetchTestData("large_test")
            val bot0 = testData.bots["Bot_0"]!!
            val bot2 = testData.bots["Bot_2"]!!
            val bot4 = testData.bots["Bot_4"]!!
            val surveyMap = HashMap<CourseBot, String>()
            surveyMap[bot0] = bot0.runSurvey("#channel_11", "What do you think of the course?", listOf("Answer0", "Answer3", "Answer7")).join()
            surveyMap[bot2] = bot2.runSurvey("#channel_11", "TEST2", listOf("Answer1", "Answer2", "Answer4")).join()
            surveyMap[bot4] = bot4.runSurvey("#channel_11", "TEST5", listOf("Answer5", "Answer6")).join()

            sendMessages("large_test_survey.csv", testData)

            assertWithTimeout {
                assertThat(bot0.surveyResults(surveyMap[bot0]!!).join(), containsElementsInOrder(60L, 62L, 57L))
                assertThat(bot2.surveyResults(surveyMap[bot2]!!).join(), containsElementsInOrder(60L, 52L, 58L))
                assertThat(bot4.surveyResults(surveyMap[bot4]!!).join(), containsElementsInOrder(61L, 55L))
            }
        }

        @Test
        fun `multiple surveys by a single bot`() {
            val testData = fetchTestData("large_test")
            val bot6 = testData.bots["Bot_6"]!!
            val surveyMap = HashMap<String, String>()
            surveyMap["#channel_0"] = bot6.runSurvey("#channel_0", "Q1", listOf("Answer7", "Answer5")).join()
            surveyMap["#channel_5"] = bot6.runSurvey("#channel_5", "Q2", listOf("Answer7", "Answer4", "Answer2")).join()
            surveyMap["#channel_7"] = bot6.runSurvey("#channel_7", "Q3", listOf("Answer1", "Answer0", "Answer2", "Answer7")).join()
            sendMessages("large_test_survey.csv", testData)

            assertWithTimeout(
                    {
                        assertThat(bot6.surveyResults(surveyMap["#channel_0"]!!).join(), containsElementsInOrder(64L, 56L))
                        assertThat(bot6.surveyResults(surveyMap["#channel_5"]!!).join(), containsElementsInOrder(59L, 47L, 51L))
                        assertThat(bot6.surveyResults(surveyMap["#channel_7"]!!).join(), containsElementsInOrder(57L, 39L, 45L, 47L))
                    }, Duration.ofSeconds(30)
            )


        }
    }
    
    @Nested
    inner class CourseBotTest {

        @Test
        fun `Can create a bot and make it join channels`() {
            val token = courseApp.login("sahar", "a very strong password").join()

            assertThat(runWithTimeout(Duration.ofSeconds(10)) {
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

            Assertions.assertEquals(0L, count)
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

            Assertions.assertEquals(0L, count)
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

            Assertions.assertNull(bot.mostActiveUser("#TakeCare").join())
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
            Assertions.assertNull(bot.richestUser(channel).join())
        }

        @Test
        fun `Asking the bot when a user who has not sent any messages was last seen should return null`() {
            courseApp.login("admin", "admin").join()

            val bot = bots.bot().join()

            Assertions.assertNull(bot.seenTime("admin").join())
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

            Assertions.assertEquals("Sahar", richestUser)
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

            Assertions.assertEquals("Sahar", mostActiveUser)
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
            Assertions.assertTrue(time2.toString() > time1.toString())
            Thread.sleep(200)

            courseApp.channelSend(adminToken, "#TakeCare2", messageFactory.create(
                    MediaType.TEXT, "second message".toByteArray()).join()).join()

            val time3 = bot.seenTime("sahar").join()!!
            Assertions.assertTrue(time3.toString() > time2.toString())
        }

        @Test
        fun `Can list bots in a channel`() {
            courseApp.login("sahar", "a very strong password")
                    .thenCompose { adminToken ->
                        courseApp.channelJoin(adminToken, "#channel")
                                .thenCompose { bots.bot().thenCompose { it.join("#channel") } }
                    }.join()

            assertThat(runWithTimeout(Duration.ofSeconds(10)) {
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

            assertThat(runWithTimeout(Duration.ofSeconds(10)) {
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

            assertThat(runWithTimeout(Duration.ofSeconds(10)) {
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
            assertThat(runWithTimeout(Duration.ofSeconds(10)) {
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

            Assertions.assertEquals(listOf("Anna0", "Anna1", "Yossi", "Anna3"), bots)
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

            Assertions.assertEquals(previousTrigger, "Hello")
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

            Assertions.assertEquals(previousTrigger, "Hello")
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

            Assertions.assertNull(bot.richestUser(channel).join())
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

            Assertions.assertNull(bot.richestUser(channel).join())
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

            Assertions.assertEquals("yuval", bot1.richestUser(channel).join())
            Assertions.assertNull(bot2.richestUser(channel).join())

            courseApp.channelSend(otherToken, channel, messageFactory.create(
                    MediaType.TEXT, "$tipTrigger 200 sahar".toByteArray()).join()).join()

            Assertions.assertEquals("yuval", bot1.richestUser(channel).join())
            Assertions.assertEquals("sahar", bot2.richestUser(channel).join())
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

            Assertions.assertEquals("yuval", bot.richestUser(channel1).join())
            Assertions.assertEquals("sahar", bot.richestUser(channel2).join())
        }

        @Test
        fun `Bots' callbacks operate only on text messages, except for counting messages & keywords tracking`() {
            val listener = mockk<ListenerCallback>()
            every { listener(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

            val adminToken = courseApp.login("sahar", "a very strong password").thenCompose { adminToken ->
                courseApp.login("yuval", "a very weak password").thenApply { adminToken }
            }.join()

            val channel = "#channel"
            val trigger = "!hello:"
            courseApp.addListener(adminToken, listener).thenCompose {
                courseApp.channelJoin(adminToken, channel)
            }.join()

            val bot = bots.bot().thenCompose { bot ->
                bot.setTipTrigger(trigger).thenCompose {
                    bot.setCalculationTrigger(trigger).thenCompose {
                        bot.join(channel).thenApply { bot }
                    }
                }
            }.join()

            // tipping
            courseApp.channelSend(adminToken, channel, messageFactory.create(
                    MediaType.PICTURE, "$trigger 100 yuval".toByteArray()).join()).join()
            // calculating
            courseApp.channelSend(adminToken, channel, messageFactory.create(
                    MediaType.PICTURE, "$trigger (40+40)/4".toByteArray()).join()).join()
            //survey
            val surveyIdentifier = bot.runSurvey(channel, "will this test fail?", listOf("yes", "no")).join()
            courseApp.channelSend(adminToken, channel, messageFactory.create(
                    MediaType.PICTURE, "no".toByteArray()).join()).join()

            // verify that the bot did not calculate the given expression
            verify {
                listener.invoke("#channel@sahar", match { String(it.contents) == "$trigger 100 yuval" })
                listener.invoke("#channel@sahar", match { String(it.contents) == "$trigger (40+40)/4" }) // no reply to this!
                listener.invoke("#channel@sahar", match { String(it.contents) == "no" })
                listener.invoke("#channel@Anna0", match { String(it.contents) == "will this test fail?" })
            }

            Assertions.assertNull(bot.richestUser(channel).join())
            val surveyResult = bot.surveyResults(surveyIdentifier).join()
            Assertions.assertEquals(listOf(0L, 0L), surveyResult)
        }

        @Test
        fun `Bot's channel statistics are deleted upon leaving a channel`() {
            val adminToken = courseApp.login("sahar", "a very strong password").thenCompose { adminToken ->
                courseApp.login("yuval", "a very weak password").thenApply { adminToken }
            }.join()

            val channel = "#TakeCare"
            val tipTrigger = "!tip"
            val calculatorTrigger = "!calculator"
            courseApp.channelJoin(adminToken, channel).join()
            val bot = bots.bot().thenCompose { bot ->
                bot.setTipTrigger(tipTrigger).thenCompose {
                    bot.setCalculationTrigger(calculatorTrigger).thenCompose {
                        bot.join(channel).thenApply { bot }
                    }
                }
            }.join()

            // regex
            bot.beginCount(channel, null, MediaType.TEXT).join()
            // tip
            courseApp.channelSend(adminToken, channel, messageFactory.create(
                    MediaType.TEXT, "$tipTrigger 100 yuval".toByteArray()).join()).join()
            // survey
            val surveyIdentifier = bot.runSurvey(channel, "will this test fail?", listOf("yes", "no")).join()
            courseApp.channelSend(adminToken, channel, messageFactory.create(
                    MediaType.TEXT, "no".toByteArray()).join()).join()

            // * bot has left the channel (and then joined it)
            bot.part(channel).thenCompose {
                bot.join(channel)
            }.join()

            // these stay the same:
            Assertions.assertEquals(calculatorTrigger, bot.setCalculationTrigger(null).join())
            Assertions.assertEquals(tipTrigger, bot.setTipTrigger(null).join())
            Assertions.assertNotNull(bot.seenTime("sahar").join())

            // these are removed:
            Assertions.assertNull(bot.mostActiveUser(channel).join())
            Assertions.assertNull(bot.richestUser(channel).join())

            // these are reset
            Assertions.assertEquals(listOf(0L, 0L), bot.surveyResults(surveyIdentifier).join())
//            Assertions.assertEquals(0L, bot.count(channel, null, MediaType.TEXT).join())
        }

        @Test
        internal fun `survey results are correct when users change their votes`() {
            val (sahar, yuval, victor) = courseApp.login("sahar", "a very strong password").thenCompose { adminToken ->
                courseApp.login("yuval", "a very weak password")
                        .thenApply { Pair(adminToken, it) }
                        .thenCompose { pair ->
                            courseApp.login("victor", "anak")
                                    .thenApply { Triple(pair.first, pair.second, it) }
                        }
            }.join()

            val channel = "#TakeCare"
            courseApp.channelJoin(sahar, channel).thenCompose {
                courseApp.channelJoin(yuval, channel).thenCompose {
                    courseApp.channelJoin(victor, channel)
                }
            }.join()

            val bot = bots.bot().thenCompose { bot ->
                bot.join(channel).thenApply { bot }
            }.join()

            val id1 = bot.runSurvey(channel, "Test1", listOf("sahar", "yuval", "victor")).join()
            val id2 = bot.runSurvey(channel, "Test2", listOf("1", "2", "3")).join()

            courseApp.channelSend(sahar, channel, messageFactory.create(
                    MediaType.TEXT, "yuval".toByteArray()).join()).join()
            courseApp.channelSend(yuval, channel, messageFactory.create(
                    MediaType.TEXT, "yuval".toByteArray()).join()).join()
            courseApp.channelSend(victor, channel, messageFactory.create(
                    MediaType.TEXT, "yuval".toByteArray()).join()).join()

            assertEquals(listOf(0L, 3L, 0L), bot.surveyResults(id1).join())

            courseApp.channelSend(sahar, channel, messageFactory.create(
                    MediaType.TEXT, "1".toByteArray()).join()).join()
            courseApp.channelSend(yuval, channel, messageFactory.create(
                    MediaType.TEXT, "2".toByteArray()).join()).join()
            courseApp.channelSend(victor, channel, messageFactory.create(
                    MediaType.TEXT, "3".toByteArray()).join()).join()

            assertEquals(listOf(0L, 3L, 0L), bot.surveyResults(id1).join())
            assertEquals(listOf(1L, 1L, 1L), bot.surveyResults(id2).join())

            // change votes
            courseApp.channelSend(sahar, channel, messageFactory.create(
                    MediaType.TEXT, "sahar".toByteArray()).join()).join()
            courseApp.channelSend(victor, channel, messageFactory.create(
                    MediaType.TEXT, "victor".toByteArray()).join()).join()

            courseApp.channelSend(sahar, channel, messageFactory.create(
                    MediaType.TEXT, "1".toByteArray()).join()).join()
            courseApp.channelSend(yuval, channel, messageFactory.create(
                    MediaType.TEXT, "2".toByteArray()).join()).join()
            courseApp.channelSend(victor, channel, messageFactory.create(
                    MediaType.TEXT, "2".toByteArray()).join()).join()

            assertEquals(listOf(1L, 1L, 1L), bot.surveyResults(id1).join())
            assertEquals(listOf(1L, 2L, 0L), bot.surveyResults(id2).join())
        }
    }

    private fun sendMessages(filename: String, testData: TestData) {
        sendMessagesByFile(filename, messageFactory, courseApp, testData.userMap)
    }


}
