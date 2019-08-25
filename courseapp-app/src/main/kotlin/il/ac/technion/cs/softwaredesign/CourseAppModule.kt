package il.ac.technion.cs.softwaredesign

import com.authzee.kotlinguice4.KotlinModule
import com.google.inject.Provides
import com.google.inject.Singleton
import il.ac.technion.cs.softwaredesign.annotations.DictionaryType
import il.ac.technion.cs.softwaredesign.annotations.IndexGeneratorForChannelIds
import il.ac.technion.cs.softwaredesign.constants.EConstants
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import il.ac.technion.cs.softwaredesign.messages.MessageFactoryImpl
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory
import java.util.concurrent.CompletableFuture

class CourseAppModule : KotlinModule() {
    override fun configure() {
        bind<CourseApp>().to<CourseAppImpl>().`in`<Singleton>()
        bind<CourseAppInitializer>().to<CourseAppInitializerImpl>().`in`<Singleton>()
        bind<CourseAppStatistics>().to<CourseAppStatisticsImpl>().`in`<Singleton>()

        bind<IStorage>().to<CStorageImpl>().`in`<Singleton>()
        bind<ICache>().to<CPerpetualICache>()
        bind<ICacheFactory>().to<CPerpetualCacheFactory>()
        bind<MessageFactory>().to<MessageFactoryImpl>()
    }

    @Provides
    @IndexGeneratorForChannelIds
    fun provideChannelIdsGenerator(IStorage: IStorage): IUniqueIndexGenerator {
        return CUniqueIndexGeneratorImpl(IStorage, EConstants.CHANNEL_IDS_GENERATOR.ordinal)
    }

    @Singleton
    @Provides
    fun provideSecureStorage(secureStorageFactory: SecureStorageFactory): CompletableFuture<SecureStorage> {
        return secureStorageFactory.open("main".toBytes())
    }

    fun getCompArray(): ArrayList<(String, String) -> Int> {
        val comp1 = fun(a: String, b: String): Int { return a.toInt() - b.toInt() }
        val comp2 = fun(a: String, b: String): Int { return b.toInt() - a.toInt() }
        return arrayListOf(comp1, comp2)
    }

    @Provides
    @Singleton
    @DictionaryType("channelMsgs")
    fun providesChannelMsgs(IStorage: IStorage): IDictionary {
        val pointerGenerator = CUniqueIndexGeneratorImpl(IStorage, EConstants.CHANNEL_MESSAGES_AVL.ordinal)
        return CAvlTree(IStorage,pointerGenerator, EConstants.CHANNEL_MESSAGES_AVL.ordinal, getCompArray())
    }

    @Provides
    @Singleton
    @DictionaryType("channelActiveUsers")
    fun providesChannelActiveUsersDictionary(IStorage:IStorage): IDictionary {
        val pointerGenerator = CUniqueIndexGeneratorImpl(IStorage, EConstants.CHANNEL_ACTIVE_USERS_AVL.ordinal)
        return CAvlTree(IStorage,pointerGenerator, EConstants.CHANNEL_ACTIVE_USERS_AVL.ordinal, getCompArray())
    }

    @Provides
    @Singleton
    @DictionaryType("channelUsers")
    fun providesChannelTotalUsersDictionary(IStorage:IStorage):  IDictionary {
        val pointerGenerator = CUniqueIndexGeneratorImpl(IStorage, EConstants.CHANNEL_USERS_AVL.ordinal)
        return CAvlTree(IStorage,pointerGenerator, EConstants.CHANNEL_USERS_AVL.ordinal, getCompArray())
    }

    @Provides
    @Singleton
    @DictionaryType("userChannels")
    fun providesUserChannelsDictionary(IStorage:IStorage):  IDictionary {
        val pointerGenerator = CUniqueIndexGeneratorImpl(IStorage, EConstants.USER_CHANNELS_AVL.ordinal)
        return CAvlTree(IStorage,pointerGenerator, EConstants.USER_CHANNELS_AVL.ordinal, getCompArray())
    }
}