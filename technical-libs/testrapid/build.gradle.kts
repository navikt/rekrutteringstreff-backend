plugins {
    id("toi.common")
}

repositories {
    maven("https://jitpack.io")
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

dependencies {
    implementation(libs.tbd.rapids.and.rivers.api)
    implementation(libs.jackson3.module.kotlin)
    implementation(libs.micrometer.core)
}