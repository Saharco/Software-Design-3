package il.ac.technion.cs.softwaredesign

import com.google.inject.Inject
import java.util.concurrent.CompletableFuture

class CourseAppInitializerImpl @Inject constructor(): CourseAppInitializer{
    override fun setup() : CompletableFuture<Unit> {
        return CompletableFuture.completedFuture(Unit)
    }
}