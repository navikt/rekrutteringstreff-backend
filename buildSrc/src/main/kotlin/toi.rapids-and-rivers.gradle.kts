plugins {
    id("toi.app")
}

repositories {
    maven("https://jitpack.io")
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

// Versjonene ligger i gradle/libs.versions.toml, så Dependabot kan oppdatere dem
val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    // Rapids and rivers uten Ktor.
    implementation(libs.findLibrary("tbd-rapids-and-rivers-impl").get())
    implementation(libs.findLibrary("tbd-rapids-and-rivers-api").get())
    implementation(libs.findLibrary("tbd-kafka").get())
    testImplementation(project(":technical-libs:testrapid"))
}