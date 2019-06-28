package il.ac.technion.cs.softwaredesign

import com.authzee.kotlinguice4.KotlinModule
import com.google.inject.Inject
import com.google.inject.Provides
import com.google.inject.Singleton

class CourseBotModule : KotlinModule() {
    override fun configure() {
        bind<CourseBots>().to<CourseBotsImpl>()
        bind<CourseBot>().to<CourseBotImpl>()
    }
}