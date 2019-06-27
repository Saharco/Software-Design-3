package fakes.managers.database

import fakes.CourseAppFake
import fakes.library.database.Database
import il.ac.technion.cs.softwaredesign.exceptions.InvalidTokenException
import il.ac.technion.cs.softwaredesign.exceptions.NameFormatException
import il.ac.technion.cs.softwaredesign.exceptions.NoSuchEntityException
import il.ac.technion.cs.softwaredesign.exceptions.UserNotAuthorizedException
import fakes.library.utils.DatabaseMapper
import treeTopK
import updateTree
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture


/**
 * Manages channels in the app: this class wraps channels functionality
 * @see CourseAppFake
 * @see Database
 *
 * @param dbMapper: mapper object that contains the app's open databases
 *
 */
class ChannelsManager(private val dbMapper: DatabaseMapper) {

    private val dbName = "course_app_database"

    private val usersRoot = dbMapper.getDatabase(dbName)
            .collection("all_users")
    private val tokensRoot = dbMapper.getDatabase(dbName)
            .collection("tokens")

    private val channelsRoot = dbMapper.getDatabase(dbName)
            .collection("all_channels")
    private val metadataDocument = dbMapper.getDatabase(dbName)
            .collection("channels_metadata").document("channels_data")

    private val channelsByUsersStorage = dbMapper.getStorage("channels_by_users")
    private val channelsByActiveUsersStorage = dbMapper.getStorage("channels_by_active_users")
    private val usersByChannelsStorage = dbMapper.getStorage("users_by_channels")

    /**
     * The user identified by [token] will join [channel]. If the channel does not exist, it is created only if [token]
     * identifies a user who is an administrator.
     *
     * Valid names for channels start with `#`, then have any number of English alphanumeric characters, underscores
     * (`_`) and hashes (`#`).
     *
     * This is a *create* command.
     *
     * The procedure is:
     *  verifies that the user isn't already a member of the channel,
     *  checks for admin privilege if channel is new,
     *  adds channel to user's channels list,
     *  creates channel if its new,
     *  updates total users of channel,
     *  updates online users of channel,
     *  updates trees
     *
     * @throws InvalidTokenException If the auth [token] is invalid.
     * @throws NameFormatException If [channel] is not a valid name for a channel.
     * @throws UserNotAuthorizedException If [channel] does not exist and [token] belongs to a user who is not an
     * administrator.
     */
    fun channelJoin(token: String, channel: String): CompletableFuture<Unit> {
        return tokenToUser(token)
                .thenApply { tokenUsername ->
                    if (!validChannelName(channel))
                        throw NameFormatException("invalid channel name: $channel")
                    else
                        tokenUsername
                }.thenCompose { tokenUsername ->
                    usersRoot.document(tokenUsername)
                            .readList("channels")
                            .thenApply { userChannels ->
                                Pair(tokenUsername, userChannels?.toMutableList() ?: mutableListOf())
                            }
                }.thenCompose { pair ->
                    if (!pair.second.contains(channel)) {
                        createNewChannelIfNeeded(channel, pair.first)
                                .thenCompose {
                                    addChannelToUserList(pair.first, pair.second, channel)
                                }.thenCompose { userChannelsCount ->
                                    updateChannelUsersCount(channel)
                                            .thenCompose { channelTotalUsers ->
                                                updateChannelOnlineUsersCountIfOnline(channel, pair.first)
                                                        .thenCompose { channelOnlineUsers ->
                                                            increaseTrees(channel, pair.first, userChannelsCount,
                                                                    channelTotalUsers, channelOnlineUsers)
                                                        }
                                            }
                                }
                    } else {
                        CompletableFuture.completedFuture(Unit)
                    }
                }
    }

