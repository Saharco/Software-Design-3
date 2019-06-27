val junitVersion = "5.5.0-M1"
val hamkrestVersion = "1.7.0.0"
plugins{
    application
    id("org.jetbrains.dokka") version "0.9.18"
}

dependencies {
    compile("il.ac.technion.cs.softwaredesign", "primitive-storage-layer", "1.2")

    compile("io.mockk:mockk:1.9.3")
    testCompile("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testCompile("org.junit.jupiter:junit-jupiter-params:$junitVersion")
    testCompile("com.natpryce:hamkrest:$hamkrestVersion")
    implementation("com.google.code.gson:gson:2.8.5")
    runtime("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    compile("io.reactivex.rxjava2", "rxjava" , "2.2.9")
    compile("io.reactivex.rxjava2", "rxkotlin", "2.3.0")
}

tasks.dokka{
    outputFormat = "html"
    outputDirectory = "$buildDir/javadoc"
}