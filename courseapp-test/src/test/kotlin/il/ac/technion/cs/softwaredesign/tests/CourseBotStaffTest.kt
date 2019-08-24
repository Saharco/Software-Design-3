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
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration


class CourseBotStaffTest {
    private val injector = Guice.createInjector(CourseAppModule(), CourseBotModule(), SecureStorageModule())

    private val courseApp = injector.getInstance<CourseApp>()
    private val bots = injector.getInstance<CourseBots>()
    private val messageFactory = injector.getInstance<MessageFactory>()

    init {
        injector.getInstance<CourseAppInitializer>().setup().join()
        bots.prepare().thenCompose{bots.start()}.join()
    }
    private fun sendMessagesOfTest(testName : String,testData: TestData) {
        sendMessagesByTest(testName, messageFactory, courseApp, testData.userMap)
    }

    private fun fetchTestData(testName : String) = loadDataForTestWithoutMessages(courseApp, testName, bots)

    private fun buildMessage(content : String) : Message
    {
        return messageFactory.create(MediaType.TEXT, stringToByteArray(content)).join()
    }

    private fun getCalculatedNumbersFromChannel(testData: TestData,channel : String) =
            testData.userMap[getAdminOfChannel(channel)]!!.messages
                    .map { p -> byteArrayToString(p.second.contents)!!.toLong() }

