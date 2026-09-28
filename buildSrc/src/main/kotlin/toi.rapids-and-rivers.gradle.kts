plugins {
    id("toi.app")
}

repositories {
    maven("https://jitpack.io")
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

dependencies {
    implementation("com.github.navikt:rapids-and-rivers:2026042913501777463400")
    testImplementation(project(":technical-libs:testrapid"))
}