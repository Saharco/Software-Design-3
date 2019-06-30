package il.ac.technion.cs.softwaredesign.wrappers

import il.ac.technion.cs.softwaredesign.messages.MediaType
import java.io.Serializable
import java.lang.IllegalArgumentException

class KeywordsTracker : Serializable {

    companion object {
        private val wildcardRegex = Regex("(?s).*")
    }

    private val channelMediaTrackerMap = mutableMapOf<Pair<String, MediaType>, ArrayList<Pair<Regex, Long>>>()
    private val channelTrackerMap = mutableMapOf<String, ArrayList<Pair<Regex, Long>>>()
    private val mediaTrackerMap = mutableMapOf<MediaType, ArrayList<Pair<Regex, Long>>>()
    private val globalTrackers = ArrayList<Pair<Regex, Long>>()

    operator fun set(channel: String?, mediaType: MediaType?, stringPattern: String?) {
        val regex = stringToRegex(stringPattern)

        if (sameRegex(regex, wildcardRegex) && mediaType == null)
            return

        if (channel == null && mediaType == null) {
            // map to global tracker
            resetExistingRegex(globalTrackers, regex)
        } else if (channel == null && mediaType != null) {
            // map to media tracker
            if (mediaTrackerMap[mediaType] == null)
                mediaTrackerMap[mediaType] = ArrayList()
            resetExistingRegex(mediaTrackerMap[mediaType]!!, regex)
        } else if (mediaType == null && channel != null) {
            // map to channel tracker
            if (channelTrackerMap[channel] == null)
                channelTrackerMap[channel] = ArrayList()
            resetExistingRegex(channelTrackerMap[channel]!!, regex)
        } else if (mediaType != null && channel != null) {
            // map to channel-media tracker
            val pair = Pair(channel, mediaType)
            if (channelMediaTrackerMap[pair] == null)
                channelMediaTrackerMap[pair] = ArrayList()
            resetExistingRegex(channelMediaTrackerMap[pair]!!, regex)
        }
    }

    operator fun get(channel: String?, mediaType: MediaType?, stringPattern: String?): Long {
        val regex = stringToRegex(stringPattern)

        if (sameRegex(regex, wildcardRegex) && mediaType == null)
            throw IllegalArgumentException()

        if (channel == null && mediaType == null) {
            // fetch from global tracker
            return fetchRegexCountFromList(globalTrackers, regex)
        }

        if (channel == null && mediaType != null) {
            // fetch from media tracker
            if (mediaTrackerMap[mediaType] == null)
                throw IllegalArgumentException()
            return fetchRegexCountFromList(mediaTrackerMap[mediaType]!!, regex)
        }

        if (mediaType == null && channel != null) {
            // fetch from channel tracker
            if (channelTrackerMap[channel] == null)
                throw IllegalArgumentException()
            return fetchRegexCountFromList(channelTrackerMap[channel]!!, regex)
        }

        // fetch from channel-media tracker
        val pair = Pair(channel, mediaType)
        if (channelMediaTrackerMap[pair] == null)
            throw IllegalArgumentException()
        return fetchRegexCountFromList(channelMediaTrackerMap[pair]!!, regex)
    }

    fun track(channel: String?, mediaType: MediaType, messageContent: String) {
        if (channel != null) {
            channelMediaTrackerMap[Pair(channel, mediaType)] =
                    incrementRegexCounters(channelMediaTrackerMap[Pair(channel, mediaType)], messageContent)
            channelTrackerMap[channel] = incrementRegexCounters(channelTrackerMap[channel], messageContent)
        }
        mediaTrackerMap[mediaType] = incrementRegexCounters(mediaTrackerMap[mediaType], messageContent)
        incrementRegexCounters(globalTrackers, messageContent)
    }

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