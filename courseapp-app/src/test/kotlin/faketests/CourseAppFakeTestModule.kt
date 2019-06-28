import com.authzee.kotlinguice4.KotlinModule
import fakes.CourseAppFakeModule
import fakes.library.mocks.SecureStorageFactoryMock
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory

class CourseAppFakeTestModule: KotlinModule() {
    override fun configure() {
        bind<SecureStorageFactory>().toInstance(SecureStorageFactoryMock())
        install(CourseAppFakeModule())
    }
}