    /**
     * The user identified by [token] will exit [channel].
     *
     * If the last user leaves a channel, the channel will be destroyed and its name will be available for re-use. The
     * first user to join the channel becomes an operator.
     *
     * This is a *delete* command.
     *
     * The procedure is:
     *  verifies that the channel exists and user referenced by [token] is a member of it,
     *  removes channel from user's channels' list,
     *  removes operator from channel's operators list if the user is an operator for this channel,
     *  decreases users count of channel by 1,
     *  decreases online users count of channel by 1 if user is logged in,
     *  deletes channel if it is empty,
     *  updates query trees
     *
     * @throws InvalidTokenException If the auth [token] is invalid.
     * @throws NoSuchEntityException If [token] identifies a user who is not a member of [channel], or [channel] does
     * does exist.
     */
    fun channelPart(token: String, channel: String): CompletableFuture<Unit> {
        return tokenToUser(token)
                .thenCompose { tokenUsername ->
                    verifyChannelExists(channel).thenApply { tokenUsername }
                }.thenCompose { tokenUsername ->
                    isMemberOfChannel(tokenUsername, channel)
                            .thenApply { isMember ->
                                if (!isMember)
                                    throw NoSuchEntityException("user is not a member of the channel")
                            }.thenCompose {
                                expelChannelMember(tokenUsername, channel)
                            }
                }
    }

    /**
     * Make [username] an operator of this channel. Only existing operators of [channel] and administrators are allowed
     * to make other users operators.
     *
     * This is an *update* command.
     *
     *
     * @throws InvalidTokenException If the auth [token] is invalid.
     * @throws NoSuchEntityException If [channel] does not exist.
     * @throws UserNotAuthorizedException If the user identified by [token] is at least one of the following:
     * 1. Not an operator of [channel] or an administrator,
     * 2. An administrator who is not an operator of [channel] and [username] does not match [token],
     * 3. Not a member of [channel].
     * @throws NoSuchEntityException If [username] does not exist, or if [username] is not a member of [channel].
     */
    fun channelMakeOperator(token: String, channel: String, username: String): CompletableFuture<Unit> {
        return tokenToUser(token)
                .thenCompose { tokenUsername ->
                    verifyChannelExists(channel).thenApply { tokenUsername }
                }.thenCompose { tokenUsername ->
                    verifyOperatorPromoterPrivilege(channel, tokenUsername, username)
                }.thenCompose {
                    channelsRoot.document(channel)
                            .readList("operators")
                }.thenApply { operators ->
                    operators?.toMutableList() ?: mutableListOf()
                }.thenCompose { operators ->
                    operators.add(username)
                    channelsRoot.document(channel)
                            .set("operators", operators)
                            .update()
                }
    }

    /**
     * Remove the user [username] from [channel]. Only operators of [channel] may perform this operation.
     *
     * This is an *update* command.
     *
     * The procedure is:
     *  verifies that the channel exists and user referenced by [token] is an operator of it,
     *  removes channel from [username]'s channels' list,
     *  removes operator from channel's operators list if [username] is an operator for this channel,
     *  decreases users count of channel by 1,
     *  decreases online users count of channel by 1 if [username] is logged in,
     *  deletes channel if it is empty,
     *  updates query trees
     *
     * @throws InvalidTokenException If the auth [token] is invalid.
     * @throws NoSuchEntityException If [channel] does not exist.
     * @throws UserNotAuthorizedException If [token] is not an operator of this channel.
     * @throws NoSuchEntityException If [username] does not exist, or if [username] is not a member of [channel].
     */
    fun channelKick(token: String, channel: String, username: String): CompletableFuture<Unit> {
        return tokenToUser(token)
                .thenCompose { tokenUsername ->
                    verifyChannelExists(channel).thenApply { tokenUsername }
                }.thenCompose { tokenUsername ->
                    isOperator(tokenUsername, channel)
                            .thenApply { isOperator ->
                                if (!isOperator)
                                    throw UserNotAuthorizedException("must have operator privileges")
                            }.thenCompose {
                                isMemberOfChannel(username, channel)
                                        .thenApply { isMember ->
                                            if (!isMember)
                                                throw NoSuchEntityException(
                                                        "provided username is not a member of this channel")
                                        }.thenCompose {
                                            expelChannelMember(username, channel)
                                        }
                            }
                }
    }

    /**
     * Indicate [username]'s membership in [channel]. A user is still a member of a channel when logged off.
     *
     * This is a *read* command.
     *
     * @throws InvalidTokenException If the auth [token] is invalid.
     * @throws NoSuchEntityException If [channel] does not exist.
     * @throws UserNotAuthorizedException If [token] is not an administrator or member of this channel.
     * @return True if [username] exists and is a member of [channel], false if it exists and is not a member, and null
     * if it does not exist.
     */
    fun isUserInChannel(token: String, channel: String, username: String): CompletableFuture<Boolean?> {
        return verifyValidAndPrivilegedToQuery(token, channel)
                .thenCompose {
                    usersRoot.document(username)
                            .exists()
                }.thenCompose { exists ->
                    if (!exists)
                        CompletableFuture.completedFuture(null as Boolean?)
                    else
                        @Suppress("UNCHECKED_CAST")
                        isMemberOfChannel(username, channel) as CompletableFuture<Boolean?>
                }
    }

