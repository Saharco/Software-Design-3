import java.time.Duration
plugins {
}

val junitVersion: String? by extra
val hamkrestVersion: String? by extra
val guiceVersion: String? by extra
val kotlinGuiceVersion: String? by extra

dependencies {
    compile(project(":library"))
    compile(project(":courseapp-app"))

    testCompile("org.junit.jupiter", "junit-jupiter-api", junitVersion)
    testCompile("org.junit.jupiter", "junit-jupiter-params", junitVersion)
    testCompile("com.natpryce", "hamkrest", hamkrestVersion)
    testCompile("com.google.inject", "guice", guiceVersion)
    testCompile("com.authzee.kotlinguice4", "kotlin-guice", kotlinGuiceVersion)

    testImplementation("io.mockk", "mockk", "1.9.3")

    runtime("org.junit.jupiter", "junit-jupiter-engine", junitVersion)
}

tasks.test{
    useJUnitPlatform()

    // Make sure tests don't take over 40 minutes
    timeout.set(Duration.ofMinutes(40))
    reports{
        junitXml.isEnabled = false
        html.isEnabled = true

        //html.destination = File("path\\to\\dir\\destination")
    }
/*
    testLogging{
        val csvFile=File("\\path\\to\\csv")
        var toprow ="ClassName,TestName,Result,Duration(ms)\n"


        var content = ""
        addTestListener(object : TestListener {
            override fun beforeSuite(suite: TestDescriptor) {
                if(suite.parent == null){
                    csvFile.appendText(toprow)
                }
            }
            override fun beforeTest(testDescriptor: TestDescriptor) {}
            override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
                content += testDescriptor.getClassName()+","+
                        testDescriptor.getName()+","+
                        result.resultType.toString()+","+
                        (result.endTime-result.startTime).toString()+"\n"

            }
            override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                if(suite.parent == null){
                    println("Logging to csv at "+csvFile.absolutePath)
                    csvFile.appendText(content)
                    content=""
                }

            }
        })
    }
*/
}



