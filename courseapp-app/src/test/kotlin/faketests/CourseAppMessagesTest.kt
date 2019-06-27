import com.authzee.kotlinguice4.getInstance
import com.google.inject.Guice
import fakes.CourseAppStatistics
import il.ac.technion.cs.softwaredesign.*
import il.ac.technion.cs.softwaredesign.exceptions.InvalidTokenException
import il.ac.technion.cs.softwaredesign.exceptions.NoSuchEntityException
import il.ac.technion.cs.softwaredesign.exceptions.UserNotAuthorizedException
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import io.mockk.called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

class CourseAppMessagesTest {

    private val injector = Guice.createInjector(CourseAppFakeTestModule())
    private val messageFactory = injector.getInstance<MessageFactory>()
    private val app = injector.getInstance<CourseApp>()
    private val statistics = injector.getInstance<CourseAppStatistics>()

    init {
        injector.getInstance<CourseAppInitializer>().setup().join()
    }

    @Test
    internal fun `trying to attach a listener with an invalid token throws InvalidTokenException`() {
        val listener = mockk<ListenerCallback>()
        every { listener(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))
        assertThrows<InvalidTokenException> {
            app.addListener("bad token", listener).joinException()
        }
    }

    @Test
    internal fun `trying to remove a listener that is not registered should throw NoSuchEntityException`() {
        val listener = mockk<ListenerCallback>()
        every { listener(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))
        val token = app.login("Admin", "a very strong password").join()
        assertThrows<NoSuchEntityException> {
            app.removeListener(token, listener).joinException()
        }
    }

    @Test
    internal fun `user listener is called when a private message is sent`() {
        val listener = mockk<ListenerCallback>()
        every { listener(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))
        val (adminToken, message) = app.login("Admin", "a very strong password")
                .thenCompose { adminToken ->
                    app.login("Sahar", "a very weak password")
                            .thenApply { Pair(adminToken, it) }
                }.thenCompose { (adminToken, nonAdminToken) ->
                    app.addListener(nonAdminToken, listener)
                            .thenApply { adminToken }
                }.thenCompose { adminToken ->
                    messageFactory.create(MediaType.STICKER, "Smiley".toByteArray())
                            .thenApply { Pair(adminToken, it) }
                }.join()

        assertEquals(0, statistics.pendingMessages().join())
        app.privateSend(adminToken, "Sahar", message).join()
        assertEquals(0, statistics.pendingMessages().join())

        verify {
            listener(match { it == "@Admin" },
                    match { it.contents contentEquals "Smiley".toByteArray() })
        }
    }