    /**
     * Gets the number of logged-in users in a given [channel].
     *
     * Administrators can query any channel, while regular users can only query channels that they are members of.
     *
     * This is a *read* command.
     *
     * @throws InvalidTokenException If the auth [token] is invalid.
     * @throws NoSuchEntityException If [channel] does not exist.
     * @throws UserNotAuthorizedException If [token] identifies a user who is not an administrator and is not a member
     * of [channel].
     * @returns Number of logged-in users in [channel].
     */
    fun numberOfActiveUsersInChannel(token: String, channel: String): CompletableFuture<Long> {
        return verifyValidAndPrivilegedToQuery(token, channel)
                .thenCompose {
                    channelsRoot.document(channel)
                            .read("online_users_count")
                            .thenApply { usersCount -> usersCount?.toLong() ?: 0 }
                }
    }

    /**
     * Gets the number of users in a given [channel].
     *
     * Administrators can query any channel, while regular users can only query channels that they are members of.
     *
     * This is a *read* command.
     *
     * @throws InvalidTokenException If the auth [token] is invalid.
     * @throws NoSuchEntityException If [channel] does not exist.
     * @throws UserNotAuthorizedException If [token] identifies a user who is not an administrator and is not a member
     * of [channel].
     * @return Number of users, both logged-in and logged-out, in [channel].
     */
    fun numberOfTotalUsersInChannel(token: String, channel: String): CompletableFuture<Long> {
        return verifyValidAndPrivilegedToQuery(token, channel)
                .thenCompose {
                    channelsRoot.document(channel)
                            .read("users_count")
                            .thenApply { usersCount -> usersCount?.toLong() ?: 0 }
                }
    }

    /**
     * Return a sorted list of the top [k] channels in the system by user count. The list will be sorted in descending
     * order, so the channel with the highest membership will be first, followed by the second, and so on.
     *
     * If two channels have the same number of users, they will be sorted in ascending appearance order, such that a
     * channel that was created earlier would appear first in the list.
     *
     * If there are less than [k] channels in the system, a shorter list will be returned.
     *
     * @return A sorted list of channels by user count.
     */
    fun topKChannelsByUsers(k: Int = 10): CompletableFuture<List<String>> {
        return CompletableFuture.completedFuture(treeTopK(channelsByUsersStorage, k))
    }

    /**
     * Return a sorted list of the top [k] channels in the system by logged-in user count. The list will be sorted in
     * descending order, so the channel with the highest active membership will be first, followed by the second, and so
     * on.
     *
     * If two channels have the same number of logged-in users, they will be sorted in ascending appearance order, such
     * that a channel that was created earlier would appear first in the list.
     *
     * If there are less than [k] channels in the system, a shorter list will be returned.
     *
     * @return A sorted list of channels by logged-in user count.
     */
    fun topKChannelsByActiveUsers(k: Int = 10): CompletableFuture<List<String>> {
        return CompletableFuture.completedFuture(treeTopK(channelsByActiveUsersStorage, k))
    }

    /**
     * Return a sorted list of the top [k] users in the system by channel membership count. The list will be sorted in
     * descending order, so the user who is a member of the most channels will be first, followed by the second, and so
     * on.
     *
     * If two users are members of the same number of channels, they will be sorted in ascending appearance order. such
     * that a user that was created earlier would appear first in the list.
     *
     * If there are less than [k] users in the system, a shorter list will be returned.
     *
     * @return A sorted list of users by channel count.
     */
    fun topKUsersByChannels(k: Int = 10): CompletableFuture<List<String>> {
        return CompletableFuture.completedFuture(treeTopK(usersByChannelsStorage, k))
    }

    /**
     * Adds a channel to a given user's channels field in the appropriate database document
     */
    private fun addChannelToUserList(username: String, userChannels: MutableList<String>, channel: String)
            : CompletableFuture<Int> {
        userChannels.add(channel)

        return usersRoot.document(username)
                .set("channels", userChannels)
                .set(Pair("channels_count", userChannels.size.toString()))
                .update()
                .thenApply { userChannels.size }
    }

