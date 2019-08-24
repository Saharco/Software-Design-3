package il.ac.technion.cs.softwaredesign.annotations

import com.google.inject.BindingAnnotation

@BindingAnnotation
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE, AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class IndexGeneratorForChannelIds