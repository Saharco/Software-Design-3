import com.authzee.kotlinguice4.KotlinModule
import fakes.library.mocks.SecureStorageFactoryMock
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory

class LibraryTestModule: KotlinModule() {
    override fun configure() {
        bind<SecureStorageFactory>().toInstance(SecureStorageFactoryMock())
    }
}