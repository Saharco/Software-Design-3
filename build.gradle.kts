import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.time.Duration

plugins {
    kotlin("jvm") version "1.3.30"
}

allprojects {
    repositories {
        jcenter()
        maven {
            setUrl("https://jitpack.io")
        }
    }

    extra.apply {
        set("junitVersion", "5.5.0-M1")
        set("hamkrestVersion", "1.7.0.0")
        set("mockkVersion", "1.9.3")
        set("guiceVersion", "4.2.2")
        set("kotlinGuiceVersion", "1.3.0")
        set("gsonVersion", "2.8.5")
        set("kotsonVersion", "2.5.0")
        set("kotlinCompletableFuturesVersion", "1.2.0")
        set("kotlinListenableFuturesVersion", "1.2.0")
        set("kotlinLoggerVersion", "1.6.24")
        set("kotlinLoggerImplVersion", "1.6.1")
        set("dokkaVersion", "0.9.18")
    }
}

dependencies {
    val junitVersion: String? by extra
    testRuntime("org.junit.jupiter", "junit-jupiter-engine", junitVersion)
}

subprojects {
    apply(plugin = "kotlin")
    dependencies {
        val dokkaVersion: String? by extra

        implementation(kotlin("stdlib-jdk8"))
        compile(kotlin("reflect"))
        compile("org.jetbrains.dokka:dokka-android-gradle-plugin:$dokkaVersion")
    }
    tasks.withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }
    tasks.withType<Test> {
        useJUnitPlatform()

        // Make sure tests don't take over 10 minutes
        timeout.set(Duration.ofMinutes(10))
    }
}

task<Zip>("submission") {
    val taskname = "submission"
    val base = project.rootDir.name
    archiveBaseName.set(taskname)
    from(project.rootDir.parentFile) {
        include("$base/**")
        exclude("$base/**/*.iml", "$base/*/build", "$base/**/.gradle", "$base/**/.idea", "$base/*/out",
                "$base/**/.git")
        exclude("$base/$taskname.zip")
    }
    destinationDirectory.set(project.rootDir)
}