    /**
     * Creates a new channel with a given operator for the channel
     */
    private fun createNewChannelIfNeeded(channel: String, operatorUsername: String): CompletableFuture<Boolean> {
        return channelsRoot.document(channel)
                .exists()
                .thenCompose { exists ->
                    if (exists)
                        CompletableFuture.completedFuture(false)
                    else {
                        isAdmin(operatorUsername)
                                .thenApply { isAdmin ->
                                    if (!isAdmin)
                                        throw UserNotAuthorizedException("only an administrator may create a new channel")
                                }.thenCompose {
                                    // creation of new channel
                                    metadataDocument.read("creation_counter")
                                            .thenApply { oldCreationCounter ->
                                                oldCreationCounter?.toInt()?.plus(1) ?: 1
                                            }.thenCompose { creationCounter ->
                                                metadataDocument.set(Pair("creation_counter", creationCounter.toString()))
                                                        .update()
                                                        .thenApply { creationCounter }
                                            }.thenCompose { creationCounter ->
                                                channelsRoot.document(channel)
                                                        .set("operators", listOf(operatorUsername))
                                                        .set(Pair("users_count", "0"))
                                                        .set(Pair("online_users_count", "0"))
                                                        .set(Pair("creation_time", LocalDateTime.now().toString()))
                                                        .set(Pair("creation_counter", creationCounter.toString()))
                                                        .write()
                                                        .thenApply { true }
                                            }
                                }
                    }
                }
    }

    /**
     * Verifies the token & channel for *querying operations*
     *
     * @throws InvalidTokenException If the auth [token] is invalid.
     * @throws NoSuchEntityException If [channel] does not exist.
     * @throws UserNotAuthorizedException If [token] identifies a user who is not an administrator and is not a member
     */
    private fun verifyValidAndPrivilegedToQuery(token: String, channel: String): CompletableFuture<Unit> {
        return verifyChannelExists(channel)
                .thenCompose { tokenToUser(token) }
                .thenCompose { tokenUsername ->
                    isAdmin(tokenUsername)
                            .thenCompose { isAdmin ->
                                if (isAdmin)
                                    CompletableFuture.completedFuture(Unit)
                                else
                                    isMemberOfChannel(tokenUsername, channel)
                                            .thenApply { isMember ->
                                                if (!isMember)
                                                    throw UserNotAuthorizedException(
                                                            "must be an admin or a member of the channel")
                                            }
                            }
                }
    }

    /**
     * Makes sure that a given channel exists
     *
     * @throws NoSuchEntityException if the channel does not exist
     */
    private fun verifyChannelExists(channel: String): CompletableFuture<Unit> {
        return channelsRoot.document(channel)
                .exists()
                .thenApply { exists ->
                    if (!exists)
                        throw NoSuchEntityException("given channel does not exist")
                }
    }

    /**
     * Verifies that a user can appoint another use to be an operator of a channel
     *
     * @param channel: name of the channel
     * @param tokenUsername: username of the promoter
     * @param username: username of the user who's to be promoted
     *
     * @throws UserNotAuthorizedException if [tokenUsername] does not have the privilege to promote [username] to be an operator of the channel
     */
    private fun verifyOperatorPromoterPrivilege(channel: String, tokenUsername: String, username: String):
            CompletableFuture<Unit> {
        return isAdmin(tokenUsername)
                .thenCompose { isAdmin ->
                    isOperator(tokenUsername, channel)
                            .thenApply { isOperator ->
                                if (!isAdmin && !isOperator)
                                    throw UserNotAuthorizedException("user is not an operator / administrator")

                                if (isAdmin && !isOperator && tokenUsername != username)
                                    throw UserNotAuthorizedException("administrator who's not an operator cannot " +
                                            "appoint other users to be operators")
                            }.thenCompose {
                                isMemberOfChannel(tokenUsername, channel)
                                        .thenApply { isMember ->
                                            if (!isMember)
                                                throw UserNotAuthorizedException("user is not a member in the channel")
                                        }
                            }
                }
    }

