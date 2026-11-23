plugins {
    id("toi.app")
}

repositories {
    maven("https://jitpack.io")
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    implementation(libs.findLibrary("rapids-and-rivers").get())
    testImplementation(project(":technical-libs:testrapid"))
}