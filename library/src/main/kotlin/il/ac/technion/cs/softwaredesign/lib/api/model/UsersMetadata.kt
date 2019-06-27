package il.ac.technion.cs.softwaredesign.lib.api.model

import il.ac.technion.cs.softwaredesign.lib.db.BaseModel

class UsersMetadata : BaseModel() {

    companion object {
        const val TYPE = "__metadata"
        const val KEY_TOTAL_USERS = "totalUsers"
        const val KEY_ONLINE_USERS = "onlineUsers"

        /**
         * Holds a sorting of channels by joined user count
         */
        const val CHANNELS_BY_USERS = "channelsByUserCount"

        /**
         * Holds a sorting of channels by online user count
         */
        const val CHANNELS_BY_ONLINE_USERS = "activeChannelsByOnlineUserCount"

        /**
         * Holds a sorting of users by number of joined channels
         */
        const val USERS_BY_CHANNELS = "usersByChannels"
    }

    var totalUsers: Long = -1
    var onlineUsers: Long = -1

}