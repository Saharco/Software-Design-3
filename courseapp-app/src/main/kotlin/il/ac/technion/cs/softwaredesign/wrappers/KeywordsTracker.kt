package il.ac.technion.cs.softwaredesign.wrappers

import il.ac.technion.cs.softwaredesign.messages.MediaType
import java.io.Serializable
import java.lang.IllegalArgumentException
import il.ac.technion.cs.softwaredesign.CourseBot


/**
 * Helper class for [CourseBot].
 * Tracks keywords' regex matches in the following divisions:
 *  - all messages in all the channels the bot is a member of,
 *  - all messages of a given MediaType in all the channels the bot is a member of,
 *  - all messages in a given channel that the bot is a member of,
 *  - all messages of a given MediaType in a given channel that the bot is a member of
 *
 * @see CourseBot.beginCount
 * @see CourseBot.count
 */
class KeywordsTracker : Serializable {

    companion object {
        private val wildcardRegex = Regex("(?s).*")
    }

    private val channelMediaTrackerSet = mutableSetOf<Triple<String, MediaType, String?>>()
    private val channelTrackerSet = mutableSetOf<Pair<String, String?>>()
    private val mediaTrackerSet = mutableSetOf<Pair<MediaType, String?>>()
    private val globalTrackerSet = mutableSetOf<String?>()

    private val channelMediaTrackerMap = mutableMapOf<Pair<String, MediaType>, ArrayList<Pair<Regex, Long>>>()
    private val channelTrackerMap = mutableMapOf<String, ArrayList<Pair<Regex, Long>>>()
    private val mediaTrackerMap = mutableMapOf<MediaType, ArrayList<Pair<Regex, Long>>>()
    private val globalTrackers = ArrayList<Pair<Regex, Long>>()

    /**
     * Tracks a new regular expression of the pattern [stringPattern] in all the divisions that correspond to [channel] and [mediaType].
     * Setting the KeywordsTracker at [channel, mediaType] to value [stringPattern] when [mediaType] is null and [stringPattern] is null will do nothing.
     *
     * @param channel: channel in which to track the messages, or all channels if set to null
     * @param mediaType: message types to track, or all message types if set to null
     * @param stringPattern: regular expression to match new messages with
     */
    operator fun set(channel: String?, mediaType: MediaType?, stringPattern: String?) {
        val regex = stringToRegex(stringPattern)

        require(!(sameRegex(regex, wildcardRegex) && mediaType == null)) {
            "Either regex / media type fields must be non-null"
        }

        if (channel == null && mediaType == null) {
            // map to global tracker
            globalTrackerSet.add(stringPattern)
            resetExistingRegex(globalTrackers, regex)
        } else if (channel == null && mediaType != null) {
            // map to media tracker
            mediaTrackerSet.add(Pair(mediaType, stringPattern))
            if (mediaTrackerMap[mediaType] == null)
                mediaTrackerMap[mediaType] = ArrayList()
            resetExistingRegex(mediaTrackerMap[mediaType]!!, regex)
        } else if (mediaType == null && channel != null) {
            // map to channel tracker
            channelTrackerSet.add(Pair(channel, stringPattern))
            if (channelTrackerMap[channel] == null)
                channelTrackerMap[channel] = ArrayList()
            resetExistingRegex(channelTrackerMap[channel]!!, regex)
        } else if (mediaType != null && channel != null) {
            // map to channel-media tracker
            channelMediaTrackerSet.add(Triple(channel, mediaType, stringPattern))
            val pair = Pair(channel, mediaType)
            if (channelMediaTrackerMap[pair] == null)
                channelMediaTrackerMap[pair] = ArrayList()
            resetExistingRegex(channelMediaTrackerMap[pair]!!, regex)
        }
    }

