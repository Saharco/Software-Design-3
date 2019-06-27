package fakes.managers.database

import fakes.CourseAppFake
import fakes.library.database.Database
import fakes.library.database.DocumentReference
import il.ac.technion.cs.softwaredesign.exceptions.InvalidTokenException
import il.ac.technion.cs.softwaredesign.exceptions.NoSuchEntityException
import il.ac.technion.cs.softwaredesign.exceptions.UserAlreadyLoggedInException
import il.ac.technion.cs.softwaredesign.exceptions.UserNotAuthorizedException
import fakes.library.utils.DatabaseMapper
import updateTree
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

/**
 * Manages users in a database: this class wraps authentication functionality.
 * Provides common database operations regarding users and login session tokens
 * @see CourseAppFake
 * @see Database
 *
 * @param dbMapper: mapper object that contains the app's open databases
 *
 */
class AuthenticationManager(private val dbMapper: DatabaseMapper) {

    private val dbName = "course_app_database"

    private val usersRoot = dbMapper.getDatabase(dbName)
            .collection("all_users")
    private val tokensRoot = dbMapper.getDatabase(dbName)
            .collection("tokens")
    private val metadataRoot = dbMapper.getDatabase(dbName)
            .collection("users_metadata")

    private val channelsRoot = dbMapper.getDatabase(dbName)
            .collection("all_channels")

    private val usersByChannelsStorage = dbMapper.getStorage("users_by_channels")
    private val channelsByActiveUsersStorage = dbMapper.getStorage("channels_by_active_users")

    /**
     * Log in a user identified by [username] and [password], returning an authentication token that can be used in
     * future calls. If this username did not previously log in to the system, it will be automatically registered with
     * the provided password. Otherwise, the password will be checked against the previously provided password.
     *
     * If this is the first user to be registered, it will be made an administrator.
     *
     * This is a *create* command.
     *
     * The procedure is:
     *  verifies that the login is valid,
     *  generates unique token,
     *  updates counters: total user, online users, creation counter,
     *  grants administrator privileges if this is the first user of the system,
     *  writes user information
     *
     * @throws NoSuchEntityException If the password does not match the username.
     * @throws UserAlreadyLoggedInException If the user is already logged-in.
     * @return An authentication token to be used in other calls.
     */
    fun performLogin(username: String, password: String): CompletableFuture<String> {
        val userDocument = usersRoot.document(username)
        return userDocument.read("password").thenApply { storedPassword ->

            if (storedPassword != null && storedPassword != password)
                throw NoSuchEntityException("incorrect password")
            else
                storedPassword
        }.thenCompose { storedPassword ->

            userDocument.read("token").thenApply { storedToken ->
                if (storedToken != null)
                    throw UserAlreadyLoggedInException("please logout before logging in again")
            }.thenApply {
                val token = generateToken(username)
                userDocument.set(Pair("token", token))
                Pair(storedPassword, token)
            }.thenCompose { pair ->
                updateLoginData(userDocument, pair.first, password, username)
                        .thenApply { pair.second }
            }.thenCompose { token ->
                tokensRoot.document(token)
                        .set(Pair("username", username))
                        .write()
                        .thenApply { token }
            }.thenApply { token ->
                token
            }
        }
    }

    /**
     * Log out the user with this authentication [token]. The [token] will be invalidated and can not be used for future
     * calls.
     *
     * This is a *delete* command.
     *
     * The procedure is:
     *  verifies that the logout is valid,
     *  decreases online users counter,
     *  invalidates user token,
     *  decreases online users counter for every channel that the user is a member of
     *
     * @throws InvalidTokenException If the auth [token] is invalid.
     */
    fun performLogout(token: String): CompletableFuture<Unit> {
        val tokenDocument = tokensRoot.document(token)
        return tokenDocument.read("username")
                .thenApply { username ->
                    username ?: throw InvalidTokenException("token does not match any active user")
                }.thenCompose { username ->
                    tokenDocument.delete()
                            .thenApply { username }
                }.thenCompose { username ->
                    updateLogoutData(usersRoot.document(username))
                }
    }

