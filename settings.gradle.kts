import java.net.URI

rootProject.name = "base"

sourceControl {
    gitRepository(URI("https://github.com/itai889/sd_s19_storage_impl.git")) {
        producesModule("il.ac.technion.cs.softwaredesign:primitive-storage-layer")
    }
}

include("library")
include("courseapp-app")
include("courseapp-test")