    @Nested
    inner class BasicTest {

        @Test
        fun `join channel returns as expected`() {
            val adminToken = courseApp.login("admin", "pass").join()
            courseApp.channelJoin(adminToken, "#hagana").join()
            val bot = bots.bot().join()

            assertWithTimeout{
                assertThrows<UserNotAuthorizedException>{bot.join("hagana").joinException()}
                assertThrows<UserNotAuthorizedException>{bot.join("#dsad").joinException()}
                assertDoesNotThrow{ bot.join("#hagana").join()}
            }
        }

        @Test
        fun `part channel returns as expected`()
        {
            val adminToken = courseApp.login("admin", "pass").join()
            courseApp.channelJoin(adminToken, "#opti").join()
            val bot = bots.bot().join()
            bot.join("#opti")

            assertWithTimeout{
                assertThrows<NoSuchEntityException>{bot.part("maman").joinException()}
                assertThrows<NoSuchEntityException>{bot.part("#dsad").joinException()}
                bot.part("#opti").join()
                assertThrows<NoSuchEntityException> {bot.part("#hagana").joinException() }
            }
        }

        @Test
        fun `channels count for small amount`()
        {
            val adminToken = courseApp.login("admin", "pass").join()
            courseApp.channelJoin(adminToken, "#opti")
                    .thenCompose { courseApp.channelJoin(adminToken,"#stats") }
                    .thenCompose { courseApp.channelJoin(adminToken, "#intro_to_network") }
            val bot = bots.bot().join()

            assertWithTimeout {
                assertThat(bot.channels().join().size, equalTo(0))
                bot.join("#stats").thenCompose{bot.join("#opti")}.join()
                assertThat(bot.channels().join(),containsElementsInOrder("#stats","#opti"))
            }
        }

        @Test
        fun `seen time returns as expected`()
        {
            val adminToken = courseApp.login("admin", "pass").join()
            courseApp.channelJoin(adminToken, "#opti")
                    .thenCompose { courseApp.channelJoin(adminToken,"#stats") }
                    .thenCompose { courseApp.channelJoin(adminToken, "#intro_to_network") }.join()
            val bot = bots.bot().join()
            bot.join("#stats").thenCompose{bot.join("#opti")}.join()
            val msg1 = buildMessage("First one")
            val msg2 = buildMessage("Second!")
            val msg3 = buildMessage("!")
            assertWithTimeout {
                assertThat(bot.seenTime("admin").join(), absent())
                assertThat(bot.seenTime("dsad").join(), absent())
                courseApp.channelSend(adminToken, "#opti",msg2)
                        .thenCompose { courseApp.channelSend(adminToken, "#stats", msg1) }
                        .thenCompose { courseApp.channelSend(adminToken, "#intro_to_network", msg3) }.join()
                assertThat(bot.seenTime("admin").join(), equalTo(msg1.created))
            }
        }

        @Test
        fun `count mechanism in error scenarios`()
        {
            val adminToken = courseApp.login("admin", "pass").join()
            courseApp.channelJoin(adminToken, "#opti")
                    .thenCompose { courseApp.channelJoin(adminToken,"#compi") }
                    .thenCompose { courseApp.channelJoin(adminToken, "#theory_of_computation") }
            val bot = bots.bot().join()
            bot.join("#compi").thenCompose{bot.join("#theory_of_computation")}.join()

            assertWithTimeout {
                assertThrows<IllegalArgumentException> { bot.beginCount("#compi",null,null).joinException() }
                assertThrows<IllegalArgumentException> { bot.count("#compi",null,null).joinException() }
                assertDoesNotThrow{bot.beginCount(null,null,MediaType.TEXT).joinException()}
                assertThat(bot.count(null,null,MediaType.TEXT).join(), equalTo(0L))
            }
        }

        @Test
        fun `calc trigger returns as expected`()
        {
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
        fun `tip trigger returns as expected`()
        {
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
        fun `mostActiveUser returns as expected`()
        {
            val adminToken = courseApp.login("admin", "pass").join()
            courseApp.channelJoin(adminToken, "#oop").thenCompose { courseApp.channelJoin(adminToken, "#automatons") }.join()
            val bot = bots.bot().join()
            bot.join("#oop").join()
            assertWithTimeout {
                assertThat(bot.mostActiveUser("#oop").join(), absent())
                assertThrows<NoSuchEntityException> {bot.mostActiveUser("#automatons").joinException()  }
                courseApp.channelSend(adminToken,"#oop", buildMessage("Some message")).join()
                assertThat(bot.mostActiveUser("#oop").join(), equalTo("admin"))
            }
        }

        @Test
        fun `mostActiveUser reset after leaving channel`()
        {
            val adminToken = courseApp.login("admin", "pass").join()
            courseApp.channelJoin(adminToken, "#oop").thenCompose { courseApp.channelJoin(adminToken, "#automatons") }.join()
            val bot = bots.bot().join()
            bot.join("#oop").join()
            courseApp.channelSend(adminToken,"#oop", buildMessage("Some message")).thenCompose {
                bot.part("#oop")
            }.join()

            assertWithTimeout {
                assertThrows<NoSuchEntityException> {bot.mostActiveUser("#oop").joinException()  }
                bot.join("#oop").join()
                assertThat(bot.mostActiveUser("#oop").join(), absent())
            }
        }

        @Test
        fun `basic survey running returns as expected`()
        {
            val adminToken = courseApp.login("admin", "pass").join()
            courseApp.channelJoin(adminToken, "#opti")
                    .thenCompose { courseApp.channelJoin(adminToken,"#compi") }
                    .thenCompose { courseApp.channelJoin(adminToken, "#theory_of_computation") }
            val bot = bots.bot().join()
            bot.join("#compi").join()

            assertWithTimeout {
                assertThrows<NoSuchEntityException> { bot.runSurvey("#opti","Q3",listOf("Does it matter?")).joinException() }
                assertThrows<NoSuchEntityException> { bot.runSurvey("#shmopti","Q3",listOf("Does it matter?")).joinException() }
                assertDoesNotThrow{bot.runSurvey("#compi","How hard is HW4?",listOf("Hard","Very Hard")).joinException()}
            }
        }

        @Test
        fun `survey results return as expected`()
        {
            val adminToken = courseApp.login("admin", "pass").join()
            courseApp.channelJoin(adminToken, "#opti")
                    .thenCompose { courseApp.channelJoin(adminToken,"#compi") }
                    .thenCompose { courseApp.channelJoin(adminToken, "#theory_of_computation") }
            val bot = bots.bot().join()
            val surveyId = bot.join("#compi").thenCompose { bot.runSurvey("#compi","How hard is HW4?",listOf("Hard","Very Hard")) }.join()

            assertWithTimeout {
                assertThat(bot.surveyResults(surveyId).join(), containsElementsInOrder(0L,0L))
                assertThrows<NoSuchEntityException> { bot.surveyResults(surveyId+"Garbage").joinException() }
                courseApp.channelSend(adminToken,"#compi", buildMessage("Hard")).join()
                assertThat(bot.surveyResults(surveyId).join(), containsElementsInOrder(1L,0L))
                courseApp.channelSend(adminToken,"#compi", buildMessage("Very Hard")).join()
                assertThat(bot.surveyResults(surveyId).join(), containsElementsInOrder(0L,1L))
            }
        }

        @Test
        fun `bot creation with default names`()
        {
            val adminToken = courseApp.login("admin", "pass").join()
            courseApp.channelJoin(adminToken, "#algo").join()
            bots.bot().thenCompose { bots.bot("Arno") }.thenCompose { bots.bot() }
                    .thenCompose { bot->bot.join("#algo") }
                    .thenCompose { bots.bot("Ezio") }
                    .thenCompose { bot->bot.join("#algo") }.join()
            assertWithTimeout {
                assertThat(bots.bots(null).join(), containsElementsInOrder("Anna0","Arno","Anna2","Ezio"))
                assertThat(bots.bots("#algo").join(), containsElementsInOrder("Anna2","Ezio"))
            }
        }
    }

    @Nested
    inner class MainSmallTest
    {

        @Test
        fun `count mechanism by multiple bots on same channel`()
        {
            val testData = fetchTestData("small_test")
            val bot0 = testData.bots["Bot_0"]!!
            val bot2= testData.bots["Bot_2"]!!
            val bot4 = testData.bots["Bot_4"]!!

            bot0.beginCount("#channel_0","(.*sR.*)|(.*UU.*)",MediaType.FILE).join()
            bot4.beginCount("#channel_0", "75916_[^_]*_7.*",null).join()
            bot2.beginCount("#channel_0", ".*33581\$",MediaType.TEXT).join()
            sendMessagesOfTest("small_test", testData)

            assertWithTimeout {
                assertThat(bot0.count("#channel_0","(.*sR.*)|(.*UU.*)",MediaType.FILE).join()!!, equalTo(3L))
                assertThat(bot4.count("#channel_0", "75916_[^_]*_7.*",null).join()!!, equalTo(9L))
                assertThat(bot2.count("#channel_0", ".*33581\$",MediaType.TEXT).join(), equalTo(7L))
                bot0.beginCount("#channel_0","(.*sR.*)|(.*UU.*)",MediaType.FILE)
                assertThat(bot0.count("#channel_0","(.*sR.*)|(.*UU.*)",MediaType.FILE).join()!!, equalTo(0L))

            }

        }

        @Test
        fun `multiple count mechanism by single bot`()
        {
            val testData = fetchTestData("small_test")
            val bot4 = testData.bots["Bot_4"]!!
            bot4.beginCount("#channel_0","(.*RU.*)|(^33581.*)",MediaType.AUDIO).join()
            bot4.beginCount("#channel_3", "(.*RU.*)|(^33581.*)",null).join()
            bot4.beginCount("#channel_4", "(.*RU.*)|(^33581.*)",MediaType.REFERENCE).join()
            bot4.beginCount(null,"(.*RU.*)|(^33581.*)",MediaType.STICKER).join()
            sendMessagesOfTest("small_test", testData)

            assertWithTimeout {
                assertThat(bot4.count("#channel_0","(.*RU.*)|(^33581.*)",MediaType.AUDIO).join(), equalTo(8L))
                assertThat(bot4.count("#channel_3","(.*RU.*)|(^33581.*)",null).join(), equalTo(63L))
                assertThat(bot4.count("#channel_4","(.*RU.*)|(^33581.*)",MediaType.REFERENCE).join(), equalTo(7L))
                assertThat(bot4.count(null,"(.*RU.*)|(^33581.*)",MediaType.STICKER).join(), equalTo(36L))

            }
        }

        @Test
        fun `calc trigger test of multiple bots `()
        {
            val testData = fetchTestData("small_test")
            val bot1 =testData.bots["Bot_1"]!!
            val bot0 = testData.bots["Bot_0"]!!
            bot1.setCalculationTrigger("trigger1").thenCompose { bot0.setCalculationTrigger("trigger0") }.join()
            val user0token=testData.userMap["User0"]!!.token
            val user9token=testData.userMap["User9"]!!.token
            courseApp.channelSend(user0token,"#channel_3",buildMessage("trigger0 (5+7)/6"))
                    .thenCompose { courseApp.channelSend(user0token,"#channel_3",buildMessage("trigger1 (45*2)-20")) }
                    .thenCompose { courseApp.channelSend(user0token,"#channel_0",buildMessage("trigger0 70+4")) }
                    .thenCompose { courseApp.channelSend(user9token,"#channel_0",buildMessage("trigger0 23")) }
                    .thenCompose { courseApp.channelSend(user9token,"#channel_4",buildMessage("trigger1 35-5*2"))  }
                    .thenCompose { courseApp.channelSend(user9token,"#channel_1",buildMessage("trigger0 46")) }
                    .thenCompose { courseApp.channelSend(user9token,"#channel_1",buildMessage("trigger1 734-40*2")) }.join()

            assertWithTimeout {
                assertThat(getCalculatedNumbersFromChannel(testData,"#channel_0"), containsElementsInOrder(74L,23L))
                assertThat(getCalculatedNumbersFromChannel(testData,"#channel_1"), containsElementsInOrder(46L,654L))
                assertThat(getCalculatedNumbersFromChannel(testData,"#channel_4"), containsElementsInOrder(25L))
                assertThat(getCalculatedNumbersFromChannel(testData,"#channel_3"), containsElementsInOrder(2L,70L))
            }
        }

        @Test
        fun `calc trigger test with disable in the middle `()
        {
            val testData = fetchTestData("small_test")
            val bot1 = testData.bots["Bot_1"]!!
            val bot0 = testData.bots["Bot_0"]!!
            bot1.setCalculationTrigger("trigger1").thenCompose { bot0.setCalculationTrigger("trigger0") }.join()
            val user0token=testData.userMap["User0"]!!.token
            val user9token=testData.userMap["User9"]!!.token
            courseApp.channelSend(user0token,"#channel_3",buildMessage("trigger0 (5+7)/6"))
                    .thenCompose { courseApp.channelSend(user0token,"#channel_3",buildMessage("trigger1 (45*2)-20")) }
                    .thenCompose { courseApp.channelSend(user0token,"#channel_0",buildMessage("trigger0 30+4")) }
                    .thenCompose { bot0.setCalculationTrigger(null) }
                    .thenCompose { courseApp.channelSend(user9token,"#channel_0",buildMessage("trigger0 23")) }
                    .thenCompose { courseApp.channelSend(user0token,"#channel_3",buildMessage("trigger0 (15*7)-10"))}
                    .thenCompose { bot1.setCalculationTrigger(null) }
                    .thenCompose { courseApp.channelSend(user9token,"#channel_1",buildMessage("trigger0 46")) }
                    .thenCompose { courseApp.channelSend(user9token,"#channel_1",buildMessage("trigger1 734-40*2")) }
                    .thenCompose { courseApp.channelSend(user0token,"#channel_3",buildMessage("trigger1 15000+1"))}.
                            join()


            assertWithTimeout {
                assertThat(getCalculatedNumbersFromChannel(testData,"#channel_0"), containsElementsInOrder(34L))
                assertThat(getCalculatedNumbersFromChannel(testData,"#channel_1"), isEmpty)
                assertThat(getCalculatedNumbersFromChannel(testData,"#channel_3"), containsElementsInOrder(2L,70L))
            }
        }

        @Test
        fun `tip trigger of single bot`()
        {
            val testData = fetchTestData("small_test")
            val bot4 =testData.bots["Bot_4"]!!
            bot4.setTipTrigger("tiptrigger4").join()
            sendTipsByTest("small_test",messageFactory,courseApp,testData.userMap)

            assertWithTimeout {
                assertThat(bot4.richestUser("#channel_0").join(), equalTo("User19"))
                assertThat(bot4.richestUser("#channel_3").join(), equalTo("User101"))
                assertThat(bot4.richestUser("#channel_4").join(), equalTo("User163"))
            }

        }

        @Test
        fun `tip trigger of multiple bots`()
        {
            val testData = fetchTestData("small_test")

            val bot1 =testData.bots["Bot_1"]!!
            val bot0 =testData.bots["Bot_0"]!!
            val bot3 =testData.bots["Bot_3"]!!
            bot0.setTipTrigger("tiptrigger0").join()
            bot1.setTipTrigger("tiptrigger1").join()
            bot3.setTipTrigger("tiptrigger3").join()
            sendTipsByTest("small_test",messageFactory,courseApp,testData.userMap)
            /*val u48token=testData.userMap["User48"]!!.token
            courseApp.channelSend(u48token,"#channel_3",buildMessage("tiptrigger0 7 User50"))
                    .thenCompose { courseApp.channelSend(u48token,"#channel_3",buildMessage("tiptrigger1 8 User134")) }
                    .thenCompose { courseApp.channelSend(u48token,"#channel_3",buildMessage("tiptrigger3 8 User60")) }
                    .thenCompose { courseApp.channelSend(u48token,"#channel_3",buildMessage("tiptrigger3 8 User133")) }
                    .join()*/
            assertWithTimeout{
                assertThat(bot0.richestUser("#channel_3").join(), equalTo("User50"))
                assertThat(bot1.richestUser("#channel_3").join(), equalTo("User134"))
                assertThat(bot3.richestUser("#channel_3").join(), absent())
            }
        }

        @Test
        fun `bots statistic test`()
        {
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
        fun `channels for each bot`()
        {
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
                bot10.part("#channel_2").thenCompose{bot10.part("#channel_3")}
                        .thenCompose { bot10.join("#nafas") }.join()
                assertThat(bot10.channels().join(), containsElementsInOrder("#channel_0", "#channel_1", "#channel_4","#nafas"))
            }
        }

        @Test
        fun `active user in channel`()
        {
            val testData = fetchTestData("small_test")
            val bot2 = testData.bots["Bot_2"]!!
            val bot3 = testData.bots["Bot_3"]!!
            sendMessagesOfTest("small_test", testData)
            val u131token = testData.userMap["User131"]!!.token
            val u57token = testData.userMap["User57"]!!.token
            val u1token = testData.userMap["User1"]!!.token
            courseApp.channelSend(u131token, "#channel_0",buildMessage("Looking for a partner")).join()
            bot3.join("#channel_0").thenCompose { courseApp.channelSend(u57token,"#channel_0", buildMessage("Any references for HW1?")) }
                    .thenCompose { courseApp.channelSend(u131token, "#channel_0",buildMessage("Looking for a partner again")) }
                    .thenCompose { courseApp.channelSend(u57token,"#channel_0", buildMessage("Any references for HW2 aswell?")) }
                    .thenCompose { courseApp.channelSend(u1token,"#channel_2", buildMessage("Any references for HW2 aswell?")) }
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
    inner class MainBigTest
    {

        @Test
        fun `multiple bots doing survey on a single channel`()
        {
            val testData = fetchTestData("large_test")
            val bot0 = testData.bots["Bot_0"]!!
            val bot2= testData.bots["Bot_2"]!!
            val bot4 = testData.bots["Bot_4"]!!
            val surveyMap = HashMap<CourseBot,String>()
            surveyMap[bot0] = bot0.runSurvey("#channel_11", "What do you think of the course?",listOf("Answer0","Answer3","Answer7")).join()
            surveyMap[bot2] = bot2.runSurvey("#channel_11", "TEST2",listOf("Answer1","Answer2","Answer4")).join()
            surveyMap[bot4] = bot4.runSurvey("#channel_11", "TEST5",listOf("Answer5","Answer6")) .join()

            sendMessages("large_test_survey.csv",testData)

            assertWithTimeout {
                assertThat(bot0.surveyResults(surveyMap[bot0]!!).join(), containsElementsInOrder(60L,62L,57L))
                assertThat(bot2.surveyResults(surveyMap[bot2]!!).join(), containsElementsInOrder(60L,52L,58L))
                assertThat(bot4.surveyResults(surveyMap[bot4]!!).join(), containsElementsInOrder(61L,55L))
            }
        }

        @Test
        fun `multiple surveys by a single bot`()
        {
            val testData = fetchTestData("large_test")
            val bot6 = testData.bots["Bot_6"]!!
            val surveyMap = HashMap<String,String>()
            surveyMap["#channel_0"] = bot6.runSurvey("#channel_0","Q1",listOf("Answer7","Answer5")).join()
            surveyMap["#channel_5"] = bot6.runSurvey("#channel_5","Q2",listOf("Answer7","Answer4","Answer2")).join()
            surveyMap["#channel_7"] = bot6.runSurvey("#channel_7","Q3",listOf("Answer1","Answer0","Answer2","Answer7")).join()
            sendMessages("large_test_survey.csv",testData)

            assertWithTimeout(
                    {
                        assertThat(bot6.surveyResults(surveyMap["#channel_0"]!!).join(), containsElementsInOrder(64L,56L))
                        assertThat(bot6.surveyResults(surveyMap["#channel_5"]!!).join(), containsElementsInOrder(59L,47L,51L))
                        assertThat(bot6.surveyResults(surveyMap["#channel_7"]!!).join(), containsElementsInOrder(57L,39L,45L,47L))
                    }, Duration.ofSeconds(30)
            )


        }
    }

    private fun sendMessages(filename : String,testData: TestData) {
        sendMessagesByFile(filename, messageFactory, courseApp, testData.userMap)
    }


}