    /**
     * Indicate the status of [username] in the application.
     *
     * A valid authentication [token] (for *any* user) is required to perform this operation.
     *
     * This is a *read* command.
     *
     * @throws InvalidTokenException If the auth [token] is invalid.
     * @return True if [username] exists and is logged in, false if it exists and is not logged in, and null if it does
     * not exist.
     */
    fun isUserLoggedIn(token: String, username: String): CompletableFuture<Boolean?> {
        return tokensRoot.document(token)
                .exists()
                .thenApply { exists ->
                    if (!exists)
                        throw InvalidTokenException("token does not match any active user")
                }.thenCompose {
                    usersRoot.document(username)
                            .exists()
                }.thenCompose { exists ->
                    if (!exists)
                        CompletableFuture.completedFuture(null as Boolean?)
                    else {
                        usersRoot.document(username)
                                .read("token")
                                .thenApply { otherToken ->
                                    otherToken != null
                                }
                    }
                }
    }

    /**
     * Make another user, identified by [username], an administrator. Only users who are administrators may perform this
     * operation.
     *
     * This is an *update* command.
     *
     * @throws InvalidTokenException If the auth [token] is invalid.
     * @throws UserNotAuthorizedException If the auth [token] does not belong to a user who is an administrator.
     * @throws NoSuchEntityException If [username] does not exist.
     */
    fun makeAdministrator(token: String, username: String): CompletableFuture<Unit> {
        return tokensRoot.document(token)
                .read("username")
                .thenApply { tokenUsername ->
                    tokenUsername ?: throw InvalidTokenException("token does not match any active user")
                }.thenCompose { tokenUsername ->
                    usersRoot.document(tokenUsername)
                            .read("isAdmin")
                }.thenApply { isAdmin ->
                    isAdmin ?: throw UserNotAuthorizedException("no admin permission")
                }.thenCompose {
                    usersRoot.document(username)
                            .exists()
                }.thenApply { exists ->
                    if (!exists)
                        throw NoSuchEntityException("given user does not exist")
                }.thenCompose {
                    usersRoot.document(username)
                            .set(Pair("isAdmin", "true"))
                            .update()
                }
    }

    /**
     * Count the total number of users, both logged-in and logged-out, in the system.
     *
     * @return The total number of users.
     */
    fun getTotalUsers(): CompletableFuture<Long> {
        return metadataRoot.document("users_data")
                .read("users_count")
                .thenApply { usersCount ->
                    usersCount?.toLong() ?: 0
                }
    }

    /**
     * Count the number of logged-in users in the system.
     *
     * @return The number of logged-in users.
     */
    fun getLoggedInUsers(): CompletableFuture<Long> {
        return metadataRoot.document("users_data")
                .read("online_users_count")
                .thenApply { onlineUsersCount ->
                    onlineUsersCount?.toLong() ?: 0
                }
    }

    /**
     * Generates a unique token from a given username
     */
    private fun generateToken(username: String): String {
        return "$username+${LocalDateTime.now()}"
    }