    @Test
    internal fun `user listeners are called when a channel message is sent`() {
        val listener1 = mockk<ListenerCallback>()
        every { listener1(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val listener2 = mockk<ListenerCallback>()
        every { listener2(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val (adminToken, message) = app.login("Admin", "a very strong password")
                .thenCompose { adminToken ->
                    app.channelJoin(adminToken, "#TakeCare")
                            .thenApply { adminToken }
                }.thenCompose { adminToken ->
                    app.login("Sahar", "a very weak password")
                            .thenApply { Pair(adminToken, it) }
                }.thenCompose { (adminToken, nonAdminToken) ->
                    app.channelJoin(nonAdminToken, "#TakeCare")
                            .thenApply { Pair(adminToken, nonAdminToken) }
                }.thenCompose { (adminToken, nonAdminToken) ->
                    app.addListener(nonAdminToken, listener1)
                            .thenApply { adminToken }
                }.thenCompose { adminToken ->
                    app.login("Yuval", "pizza")
                            .thenApply { Pair(adminToken, it) }
                }.thenCompose { (adminToken, nonAdminToken) ->
                    app.channelJoin(nonAdminToken, "#TakeCare")
                            .thenApply { Pair(adminToken, nonAdminToken) }
                }.thenCompose { (adminToken, nonAdminToken) ->
                    app.addListener(nonAdminToken, listener2)
                            .thenApply { adminToken }
                }.thenCompose { adminToken ->
                    messageFactory.create(MediaType.STICKER, "Smiley".toByteArray())
                            .thenApply { Pair(adminToken, it) }
                }.join()

        assertEquals(0, statistics.pendingMessages().join())
        assertEquals(0, statistics.channelMessages().join())
        app.channelSend(adminToken, "#TakeCare", message).join()
        assertEquals(0, statistics.pendingMessages().join())
        assertEquals(1, statistics.channelMessages().join())

        verify {
            listener1(match { it == "#TakeCare@Admin" },
                    match { it.contents contentEquals "Smiley".toByteArray() })
        }

        verify {
            listener2(match { it == "#TakeCare@Admin" },
                    match { it.contents contentEquals "Smiley".toByteArray() })
        }
    }

    @Test
    internal fun `user listeners are called when a broadcast message is sent`() {
        val listener1 = mockk<ListenerCallback>()
        every { listener1(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val listener2 = mockk<ListenerCallback>()
        every { listener2(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val listener3 = mockk<ListenerCallback>()
        every { listener3(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val (adminToken, message) = app.login("Admin", "a very strong password")
                .thenCompose { adminToken ->
                    app.addListener(adminToken, listener1)
                            .thenApply { adminToken }
                }.thenCompose { adminToken ->
                    app.login("Sahar", "a very weak password")
                            .thenApply { Pair(adminToken, it) }
                }.thenCompose { (adminToken, nonAdminToken) ->
                    app.addListener(nonAdminToken, listener2)
                            .thenApply { adminToken }
                }.thenCompose { adminToken ->
                    app.login("Yuval", "pizza")
                            .thenApply { Pair(adminToken, it) }
                }.thenCompose { (adminToken, nonAdminToken) ->
                    app.addListener(nonAdminToken, listener3)
                            .thenApply { adminToken }
                }.thenCompose { adminToken ->
                    messageFactory.create(MediaType.STICKER, "Smiley".toByteArray())
                            .thenApply { Pair(adminToken, it) }
                }.join()

        assertEquals(0, statistics.pendingMessages().join())
        app.broadcast(adminToken, message).join()
        assertEquals(0, statistics.pendingMessages().join())

        verify {
            listener1(match { it == "BROADCAST" },
                    match { it.contents contentEquals "Smiley".toByteArray() })
        }

        verify {
            listener2(match { it == "BROADCAST" },
                    match { it.contents contentEquals "Smiley".toByteArray() })
        }

        verify {
            listener3(match { it == "BROADCAST" },
                    match { it.contents contentEquals "Smiley".toByteArray() })
        }
    }

    @Test
    internal fun `user listener is called when attached after a private message was sent`() {
        val listener = mockk<ListenerCallback>()
        every { listener(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))
        val (adminToken, nonAdminToken, message) = app.login("Admin", "a very strong password")
                .thenCompose { adminToken ->
                    app.login("Sahar", "a very weak password")
                            .thenApply { Pair(adminToken, it) }
                }.thenCompose { (adminToken, nonAdminToken) ->
                    messageFactory.create(MediaType.STICKER, "Smiley".toByteArray())
                            .thenApply { Triple(adminToken, nonAdminToken, it) }
                }.join()

        assertEquals(0, statistics.pendingMessages().join())
        app.privateSend(adminToken, "Sahar", message).join()
        assertEquals(1, statistics.pendingMessages().join())

        app.addListener(nonAdminToken, listener).join()

        assertEquals(0, statistics.pendingMessages().join())

        verify {
            listener(match { it == "@Admin" },
                    match { it.contents contentEquals "Smiley".toByteArray() })
        }
    }

    @Test
    internal fun `user listeners are called when attached after a channel message was sent`() {
        val listener1 = mockk<ListenerCallback>()
        every { listener1(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val listener2 = mockk<ListenerCallback>()
        every { listener2(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val adminToken = app.login("admin", "123456").join()
        val otherToken1 = app.login("Sahar", "a very strong password").join()
        val otherToken2 = app.login("Yuval", "a very weak password").join()
        val message = app.channelJoin(adminToken, "#TakeCare")
                .thenCompose {
                    app.channelJoin(otherToken1, "#TakeCare")
                }.thenCompose {
                    app.channelJoin(otherToken2, "#TakeCare")
                }.thenCompose {
                    messageFactory.create(MediaType.TEXT, "take care guys ;)".toByteArray())
                }.join()

        app.channelSend(adminToken, "#TakeCare", message).join()

        assertEquals(1, statistics.channelMessages().join())

        app.addListener(otherToken1, listener1).thenCompose {
            app.addListener(otherToken2, listener2)
        }.join()

        verify {
            listener1(match { it == "#TakeCare@admin" },
                    match { it.contents contentEquals "take care guys ;)".toByteArray() })
        }

        verify {
            listener2(match { it == "#TakeCare@admin" },
                    match { it.contents contentEquals "take care guys ;)".toByteArray() })
        }
    }

    @Test
    internal fun `user listener is called when attached after a broadcast message was sent`() {
        val listener1 = mockk<ListenerCallback>()
        every { listener1(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val listener2 = mockk<ListenerCallback>()
        every { listener2(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val listener3 = mockk<ListenerCallback>()
        every { listener3(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val adminToken = app.login("admin", "123456").join()
        val otherToken1 = app.login("Sahar", "a very strong password").join()
        val otherToken2 = app.login("Yuval", "a very weak password").join()
        val message = messageFactory.create(MediaType.TEXT, "take care guys ;)".toByteArray()).join()

        app.broadcast(adminToken, message).join()

        assertEquals(1, statistics.pendingMessages().join())

        app.addListener(otherToken1, listener1).thenCompose {
            app.addListener(otherToken2, listener2)
        }.thenCompose {
            app.addListener(adminToken, listener3)
        }.join()

        verify {
            listener1(match { it == "BROADCAST" },
                    match { it.contents contentEquals "take care guys ;)".toByteArray() })
        }

        verify {
            listener2(match { it == "BROADCAST" },
                    match { it.contents contentEquals "take care guys ;)".toByteArray() })
        }
    }

    @Test
    internal fun `broadcast message is pending until all users have received it`() {
        val listener1 = mockk<ListenerCallback>()
        every { listener1(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val listener2 = mockk<ListenerCallback>()
        every { listener2(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val listener3 = mockk<ListenerCallback>()
        every { listener3(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val adminToken = app.login("admin", "123456").join()
        val otherToken1 = app.login("Sahar", "a very strong password").join()
        val otherToken2 = app.login("Yuval", "a very weak password").join()
        val message = messageFactory.create(MediaType.TEXT, "take care guys ;)".toByteArray()).join()

        app.broadcast(adminToken, message).join()
        assertEquals(1, statistics.pendingMessages().join())

        app.addListener(otherToken1, listener1).join()
        verify {
            listener1(match { it == "BROADCAST" },
                    match { it.contents contentEquals "take care guys ;)".toByteArray() })
        }
        assertEquals(1, statistics.pendingMessages().join())

        app.addListener(otherToken2, listener2).join()
        verify {
            listener1(match { it == "BROADCAST" },
                    match { it.contents contentEquals "take care guys ;)".toByteArray() })
        }
        assertEquals(1, statistics.pendingMessages().join())

        // admin must listen to this message too!!
        app.addListener(adminToken, listener3).join()
        verify {
            listener1(match { it == "BROADCAST" },
                    match { it.contents contentEquals "take care guys ;)".toByteArray() })
        }

        assertEquals(0, statistics.pendingMessages().join())
    }

    @Test
    internal fun `channel message is never removed`() {
        val listener = mockk<ListenerCallback>()
        every { listener(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        assertEquals(0, statistics.channelMessages().join())
        app.login("admin", "a very strong password")
                .thenCompose { adminToken ->
                    app.addListener(adminToken, listener)
                            .thenCompose {
                                app.channelJoin(adminToken, "#TakeCare")
                                        .thenCompose {
                                            messageFactory.create(MediaType.TEXT, "I wont die!".toByteArray())
                                                    .thenCompose { message ->
                                                        app.channelSend(adminToken, "#TakeCare", message)
                                                    }
                                        }
                            }
                }.join()

        assertEquals(1, statistics.channelMessages().join())
    }


    @Test
    internal fun `trying to send a private message to a user that does not exist should throw NoSuchEntityException`() {
        assertThrows<NoSuchEntityException> {
            app.login("admin", "a very strong password")
                    .thenCompose { adminToken ->
                        messageFactory.create(MediaType.TEXT, "Bad Message".toByteArray())
                                .thenCompose { message ->
                                    app.privateSend(adminToken, "Sahar", message)
                                }

                    }.joinException()
        }
    }

    @Test
    internal fun `trying to send a message to a channel that does not exist should throw NoSuchEntityException`() {
        assertThrows<NoSuchEntityException> {
            app.login("admin", "a very strong password")
                    .thenCompose { adminToken ->
                        messageFactory.create(MediaType.TEXT, "Bad Message".toByteArray())
                                .thenCompose { message ->
                                    app.channelSend(adminToken, "#TakeCare", message)
                                }

                    }.joinException()
        }
    }

    @Test
    internal fun `trying to send a message to a channel that the user is not a member of should throw UserNotAuthorizedException`() {
        assertThrows<UserNotAuthorizedException> {
            app.login("admin", "a very strong password")
                    .thenCompose { adminToken ->
                        app.channelJoin(adminToken, "#TakeCare")
                                .thenCompose {
                                    app.login("Sahar", "mc not admin")
                                            .thenCompose { nonAdminToken ->
                                                messageFactory.create(MediaType.TEXT, "Bad Message".toByteArray())
                                                        .thenCompose { message ->
                                                            app.channelSend(nonAdminToken, "#TakeCare", message)
                                                        }
                                            }
                                }
                    }.joinException()
        }
    }

    @Test
    internal fun `trying to send a broadcast message without admin privileges should throw UserNotAuthorizedException`() {
        assertThrows<UserNotAuthorizedException> {
            app.login("admin", "a very strong password")
                    .thenCompose {
                        app.login("Sahar", "a very weak password")
                                .thenCompose { nonAdminToken ->
                                    messageFactory.create(MediaType.TEXT, "Bad Message".toByteArray())
                                            .thenCompose { message ->
                                                app.broadcast(nonAdminToken, message)
                                            }
                                }
                    }.joinException()
        }
    }

    @Test
    internal fun `channel member can fetch a channel message`() {
        val (adminToken, nonAdminToken) = app.login("admin", "a very strong password")
                .thenCompose { adminToken ->
                    app.login("Sahar", "a very weak password")
                            .thenApply { Pair(adminToken, it) }
                }.join()
        val messageId = app.channelJoin(adminToken, "#TakeCare").thenCompose {
            app.channelJoin(nonAdminToken, "#TakeCare")
                    .thenCompose {
                        messageFactory.create(MediaType.LOCATION, "Netanya".toByteArray())
                                .thenCompose { message ->
                                    app.channelSend(adminToken, "#TakeCare", message)
                                            .thenApply { message.id }
                                }
                    }
        }.join()

        val (sender, message) = app.fetchMessage(nonAdminToken, messageId).join()
        assertEquals("#TakeCare@admin", sender)
        assertEquals("Netanya", String(message.contents))
        assertEquals(MediaType.LOCATION, message.media)
    }

    @Test
    internal fun `user can fetch a channel message that predates them joining the channel`() {
        val (adminToken, nonAdminToken) = app.login("admin", "a very strong password")
                .thenCompose { adminToken ->
                    app.login("Sahar", "a very weak password")
                            .thenApply { Pair(adminToken, it) }
                }.join()

        val messageId = app.channelJoin(adminToken, "#TakeCare")
                .thenCompose {
                    messageFactory.create(MediaType.TEXT, "ping -t 8.8.8.8".toByteArray())
                            .thenCompose { message ->
                                app.channelSend(adminToken, "#TakeCare", message)
                                        .thenApply { message.id }
                            }
                }.join()

        app.channelJoin(nonAdminToken, "#TakeCare").join()

        val (sender, message) = app.fetchMessage(nonAdminToken, messageId).join()
        assertEquals("#TakeCare@admin", sender)
        assertEquals("ping -t 8.8.8.8", String(message.contents))
    }

    @Test
    internal fun `trying to fetch a channel message that does not exist should throw NoSuchEntityException`() {
        val adminToken = app.login("admin", "a very strong password").join()
        app.channelJoin(adminToken, "#TakeCare").join()

        assertThrows<NoSuchEntityException> {
            app.fetchMessage(adminToken, 1).joinException()
        }
    }

    @Test
    internal fun `trying to fetch a channel message that is actually a different type of message should throw NoSuchEntityException`() {
        val adminToken = app.login("admin", "a very strong password").join()
        val messageId = app.channelJoin(adminToken, "#TakeCare").thenCompose {
            messageFactory.create(MediaType.TEXT, "ATTENTION ALL EPIC GAMERS!".toByteArray())
                    .thenCompose { message ->
                        app.broadcast(adminToken, message)
                                .thenApply { message.id }
                    }
        }.join()

        assertThrows<NoSuchEntityException> {
            app.fetchMessage(adminToken, messageId).joinException()
        }
    }

    @Test
    internal fun `trying to fetch a channel message from a channel that the user is not a member of should throw UserNotAuthorizedException`() {
        val (adminToken, nonAdminToken) = app.login("admin", "a very strong password")
                .thenCompose { adminToken ->
                    app.login("Sahar", "a very weak password")
                            .thenApply { Pair(adminToken, it) }
                }.join()

        val messageId = app.channelJoin(adminToken, "#TakeCare").thenCompose {
            messageFactory.create(MediaType.TEXT, "ATTENTION ALL EPIC TakeCares!".toByteArray())
                    .thenCompose { message ->
                        app.channelSend(adminToken, "#TakeCare", message)
                                .thenApply { message.id }
                    }
        }.join()

        assertThrows<UserNotAuthorizedException> {
            app.fetchMessage(nonAdminToken, messageId).joinException()
        }
    }

    @Test
    internal fun `user listener is not invoked for messages in channels that the user is not a member of`() {
        val listener = mockk<ListenerCallback>()
        every { listener(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val (adminToken, nonAdminToken) = app.login("admin", "a very strong password")
                .thenCompose { adminToken ->
                    app.login("sahar", "a very weak password")
                            .thenApply { Pair(adminToken, it) }
                }.join()
        app.channelJoin(adminToken, "#TakeCare").join()
        messageFactory.create(MediaType.TEXT, "Hello Sus".toByteArray())
                .thenCompose { app.channelSend(adminToken, "#TakeCare", it) }.join()

        assertEquals(1, statistics.channelMessages().join())
        app.addListener(nonAdminToken, listener).join()

        verify { listener wasNot called }
    }

    @Test
    internal fun `multiple listeners for the same user are properly invoked`() {
        val listener1 = mockk<ListenerCallback>()
        every { listener1(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val listener2 = mockk<ListenerCallback>()
        every { listener2(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val (adminToken, message) = app.login("Admin", "a very strong password")
                .thenCompose { adminToken ->
                    app.login("Sahar", "a very weak password")
                            .thenApply { Pair(adminToken, it) }
                }.thenCompose { (adminToken, nonAdminToken) ->
                    app.addListener(nonAdminToken, listener1)
                            .thenApply { Pair(adminToken, nonAdminToken) }
                }.thenCompose { (adminToken, nonAdminToken) ->
                    app.addListener(nonAdminToken, listener2)
                            .thenApply { adminToken }
                }.thenCompose { adminToken ->
                    messageFactory.create(MediaType.STICKER, "Smiley".toByteArray())
                            .thenApply { Pair(adminToken, it) }
                }.join()

        verify {
            listener1 wasNot called
            listener2 wasNot called
        }

        assertEquals(0, statistics.pendingMessages().join())
        app.privateSend(adminToken, "Sahar", message).join()
        assertEquals(0, statistics.pendingMessages().join())

        verify {
            listener1(match { it == "@Admin" },
                    match { it.contents contentEquals "Smiley".toByteArray() })
        }

        verify {
            listener2(match { it == "@Admin" },
                    match { it.contents contentEquals "Smiley".toByteArray() })
        }
    }

    @Test
    internal fun `if a listener is removed and then a message is sent - the callback should not be invoked`() {
        val listener = mockk<ListenerCallback>()
        every { listener(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val (adminToken, nonAdminToken, message) = app.login("Admin", "a very strong password")
                .thenCompose { adminToken ->
                    app.login("Sahar", "a very weak password")
                            .thenApply { Pair(adminToken, it) }
                }.thenCompose { (adminToken, nonAdminToken) ->
                    app.addListener(nonAdminToken, listener)
                            .thenApply { Pair(adminToken, nonAdminToken) }
                }.thenCompose { (adminToken, nonAdminToken) ->
                    messageFactory.create(MediaType.STICKER, "Smiley".toByteArray())
                            .thenApply { Triple(adminToken, nonAdminToken, it) }
                }.join()

        verify { listener wasNot called }
        app.removeListener(nonAdminToken, listener)
        assertEquals(0, statistics.pendingMessages().join())
        app.privateSend(adminToken, "Sahar", message).join()

        assertEquals(1, statistics.pendingMessages().join())
    }

    @Test
    internal fun `a listener is only invoked once for the same message - even if the message is still pending`() {
        val listener1 = mockk<ListenerCallback>()
        every { listener1(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val adminToken = app.login("Admin", "a very strong password")
                .thenCompose { adminToken ->
                    app.login("Alon", "*******")
                            .thenApply { adminToken }
                }.thenCompose { adminToken ->
                    app.login("Sahar", "a very weak password")
                            .thenApply { adminToken }
                }.thenCompose { adminToken ->
                    app.addListener(adminToken, listener1)
                            .thenApply { adminToken }
                }.thenCompose { adminToken ->
                    messageFactory.create(MediaType.STICKER, "Smiley".toByteArray())
                            .thenApply { Pair(adminToken, it) }
                }.thenCompose { (adminToken, message) ->
                    app.broadcast(adminToken, message)
                            .thenApply { adminToken }
                }.join()

        verify(exactly = 1) { listener1(any(), any()) }

        val listener2 = mockk<ListenerCallback>()
        every { listener2(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        app.addListener(adminToken, listener2).join()

        verify(exactly = 1) { listener1(any(), any()) }
        verify { listener2 wasNot called }

        messageFactory.create(MediaType.LOCATION, "Africa".toByteArray()).thenCompose { anotherMessage ->
            app.broadcast(adminToken, anotherMessage)
        }.join()

        verify(exactly = 2) { listener1(any(), any()) }
        verify(exactly = 1) { listener2(any(), any()) }
    }

    @Test
    internal fun `receive time of a message is updated when a user reads it`() {
        val listener = mockk<ListenerCallback>()
        every { listener(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        app.login("Admin", "a very strong password")
                .thenCompose { adminToken ->
                    app.login("Sahar", "a very weak password")
                            .thenApply { Pair(adminToken, it) }
                }.thenCompose { (adminToken, nonAdminToken) ->
                    app.addListener(nonAdminToken, listener)
                            .thenApply { adminToken }
                }.thenCompose { adminToken ->
                    messageFactory.create(MediaType.STICKER, "Smiley".toByteArray())
                            .thenApply { Pair(adminToken, it) }
                }.thenCompose { (adminToken, message) ->
                    app.privateSend(adminToken, "Sahar", message)
                }.join()

        verify {
            listener(match { it == "@Admin" },
                    match { it.received != null })
        }
    }

    @Test
    internal fun `two users who fetched the same message at different times will have different receive times`() {
        val listener1 = mockk<ListenerCallback>()
        every { listener1(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val listener2 = mockk<ListenerCallback>()
        every { listener2(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val beforeFirstListener = LocalDateTime.now()

        val adminToken = app.login("Admin", "a very strong password")
                .thenCompose { adminToken ->
                    app.login("Sahar", "a very weak password")
                            .thenApply { Pair(adminToken, it) }
                }.thenCompose { (adminToken, nonAdminToken) ->
                    app.addListener(nonAdminToken, listener1)
                            .thenApply { adminToken }
                }.thenCompose { adminToken ->
                    messageFactory.create(MediaType.STICKER, "Smiley".toByteArray())
                            .thenApply { Pair(adminToken, it) }
                }.thenCompose { (adminToken, message) ->
                    app.broadcast(adminToken, message)
                            .thenApply { adminToken }
                }.join()

        // sleep for a short while and then attach a listener for another user
        Thread.sleep(200)

        val afterFirstListener = LocalDateTime.now()

        app.addListener(adminToken, listener2).join()

        verify {
            listener1(match { it == "BROADCAST" },
                    match { it.received!! > beforeFirstListener && it.received!! < afterFirstListener })
        }

        verify {
            listener2(match { it == "BROADCAST" },
                    match { it.received!! > afterFirstListener })
        }
    }
}