    /**
     * Kicks a user from a channel, along with all necessary updates:
     *
     *  removes channel from user's channels' list,
     *  removes operator from channel's operators list if the user is an operator for this channel,
     *  decreases users count of channel by 1,
     *  decreases online users count of channel by 1 if user is logged in,
     *  deletes channel if it is empty,
     *  updates query trees
     *
     */
    private fun expelChannelMember(username: String, channel: String): CompletableFuture<Unit> {
        return removeOperatorFromChannel(channel, username)
                .thenCompose { removeChannelFromUserList(username, channel) }
                .thenCompose { userChannelsCount ->
                    updateChannelUsersCount(channel, change = -1)
                            .thenApply { totalUsers -> Pair(userChannelsCount, totalUsers) }
                }.thenCompose { pair ->
                    updateChannelOnlineUsersCountIfOnline(channel, username, change = -1)
                            .thenApply { onlineUsers -> Triple(pair.first, pair.second, onlineUsers) }
                }.thenCompose { triple ->
                    if (triple.second == 0)
                        deleteChannel(channel)
                                .thenApply { triple }
                    else
                        CompletableFuture.completedFuture(triple)
                }.thenCompose { triple ->
                    decreaseTrees(channel, username, triple.first, triple.second, triple.third)
                }
    }
    /**
     * Decreases the total users count in a given channel as well as the amount of channels the new member is part of.
     * If the user is currently logged in: the amount of online users is decreased as well
     */

    private fun decreaseTrees(channel: String, username: String, userChannelsCount: Int, channelTotalUsers: Int,
                              channelOnlineUsers: Int): CompletableFuture<Unit> {
        return channelsRoot.document(channel)
                .read("creation_counter")
                .thenApply { channelCreationCounter ->
                    channelCreationCounter?.toInt() ?: 0
                }.thenCompose { channelCreationCounter ->
                    usersRoot.document(username)
                            .read("creation_counter")
                            .thenApply { userCreationCounter ->
                                Pair(channelCreationCounter, userCreationCounter?.toInt() ?: 0)
                            }
                }.thenCompose { pair ->
                    usersRoot.document(username)
                            .read("token")
                            .thenApply { token ->
                                if (token != null)
                                    Triple(pair.first, pair.second, channelOnlineUsers + 1)
                                else
                                    Triple(pair.first, pair.second, channelOnlineUsers)
                            }
                }.thenApply { triple ->
                    updateTree(channelsByUsersStorage, channel, channelTotalUsers, channelTotalUsers + 1,
                            triple.first, channelTotalUsers <= 0)
                    updateTree(channelsByActiveUsersStorage, channel, channelOnlineUsers, triple.third, triple.first)
                    updateTree(usersByChannelsStorage, username, userChannelsCount, userChannelsCount + 1,
                            triple.second)
                }
    }

    /**
     * Increases the total users & online users count in a given channel as well as the amount of channels the new member is part of.
     */
    private fun increaseTrees(channel: String, username: String, userChannelsCount: Int, channelTotalUsers: Int,
                              channelOnlineUsers: Int): CompletableFuture<Unit> {
        return channelsRoot.document(channel)
                .read("creation_counter")
                .thenApply { channelCreationCounter ->
                    channelCreationCounter?.toInt() ?: 0
                }.thenCompose { channelCreationCounter ->
                    usersRoot.document(username)
                            .read("creation_counter")
                            .thenApply { userCreationCounter ->
                                Pair(channelCreationCounter, userCreationCounter?.toInt() ?: 0)
                            }
                }.thenApply { pair ->
                    updateTree(channelsByUsersStorage, channel, channelTotalUsers, channelTotalUsers - 1,
                            pair.first)
                    updateTree(channelsByActiveUsersStorage, channel, channelOnlineUsers,
                            channelOnlineUsers - 1, pair.first)
                    updateTree(usersByChannelsStorage, username, userChannelsCount, userChannelsCount - 1,
                            pair.second)
                }
    }


    /**
     * Deletes a channel document from the database
     */
    private fun deleteChannel(channel: String): CompletableFuture<Unit> {
        return channelsRoot.document(channel)
                .delete()
    }

    /**
     * Removes a channel from a user's list of channels
     *
     * @return amount of channels the user is a member of after removal
     */
    private fun removeChannelFromUserList(username: String, channel: String): CompletableFuture<Int> {
        return usersRoot.document(username)
                .readList("channels")
                .thenApply { userChannels ->
                    userChannels?.toMutableList() ?: mutableListOf()
                }.thenCompose { userChannels ->
                    userChannels.remove(channel)
                    usersRoot.document(username)
                            .set("channels", userChannels)
                            .set(Pair("channels_count", userChannels.size.toString()))
                            .update()
                            .thenApply { userChannels.size }
                }
    }

