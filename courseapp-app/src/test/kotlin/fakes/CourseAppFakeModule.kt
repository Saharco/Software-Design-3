package fakes

import com.authzee.kotlinguice4.KotlinModule
import com.google.inject.Inject
import com.google.inject.Provides
import com.google.inject.Singleton
import fakes.library.database.CachedStorage
import fakes.library.database.CourseAppDatabaseFactory
import fakes.library.database.Database
import fakes.library.database.DatabaseFactory
import fakes.messages.MessageFactoryImpl
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory
import fakes.library.utils.DatabaseMapper
import il.ac.technion.cs.softwaredesign.CourseApp
import il.ac.technion.cs.softwaredesign.CourseAppInitializer
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import java.util.concurrent.CompletableFuture

class CourseAppFakeModule : KotlinModule() {

    override fun configure() {
        bind<CourseApp>().to<CourseAppFake>().asEagerSingleton()
        bind<CourseAppStatistics>().to<CourseAppStatisticsImpl>()
        bind<CourseAppInitializer>().to<CourseAppInitializerFake>()
        bind<MessageFactory>().to<MessageFactoryImpl>()
    }

    @Provides
    @Singleton
    @Inject
    fun dbMapperProvider(factory: SecureStorageFactory): DatabaseMapper {
        val dbFactory = CourseAppDatabaseFactory(factory)
        val dbMap = mutableMapOf<String, CompletableFuture<Database>>()
        val storageMap = mutableMapOf<String, CompletableFuture<CachedStorage>>()

        mapNewDatabase(dbFactory, dbMap, "course_app_database")

        mapNewStorage(factory, storageMap, "channels_by_users")
        mapNewStorage(factory, storageMap, "channels_by_active_users")
        mapNewStorage(factory, storageMap, "users_by_channels")
        mapNewStorage(factory, storageMap, "channels_by_messages")
        return DatabaseMapper(dbMap, storageMap)
    }

    private fun mapNewDatabase(dbFactory: DatabaseFactory, dbMap: MutableMap<String, CompletableFuture<Database>>,
                               dbName: String) {
        dbMap[dbName] = dbFactory.open(dbName)
    }

    private fun mapNewStorage(factory: SecureStorageFactory,
                              storageMap: MutableMap<String, CompletableFuture<CachedStorage>>, storageName: String) {
        storageMap[storageName] =
                factory.open(storageName.toByteArray()).thenApply { storage -> CachedStorage(storage) }
    }
}