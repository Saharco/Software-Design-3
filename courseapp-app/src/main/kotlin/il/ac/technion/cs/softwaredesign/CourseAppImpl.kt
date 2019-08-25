package il.ac.technion.cs.softwaredesign

import il.ac.technion.cs.softwaredesign.annotations.DictionaryType
import il.ac.technion.cs.softwaredesign.annotations.IndexGeneratorForChannelIds
import il.ac.technion.cs.softwaredesign.constants.EConstants
import il.ac.technion.cs.softwaredesign.constants.EConstants.*
import il.ac.technion.cs.softwaredesign.exceptions.*
import il.ac.technion.cs.softwaredesign.messages.*
import java.time.LocalDateTime.now
import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern
import javax.inject.Inject


class CourseAppImpl @Inject constructor(private val dataStore : IStorage,
                                        @IndexGeneratorForChannelIds private val channelIdGenerator: IUniqueIndexGenerator,
                                        @DictionaryType("channelUsers") private val channelsTotalUsers : IDictionary,
                                        @DictionaryType("channelActiveUsers") private val channelsActiveUsers : IDictionary,
                                        @DictionaryType("channelMsgs") private val channelMsgs : IDictionary,
                                        @DictionaryType("userChannels") private val userChannels : IDictionary) : CourseApp {


    private val listeners = HashMap<String,HashSet<ListenerCallback>>()

    private var usersTime : Long  = dataStore.getCounterValue(USERS_TIME.ordinal)
    private var channelsTime: Long = dataStore.getCounterValue(CHANNELS_TIME.ordinal)

    private fun userExists(username:String):Boolean{
        return dataStore.isValid(USER_EXISTS.ordinal,username)
    }

    private fun isCorrectPassword(username: String,password: String):Boolean{
        return dataStore.isValid(USER_HAS_PASSWORD.ordinal,username,password)
    }

    private fun userIsLoggedIn(username: String):Boolean{
        return dataStore.isValid(IS_LOGGED_IN.ordinal,username)
    }

    private fun isValidNameForChannel(channel:String):Boolean{
        return Pattern.matches("#([0-9]*[A-Z]*[a-z]*(_)*(#)*)*",channel)
    }

    private fun channelExists(channel: String):Boolean{
        return dataStore.readFromMap("${CHANNEL_NAME_TO_ID.ordinal},$channel")!=null &&
               dataStore.getCounterValue(TOTAL_USERS.ordinal,getChannelId(channel).toString())>0
    }

    private fun isTokenValid(token: String): Boolean {
        if(!Pattern.matches("(.*(\r)*(\n)*)*-[0-9]+",token)){//HAHA
            return false
        }
        val username = extractUsernameFromToken(token)
        val tokenNum = token.drop(username.length+1)
        val userTokenNum = dataStore.getCounterValue(USER_TO_TOKEN.ordinal,username).toString()
        return userIsLoggedIn(username) && tokenNum==userTokenNum
    }

    private fun extractUsernameFromToken(token:String): String{
        val stringsList = token.reversed().split('-')
        val tokenNum = stringsList[0].reversed()
        return token.dropLast(tokenNum.length+1)
    }

    private fun initNewUser(username: String,password: String){
        dataStore.makeValid(USER_HAS_PASSWORD.ordinal, username, password)
        dataStore.makeValid(USER_EXISTS.ordinal, username)
        val totalUsers = dataStore.getCounterValue(TOTAL_USERS.ordinal)
        if (totalUsers == 1L) dataStore.makeValid(IS_ADMIN.ordinal, username)
        dataStore.writeToMap("${USER_TO_TIME.ordinal},$username",usersTime.toString())
        userChannels.insert(arrayListOf("0", "$usersTime"),username)
    }

    override fun login(username: String, password: String): CompletableFuture<String> {
        val userExists = userExists(username)
        if (userExists && !isCorrectPassword(username, password)) {
            throw NoSuchEntityException()
        }
        if (userIsLoggedIn(username)) {
            throw UserAlreadyLoggedInException()
        }
        dataStore.incCounter(ACTIVE_USERS_COUNTER.ordinal)
        dataStore.makeValid(IS_LOGGED_IN.ordinal, username)
        val tokenNumber = dataStore.incCounter(USER_TO_TOKEN.ordinal,username)
        if (!userExists) {
            setUserTime(username,USER_TIME_OF_CREATION)
            dataStore.incCounter(TOTAL_USERS.ordinal)
            usersTime = dataStore.incCounter(USERS_TIME.ordinal)
            initNewUser(username,password)
        } else {
            updateActiveUsersCounterOfAllChannelsOfUser(username,1L)
        }
        return CompletableFuture.completedFuture("$username-$tokenNumber")
    }

    override fun logout(token: String):CompletableFuture<Unit>{
        if(!isTokenValid(token)){
            throw InvalidTokenException()
        }
        val username = extractUsernameFromToken(token)
        dataStore.decCounter(ACTIVE_USERS_COUNTER.ordinal)
        dataStore.invalidate(IS_LOGGED_IN.ordinal, username)
        updateActiveUsersCounterOfAllChannelsOfUser(username,-1L)
        return CompletableFuture.completedFuture(Unit)
    }

    override fun isUserLoggedIn(token: String, username: String): CompletableFuture<Boolean?> {
        if(!isTokenValid(token)){
            throw InvalidTokenException()
        }
        if(!userExists(username)) return CompletableFuture.completedFuture(null)
        if(userIsLoggedIn(username)) return CompletableFuture.completedFuture(true)
        return CompletableFuture.completedFuture(false)
    }

    override fun makeAdministrator(token: String, username: String):CompletableFuture<Unit> {
        if(!isTokenValid(token)){
            throw InvalidTokenException()
        }
        val user = extractUsernameFromToken(token)
        if(!dataStore.isValid(IS_ADMIN.ordinal,user)){
            throw UserNotAuthorizedException()
        }
        if(!userExists(username)){
            throw NoSuchEntityException()
        }
        dataStore.makeValid(IS_ADMIN.ordinal,username)
        return CompletableFuture.completedFuture(Unit)
    }

    private fun addNewChannel(channel:String):Int{
        val channelId = channelIdGenerator.getUniqueIndex().toString()
        dataStore.writeToMap("${CHANNEL_NAME_TO_ID.ordinal},$channel",channelId)
        dataStore.writeToMap("${CHANNEL_ID_TO_TIME.ordinal},$channelId",channelsTime.toString())
        dataStore.writeToMap("${CHANNEL_ID_TO_NAME.ordinal},$channelId",channel)

        channelMsgs.insert(arrayListOf("0",channelsTime.toString()),channelId)
        return channelId.toInt()
    }

    private fun updateActiveUsersCounterOfAllChannelsOfUser(username:String, addedValue:Long){
        val channelsList = dataStore.getList(USER_CHANNELS_LIST.ordinal,username)
        for(channelID in channelsList){
            updateUsersCounterOfChannel(channelID,addedValue, ACTIVE_USERS_COUNTER.ordinal)
        }
    }

    private fun updateUsersCounterOfChannel(channelId: Int,addedValue: Long,counterKey:Int){
        val channelsAvl : IDictionary = when (counterKey) {
            TOTAL_USERS.ordinal -> channelsTotalUsers
            ACTIVE_USERS_COUNTER.ordinal -> channelsActiveUsers
            else -> return
        }
        val usersCount = dataStore.getCounterValue(counterKey,channelId.toString())
        val channelTime = dataStore.readFromMap("${CHANNEL_ID_TO_TIME.ordinal},$channelId")
        if(usersCount >= 1) {
            channelsAvl.delete(arrayListOf((usersCount).toString(),"$channelTime"))
        }
        val newCount = usersCount+addedValue
        dataStore.setCounterValue(newCount,counterKey,channelId.toString())
        val channelTotalUsers = dataStore.getCounterValue(TOTAL_USERS.ordinal,channelId.toString())
        if(channelTotalUsers==0L){
            return
        }
        channelsAvl.insert(arrayListOf("$newCount","$channelTime"),channelId.toString())
    }

    private fun updateUserChannelsCounter(username: String,addedValue: Long){
        val oldNumOfChannels = dataStore.getCounterValue(USER_CHANNELS_COUNTER.ordinal, username)
        val usrNumOfChannels = oldNumOfChannels + addedValue
        dataStore.setCounterValue(usrNumOfChannels, USER_CHANNELS_COUNTER.ordinal, username)
        val userTime = dataStore.readFromMap("${USER_TO_TIME.ordinal},$username")
        userChannels.delete(arrayListOf("$oldNumOfChannels", "$userTime"))
        userChannels.insert(arrayListOf("$usrNumOfChannels", "$userTime"), username)
    }

    private fun removeUserFromChannel(username: String,channelId: Int){
        val channelExists = dataStore.removeFromList(USER_CHANNELS_LIST.ordinal, username, channelId)
        if(!channelExists) return
        updateUserChannelsCounter(username,-1L)
        updateUsersCounterOfChannel(channelId, -1L, TOTAL_USERS.ordinal)
        if (userIsLoggedIn(username)) {
            updateUsersCounterOfChannel(channelId, -1L, ACTIVE_USERS_COUNTER.ordinal)
        }
    }

    private fun addUserToChannel(username: String,channelId:Int) {
        val channelDoesNotExist = dataStore.addToList(USER_CHANNELS_LIST.ordinal, username, channelId)
        if(!channelDoesNotExist){
            return
        }
        updateUserChannelsCounter(username,1L)
        updateUsersCounterOfChannel(channelId, 1L, TOTAL_USERS.ordinal)
        if (userIsLoggedIn(username)) {
            updateUsersCounterOfChannel(channelId, 1L, ACTIVE_USERS_COUNTER.ordinal)
        }
    }

    override fun channelJoin(token: String, channel: String):CompletableFuture<Unit>{
        if(!isTokenValid(token)){
            throw InvalidTokenException()
        }
        if(!isValidNameForChannel(channel)){
            throw NameFormatException()
        }
        val username = extractUsernameFromToken(token)
        val channelId : Int

        if(!channelExists(channel)){
            if(!dataStore.isValid(IS_ADMIN.ordinal,username)) throw UserNotAuthorizedException()
            channelsTime = dataStore.incCounter(CHANNELS_TIME.ordinal)
            channelId = addNewChannel(channel)
            dataStore.makeValid(IS_OPERATOR.ordinal,username,channel)
            addUserToChannel(username,channelId)
        }else{
            channelId = getChannelId(channel)
            addUserToChannel(username,channelId)
        }
        dataStore.writeToMap("${USER_TIME_OF_JOINING_CHANNEL.ordinal},$channelId,$username",
                dataStore.getCounterValue(MESSAGES_COUNTER.ordinal).toString())
        dataStore.incCounter(CHANNEL_USERS_NUMBER.ordinal,channelId.toString())

        return CompletableFuture.completedFuture(Unit)
    }

    private fun deleteChannelMessagesCounterIfNeeded(channelId: Int){
        val channelTotalUsers = dataStore.getCounterValue(TOTAL_USERS.ordinal,channelId.toString())
        if(channelTotalUsers == 0L){
            val channelTime = dataStore.readFromMap("${CHANNEL_ID_TO_TIME.ordinal},$channelId")
            val channelMessages = dataStore.getCounterValue(CHANNEL_MESSAGES_COUNTER.ordinal,channelId.toString())
            channelMsgs.delete(arrayListOf("$channelMessages","$channelTime"))
            dataStore.setCounterValue(0,CHANNEL_MESSAGES_COUNTER.ordinal,channelId.toString())
        }
    }
    override fun channelPart(token: String, channel: String):CompletableFuture<Unit> {
        if(!isTokenValid(token)){
            throw InvalidTokenException()
        }
        if(!channelExists(channel)){
            throw NoSuchEntityException()
        }
        val channelId = getChannelId(channel)
        val username = extractUsernameFromToken(token)
        if(!isUserMemberOfChannel(username,channelId)){
            throw NoSuchEntityException()
        }
        removeUserFromChannel(username,channelId)
        dataStore.decCounter(CHANNEL_USERS_NUMBER.ordinal,channelId.toString())
        deleteChannelMessagesCounterIfNeeded(channelId)
        dataStore.invalidate(IS_OPERATOR.ordinal,username,channel)
        return CompletableFuture.completedFuture(Unit)
    }

    override fun channelMakeOperator(token: String, channel: String, username: String):CompletableFuture<Unit> {
        if(!isTokenValid(token)){
            throw InvalidTokenException()
        }
        if(!channelExists(channel)){
            throw NoSuchEntityException()
        }
        val tokenUsername = extractUsernameFromToken(token)
        val isOperator = dataStore.isValid(IS_OPERATOR.ordinal,tokenUsername,channel)
        val isAdmin = dataStore.isValid(IS_ADMIN.ordinal,tokenUsername)
        if (!isOperator && !isAdmin)
            throw UserNotAuthorizedException()
        if(isAdmin && !isOperator && username!=tokenUsername)
            throw UserNotAuthorizedException()
        val channelId = getChannelId(channel)
        if(!isUserMemberOfChannel(tokenUsername,channelId))
            throw UserNotAuthorizedException()
        if(!userExists(username)|| !isUserMemberOfChannel(username,channelId))
            throw NoSuchEntityException()
        dataStore.makeValid(IS_OPERATOR.ordinal,username,channel)
        return CompletableFuture.completedFuture(Unit)
    }

    override fun channelKick(token: String, channel: String, username: String) :CompletableFuture<Unit>{
        if(!isTokenValid(token)){
            throw InvalidTokenException()
        }
        if(!channelExists(channel)){
            throw NoSuchEntityException()
        }
        val tokenUsername = extractUsernameFromToken(token)
        val isOperator = dataStore.isValid(IS_OPERATOR.ordinal,tokenUsername,channel)
        if(!isOperator) throw UserNotAuthorizedException()
        val channelId = getChannelId(channel)
        if(!userExists(username)|| !isUserMemberOfChannel(username,channelId)) throw NoSuchEntityException()
        removeUserFromChannel(username,channelId)
        dataStore.decCounter(CHANNEL_USERS_NUMBER.ordinal,channelId.toString())
        deleteChannelMessagesCounterIfNeeded(channelId)
        dataStore.invalidate(IS_OPERATOR.ordinal,username,channel)
        return CompletableFuture.completedFuture(Unit)
    }

    private fun getChannelId(channel: String):Int{
        return dataStore.readFromMap("${CHANNEL_NAME_TO_ID.ordinal},$channel")!!.toInt()
    }

    override fun isUserInChannel(token: String, channel: String, username: String): CompletableFuture<Boolean?>{
        if(!isTokenValid(token)){
            throw InvalidTokenException()
        }
        if(!channelExists(channel)){
            throw NoSuchEntityException()
        }
        val channelId = getChannelId(channel)
        if(!userIsAuthorized(token,channelId)) {
            throw UserNotAuthorizedException()
        }
        if(!userExists(username)) return CompletableFuture.completedFuture(null)
        return CompletableFuture.completedFuture(isUserMemberOfChannel(username,channelId))
    }

    private fun isUserMemberOfChannel(username: String,channelId: Int):Boolean{
        return dataStore.listContains(USER_CHANNELS_LIST.ordinal,username,channelId)
    }

    private fun userIsAuthorized(token:String,channelId: Int):Boolean{
        val username = extractUsernameFromToken(token)
        if(!dataStore.isValid(IS_ADMIN.ordinal,username)){
            if(!isUserMemberOfChannel(username,channelId)){
                return false
            }
        }
        return true
    }

    override fun numberOfActiveUsersInChannel(token: String, channel: String): CompletableFuture<Long> {
        if(!isTokenValid(token)){
            throw InvalidTokenException()
        }
        if(!channelExists(channel)){
            throw NoSuchEntityException()
        }
        val channelId = getChannelId(channel)
        if(!userIsAuthorized(token,channelId)) {
            throw UserNotAuthorizedException()
        }
        return CompletableFuture.completedFuture(dataStore.getCounterValue(ACTIVE_USERS_COUNTER.ordinal,channelId.toString()))
    }

    override fun numberOfTotalUsersInChannel(token: String, channel: String): CompletableFuture<Long> {
        if(!isTokenValid(token)){
            throw InvalidTokenException()
        }
        if(!channelExists(channel)){
            throw NoSuchEntityException()
        }
        val channelId = getChannelId(channel)
        if(!userIsAuthorized(token,channelId)) {
            throw UserNotAuthorizedException()
        }
        return CompletableFuture.completedFuture(dataStore.getCounterValue(TOTAL_USERS.ordinal,channelId.toString()))
    }


    private fun setUserTime(username: String, timeType : EConstants){
        val time = dataStore.getCounterValue(MESSAGES_COUNTER.ordinal)
        dataStore.writeToMap("${timeType.ordinal},$username", time.toString())
    }

    private fun getUserTime(username: String, timeType : EConstants):Long?{
        val time = dataStore.readFromMap("${timeType.ordinal},$username") ?: return null
        return time.toLong()
    }


    override fun addListener(token: String, callback: ListenerCallback): CompletableFuture<Unit> {
        if(!isTokenValid(token)){
            throw InvalidTokenException()
        }
        val username = extractUsernameFromToken(token)
        if(listeners[username]== null){
            listeners[username] = hashSetOf()
        }
        listeners[username]!!.add(callback)
        readPendingMessages(username, callback)
        setUserTime(username,USER_LAST_SEEN)
        return CompletableFuture.completedFuture(Unit)
    }

    private fun getUserJoiningTime(username: String,channel: String):Long?{
        val channelId = getChannelId(channel)
        val time = dataStore.readFromMap("${USER_TIME_OF_JOINING_CHANNEL.ordinal},$channelId,$username")
        return if(time == null) null else time.toLong()
    }

    private fun readPendingMessages(username: String, callback: ListenerCallback) {
        val listOfPendingMessages = dataStore.getList(PENDING_MESSAGES_LIST.ordinal)
        val messageIdsToBeRemoved = mutableListOf<Int>()
        val userCreationTime = getUserTime(username,USER_TIME_OF_CREATION)!!
        val userLastSeen = getUserTime(username,USER_LAST_SEEN)
        var backedUpMessage : BackedUpMessage
        for (id in listOfPendingMessages) {
            backedUpMessage = BackedUpMessageImpl(dataStore,id.toLong())
            val message = getMessage(id.toLong())!!
            val messageType = backedUpMessage.type!!
            val destination = backedUpMessage.destination!!
            val source = backedUpMessage.source!!
            if (messageType == MessageType.PRIVATE) {
                if (destination == username) {
                    callback(source, message).get()
                    if(message.received==null) message.received = now()
                    messageIdsToBeRemoved.add(id)
                    dataStore.decCounter(PENDING_PRIVATE_MESSAGES.ordinal)
                }
            } else if (userLastSeen == null || userLastSeen < message.id) {
                if (messageType == MessageType.BROADCAST ) {
                    if( userCreationTime < message.id) {
                        readPendingMessage(callback, source, message)
                        if (backedUpMessage.pendingReadersNum == 0L) {
                            dataStore.decCounter(PENDING_BROADCAST_MESSAGES.ordinal)
                            messageIdsToBeRemoved.add(id)
                        }
                    }
                } else {
                    val channelId = getChannelId(destination)
                    val userJoiningTime = getUserJoiningTime(username,destination)
                    if (userJoiningTime!=null &&
                            isUserMemberOfChannel(username, channelId) && userJoiningTime < message.id) {
                        readPendingMessage(callback, source, message)
                        if (backedUpMessage.pendingReadersNum==0L) messageIdsToBeRemoved.add(id)
                    }
                }
            }
        }
        for(id in messageIdsToBeRemoved){
            dataStore.removeFromList(PENDING_MESSAGES_LIST.ordinal,value = id)
        }
    }

    private fun readPendingMessage(callback: ListenerCallback, source: String, message: Message) {
        callback(source, message).get()
        if(message.received==null) message.received = now()
        val backedUpMessage = BackedUpMessageImpl(dataStore,message.id)
        backedUpMessage.pendingReadersNum = backedUpMessage.pendingReadersNum!!-1
    }

    override fun removeListener(token: String, callback: ListenerCallback): CompletableFuture<Unit> {
        if(!isTokenValid(token)){
            throw InvalidTokenException()
        }
        val username = extractUsernameFromToken(token)
        if(listeners[username]==null){
            throw NoSuchEntityException()
        }
        val callBackExists = listeners[username]!!.remove(callback)
        if(!callBackExists) {
            throw NoSuchEntityException()
        }
        return CompletableFuture.completedFuture(Unit)
    }

    private fun incChannelMessagesCounter(channelId : Int){
        val newCount = dataStore.incCounter(CHANNEL_MESSAGES_COUNTER.ordinal,channelId.toString())
        val oldCount = newCount - 1
        val channelTime = dataStore.readFromMap("${CHANNEL_ID_TO_TIME.ordinal},$channelId")!!
        channelMsgs.delete(arrayListOf("$oldCount",channelTime))
        channelId.toString()
        channelMsgs.insert(arrayListOf("$newCount",channelTime),"$channelId")
    }

    override fun channelSend(token: String, channel: String, message: Message): CompletableFuture<Unit> {
        if(!isTokenValid(token)){
            throw InvalidTokenException()
        }
        if(!channelExists(channel)){
            throw NoSuchEntityException()
        }
        val username = extractUsernameFromToken(token)
        val channelId = getChannelId(channel)
        if(!isUserMemberOfChannel(username,channelId)){
            throw  UserNotAuthorizedException()
        }
        dataStore.incCounter(CHANNEL_MESSAGES_COUNTER.ordinal)
        incChannelMessagesCounter(channelId)
        val source = "$channel@$username"
        setAdditionalMessageFields(message, MessageType.CHANNEL, source, channel)
        val numberOfMembers = dataStore.getCounterValue(CHANNEL_USERS_NUMBER.ordinal,channelId.toString())
        val backedUpMessage = BackedUpMessageImpl(dataStore,message.id)
        backedUpMessage.pendingReadersNum = numberOfMembers
        for((key,value) in listeners){
            if(isUserMemberOfChannel(key,channelId)){
                for(listenerCallBack in value){
                    listenerCallBack(source,message).get()
                    if(message.received==null)
                        message.received = now()
                }
                if(value.size > 0 ) setUserTime(key,USER_LAST_SEEN)
                backedUpMessage.pendingReadersNum = backedUpMessage.pendingReadersNum!! - 1
            }
        }
        if(backedUpMessage.pendingReadersNum!! > 0){
            //dataStore.addToList(PENDING_MESSAGES_LIST.ordinal,value = message.id.toInt())
        }
        return CompletableFuture.completedFuture(Unit)
    }

    private fun setAdditionalMessageFields(message: Message, type: MessageType,source: String, destination:String) {
        val backedUpMessage = BackedUpMessageImpl(dataStore,message.id)
        backedUpMessage.source = source
        backedUpMessage.destination =  destination
        backedUpMessage.type = type
    }

    override fun broadcast(token: String, message: Message): CompletableFuture<Unit> {
        if(!isTokenValid(token)){
            throw InvalidTokenException()
        }
        val username = extractUsernameFromToken(token)
        val isAdmin = dataStore.isValid(IS_ADMIN.ordinal, username)
        if(!isAdmin){
            throw UserNotAuthorizedException()
        }
        val source = "BROADCAST"
        val backedUpMessage = BackedUpMessageImpl(dataStore,message.id)
        var numOfPendingUsers = dataStore.getCounterValue(TOTAL_USERS.ordinal)
        for((key,value) in listeners){
            for(listenerCallBack in value){
               listenerCallBack(source,message).get()
                if(message.received==null) message.received = now()
            }
            if(value.size > 0 ) setUserTime(key,USER_LAST_SEEN)
            numOfPendingUsers--
        }
        if(numOfPendingUsers>0){
            setAdditionalMessageFields(message,MessageType.BROADCAST,source,"ALL")
            backedUpMessage.pendingReadersNum = numOfPendingUsers
            dataStore.incCounter(PENDING_BROADCAST_MESSAGES.ordinal)
            dataStore.addToList(PENDING_MESSAGES_LIST.ordinal,value=message.id.toInt())
        }
        return CompletableFuture.completedFuture(Unit)
    }

    override fun privateSend(token: String, user: String, message: Message): CompletableFuture<Unit> {
        if(!isTokenValid(token)){
            throw InvalidTokenException()
        }
        if(!userExists(user)){
            throw NoSuchEntityException()
        }
        val tokenUser = extractUsernameFromToken(token)
        val source = "@$tokenUser"
        val backedUpMessage = BackedUpMessageImpl(dataStore,message.id)
        if(listeners[user]!=null){
            for(listenerCallBack in listeners[user]!!){

                listenerCallBack(source,message).get()
                if(message.received==null) message.received = now()
            }
        }else {
            setAdditionalMessageFields(message,MessageType.PRIVATE,source,user)
            backedUpMessage.pendingReadersNum = 1
            dataStore.incCounter(PENDING_PRIVATE_MESSAGES.ordinal)
            dataStore.addToList(PENDING_MESSAGES_LIST.ordinal,value = message.id.toInt())
        }
        return CompletableFuture.completedFuture(Unit)
    }

    private fun buildMsgFieldKey(field: MessageFields, messageId: Long) : String{
        return "${MESSAGE_FILE_ID.ordinal},$messageId,${field.ordinal}"
    }

    private fun getMessage(id: Long): Message?{
        dataStore.readFromMap(buildMsgFieldKey(MessageFields.ID,id))?.toLong() ?: return null
        val contents =
                dataStore.readFromMap(buildMsgFieldKey(MessageFields.CONTENTS, id))!!.toByteArray(Charsets.UTF_8)
        val ordinal = dataStore.readFromMap(buildMsgFieldKey(MessageFields.MEDIA, id))!!.toInt()
        val media =  MediaType.values()[ordinal]
        return MessageImpl(dataStore,id,media,contents)
    }

    override fun fetchMessage(token: String, id: Long): CompletableFuture<Pair<String, Message>> {
        if(!isTokenValid(token)){
            throw InvalidTokenException()
        }
        val message = getMessage(id) ?: throw NoSuchEntityException()
        val backedUpMessage = BackedUpMessageImpl(dataStore,id)
        val messageType = backedUpMessage.type
        if(messageType != MessageType.CHANNEL)
            throw NoSuchEntityException()
        val username = extractUsernameFromToken(token)
        val channelId = getChannelId(backedUpMessage.destination!!)
        if(!isUserMemberOfChannel(username,channelId))
            throw UserNotAuthorizedException()
        val source = backedUpMessage.source!!
        return CompletableFuture.completedFuture(Pair(source,message))
    }


}