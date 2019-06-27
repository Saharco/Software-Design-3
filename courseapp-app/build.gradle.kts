plugins {
    application
    id("org.jetbrains.dokka") version "0.9.18"
}

application {
    mainClassName = "il.ac.technion.cs.softwaredesign.MainKt"
}

val junitVersion: String? by extra
val hamkrestVersion: String? by extra

val mockkVersion: String? by extra

val guiceVersion: String? by extra
val kotlinGuiceVersion: String? by extra

val gsonVersion: String? by extra
val kotsonVersion: String? by extra

val kotlinCompletableFuturesVersion: String? by extra
val kotlinListenableFuturesVersion: String? by extra

val kotlinLoggerVersion: String? by extra
val kotlinLoggerImplVersion: String? by extra

dependencies {
    compile(project(":library"))
    compile("com.google.inject", "guice", guiceVersion)
    compile("com.authzee.kotlinguice4", "kotlin-guice", kotlinGuiceVersion)

    testCompile("org.junit.jupiter", "junit-jupiter-api", junitVersion)
    testCompile("org.junit.jupiter", "junit-jupiter-params", junitVersion)
    testCompile("com.natpryce", "hamkrest", hamkrestVersion)

    testImplementation("io.mockk:mockk:$mockkVersion")
    compile("com.google.code.gson", "gson", gsonVersion)
    compile("com.github.salomonbrys.kotson", "kotson", kotsonVersion)

    compile("com.github.vjames19.kotlin-futures:kotlin-futures-jdk8:$kotlinCompletableFuturesVersion")
    compile("com.github.vjames19.kotlin-futures:kotlin-futures-guava:$kotlinListenableFuturesVersion")

    compile("io.github.microutils:kotlin-logging:$kotlinLoggerVersion")
    compile("org.slf4j:slf4j-simple:$kotlinLoggerImplVersion")

    testRuntime("org.junit.jupiter", "junit-jupiter-engine", junitVersion)
}

tasks.dokka {
    outputFormat = "html"
    outputDirectory = "build/javadoc"
}