    /**
     * Updates the following information:
     *  - number of logged in users in the system,
     *  - number of users in the system,
     *  - number of logged in users in each channel that the user is a member of,
     *  - administrator privilege is given to the user if they're the first user in the system,
     *  - password is written for the user if it's their first time logging in,
     *  - creation time is written for the user if it's their first time logging in
     *
     *  @param userDocument: fetched document of the user who's logging in
     *  @param storedPassword: the password currently stored in the user's document
     *  @param enteredPassword: the password entered by the user
     *
     */
    private fun updateLoginData(userDocument: DocumentReference, storedPassword: String?,
                                enteredPassword: String, username: String): CompletableFuture<Unit> {
        var future = CompletableFuture.completedFuture(Unit)

        if (storedPassword == null) {
            future = future.thenCompose {
                metadataRoot.document("users_data")
                        .read("creation_counter")
            }.thenApply { oldCounter ->
                oldCounter?.toInt()?.plus(1) ?: 1
            }.thenCompose { newCounter ->
                metadataRoot.document("users_data")
                        .set(Pair("creation_counter", newCounter.toString()))
                        .update()
                        .thenApply { newCounter }
            }.thenCompose { newCounter ->
                userDocument.set(Pair("password", enteredPassword))
                        .set(Pair("creation_time", LocalDateTime.now().toString()))
                        .set(Pair("creation_counter", newCounter.toString()))
                metadataRoot.document("users_data")
                        .read("users_count")
                        .thenApply { oldUsersCount -> Pair(newCounter, oldUsersCount?.toInt()?.plus(1) ?: 1) }
            }.thenCompose { pair ->
                if (pair.second == 1)
                    userDocument.set(Pair("isAdmin", "true"))
                metadataRoot.document("users_data")
                        .set(Pair("users_count", pair.second.toString()))
                        .update()
                        .thenApply { pair.first }
            }.thenApply { newCounter ->
                updateTree(usersByChannelsStorage, username, 0, 0, newCounter)
            }
        }

        val usersCountDocument = metadataRoot.document("users_data")

        return future.thenCompose {
            usersCountDocument.read("online_users_count")
        }.thenApply { oldOnlineUsersCount ->
            oldOnlineUsersCount?.toInt()?.plus(1) ?: 1
        }.thenCompose { newOnlineUsersCount ->
            usersCountDocument.set(Pair("online_users_count", newOnlineUsersCount.toString()))
                    .update()
        }.thenCompose {
            userDocument.readList("channels")
        }.thenApply { channels ->
            channels ?: listOf()
        }.thenCompose { channels ->
            updateUserChannels(channels, updateCount = 1)
        }.thenCompose {
            userDocument.update()
        }
    }

    /**
     * Updates the amount of logged in users for all channels in [channels] by [updateCount]
     */
    private fun updateUserChannels(channels: List<String>, updateCount: Int, index: Int = 0):
            CompletableFuture<Unit> {
        if (channels.size <= index)
            return CompletableFuture.completedFuture(Unit)
        val channel = channels[index]

        return channelsRoot.document(channel)
                .read("online_users_count")
                .thenApply { oldOnlineUsersCount ->
                    oldOnlineUsersCount?.toInt()?.plus(updateCount) ?: 0
                }.thenCompose { newOnlineUsersCount ->
                    channelsRoot.document(channel)
                            .set(Pair("online_users_count", newOnlineUsersCount.toString()))
                            .update()
                            .thenApply { newOnlineUsersCount }
                }.thenCompose { newOnlineUsersCount ->
                    channelsRoot.document(channel).read("creation_counter")
                            .thenApply { creationCounter ->
                                Pair(newOnlineUsersCount, creationCounter!!.toInt())
                            }
                }.thenApply { pair ->
                    updateTree(channelsByActiveUsersStorage, channel,
                            pair.first, pair.first - updateCount, pair.second)
                }.thenCompose {
                    updateUserChannels(channels, updateCount, index + 1)
                }
    }

    /**
     * Updates the following information:
     *  - number of logged in users in the system,
     *  - number of users in the system,
     *  - number of logged in users in each channel that the user is a member of,
     *  - invalidating user token
     */
    private fun updateLogoutData(userDocument: DocumentReference): CompletableFuture<Unit> {
        val usersCountDocument = metadataRoot.document("users_data")
        return usersCountDocument.read("online_users_count")
                .thenApply { oldOnlineUsers ->
                    oldOnlineUsers?.toInt()?.minus(1) ?: 0
                }.thenCompose { newOnlineUsers ->
                    usersCountDocument.set(Pair("online_users_count", newOnlineUsers.toString()))
                            .update()
                }.thenCompose {
                    userDocument.readList("channels")
                }.thenApply { channels ->
                    channels ?: listOf()
                }.thenCompose { channels ->
                    updateUserChannels(channels, updateCount = -1)
                }.thenCompose {
                    userDocument.delete(listOf("token"))
                }
    }
}