    /**
     * Get the keywords tracking count in a given division.
     *
     * @param channel: channel from which to return the tracking count, or all channels if set to null
     * @param mediaType: message types of which to return the count, or all message types if set to null
     * @param stringPattern: regex pattern that matches all the counted messages, or all messages if set to null
     *
     * @throw [IllegalArgumentException] if [mediaType] and [stringPattern] are both null
     */
    operator fun get(channel: String?, mediaType: MediaType?, stringPattern: String?): Long {
        val regex = stringToRegex(stringPattern)

        require(!(sameRegex(regex, wildcardRegex) && mediaType == null)) { "Unregistered" }

        if (channel == null && mediaType == null) {
            // fetch from global tracker
            require(globalTrackerSet.contains(stringPattern)) { "Unregistered" }
            return fetchRegexCountFromList(globalTrackers, regex)
        }

        if (channel == null && mediaType != null) {
            // fetch from media tracker
            require(mediaTrackerSet.contains(Pair(mediaType, stringPattern))) { "Unregistered" }
            return fetchRegexCountFromList(mediaTrackerMap[mediaType]!!, regex)
        }

        if (mediaType == null && channel != null) {
            // fetch from channel tracker
            require(channelTrackerSet.contains(Pair(channel, stringPattern))) { "Unregistered" }
            return fetchRegexCountFromList(channelTrackerMap[channel]!!, regex)
        }

        // fetch from channel-media tracker
        val pair = Pair(channel, mediaType)
        require(channelMediaTrackerSet.contains(Triple(channel, mediaType, stringPattern))) { "Unregistered" }
        return fetchRegexCountFromList(channelMediaTrackerMap[pair]!!, regex)
    }

    /**
     * Matches all the appropriate regular expressions on a given message, and updates counters upon match
     *
     * @param channel: channel in which the message was sent, or null if it's not a channel message
     * @param mediaType: media type of the message
     * @param messageContent: content of the message
     */
    fun track(channel: String?, mediaType: MediaType, messageContent: String) {
        if (channel != null) {
            channelMediaTrackerMap[Pair(channel, mediaType)] =
                    incrementRegexCounters(channelMediaTrackerMap[Pair(channel, mediaType)], messageContent)
            channelTrackerMap[channel] = incrementRegexCounters(channelTrackerMap[channel], messageContent)
        }
        mediaTrackerMap[mediaType] = incrementRegexCounters(mediaTrackerMap[mediaType], messageContent)
        incrementRegexCounters(globalTrackers, messageContent)
    }

    /**
     * Removes all the keyword-tracks that correspond to [channelName]
     */
    fun remove(channelName: String) {
        for (media in MediaType.values())
        // remove channel-media tracker entry for all possible media types
            channelMediaTrackerMap.remove(Pair(channelName, media))
        channelTrackerMap.remove(channelName)
    }

    private fun incrementRegexCounters(regexCounters: ArrayList<Pair<Regex, Long>>?, messageContent: String)
            : ArrayList<Pair<Regex, Long>> {
        regexCounters ?: return ArrayList()
        for ((regex, counter) in regexCounters) {
            if (regex matches messageContent) {
                regexCounters.remove(Pair(regex, counter))
                regexCounters.add(Pair(regex, counter + 1))
            }
        }
        return regexCounters
    }

    private fun stringToRegex(stringPattern: String?): Regex =
            if (stringPattern == null) Regex("(?s).*") else Regex(stringPattern)

    private fun fetchRegexCountFromList(regexCounters: ArrayList<Pair<Regex, Long>>, regex: Regex): Long {
        for ((reg, count) in regexCounters) {
            if (sameRegex(reg, regex))
                return count
        }
        return 0L // we shouldn't get here
    }

    private fun resetExistingRegex(regexCounters: ArrayList<Pair<Regex, Long>>, regex: Regex) {
        for ((reg, count) in regexCounters) {
            if (sameRegex(reg, regex)) {
                regexCounters.remove(Pair(reg, count))
                break
            }
        }
        regexCounters.add(Pair(regex, 0L))
    }

    private fun sameRegex(regex1: Regex, regex2: Regex): Boolean = regex1.toString() == regex2.toString()
}