    /**
     * Updates a channel's users count
     *
     * @return amount of users in the channel after the update
     */
    private fun updateChannelUsersCount(channel: String, change: Int = 1): CompletableFuture<Int> {
        return channelsRoot.document(channel)
                .read("users_count")
                .thenApply { oldUsersCount ->
                    oldUsersCount?.toInt()?.plus(change) ?: 0
                }.thenCompose { newUsersCount ->
                    channelsRoot.document(channel)
                            .set(Pair("users_count", newUsersCount.toString()))
                            .update()
                            .thenApply { newUsersCount }
                }
    }


    /**
     * Updates a channel's online users count
     *
     * @return amount of online users in the channel after the update
     */
    private fun updateChannelOnlineUsersCountIfOnline(channel: String, username: String, change: Int = 1)
            : CompletableFuture<Int> {

        var effectiveChange = 0
        val isOnlineFuture = usersRoot.document(username)
                .read("token")
                .thenApply { token ->
                    if (token != null)
                        effectiveChange = change
                }

        return isOnlineFuture.thenCompose {
            channelsRoot.document(channel)
                    .read("online_users_count")
                    .thenApply { oldOnlineUsersCount ->
                        oldOnlineUsersCount?.toInt()?.plus(effectiveChange) ?: 0
                    }.thenCompose { newOnlineUsersCount ->
                        channelsRoot.document(channel)
                                .set(Pair("online_users_count", newOnlineUsersCount.toString()))
                                .update()
                                .thenApply { newOnlineUsersCount }
                    }
        }
    }

    /**
     * Removes a user from the list of operators for this channel, if the user is indeed an operator for the channel.
     *
     * @return amount of operators in the channel after removal
     */
    private fun removeOperatorFromChannel(channel: String, operatorToDelete: String)
            : CompletableFuture<Int> {
        return channelsRoot.document(channel)
                .readList("operators")
                .thenApply { operators ->
                    operators?.toMutableList() ?: mutableListOf()
                }.thenCompose { operators ->
                    if (!operators.contains(operatorToDelete))
                        CompletableFuture.completedFuture(operators.size)
                    else {
                        operators.remove(operatorToDelete)
                        channelsRoot.document(channel)
                                .set("operators", operators)
                                .update()
                                .thenApply { operators.size }
                    }
                }
    }

    /**
     * Returns whether a given user is a member of a given channel or not
     */
    private fun isMemberOfChannel(username: String, channel: String): CompletableFuture<Boolean> {
        return usersRoot.document(username)
                .readList("channels")
                .thenApply { channels ->
                    channels != null && channels.contains(channel)
                }
    }

    /**
     * Translates a token to its corresponding user
     *
     * @throws InvalidTokenException if the token does not belong to any user
     */
    private fun tokenToUser(token: String): CompletableFuture<String> {
        return tokensRoot.document(token)
                .read("username")
                .thenApply { tokenUsername ->
                    tokenUsername ?: throw InvalidTokenException("token does not match any active user")
                }
    }

    /**
     * Returns whether or not a given user is an administrator
     */
    private fun isAdmin(username: String): CompletableFuture<Boolean> {
        return usersRoot.document(username)
                .read("isAdmin")
                .thenApply { isAdmin ->
                    isAdmin == "true"
                }
    }

    /**
     * Returns whether or not a given user is an operator of a given channel
     */
    private fun isOperator(username: String, channel: String): CompletableFuture<Boolean> {
        return channelsRoot.document(channel)
                .readList("operators")
                .thenApply { channelModerators ->
                    channelModerators?.contains(username) ?: false
                }
    }

    /**
     * Checks channels' names validity.
     * A channel's name is valid only if the following hold:
     *  - first letter is '#'
     *  - contains *only* a-z, A-Z, 0-9, '#' or '_' characters
     *
     * @param channel: name of the channel
     * @return true if the channel's name is valid, false otherwise
     */
    private fun validChannelName(channel: String): Boolean {
        if (channel[0] != '#') return false
        val validCharPool = ('a'..'z') + ('A'..'Z') + ('0'..'9') + '#' + '_'
        for (c in channel)
            if (!validCharPool.contains(c)) return false
        return true
    }
}