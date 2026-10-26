// Deprekert: rapids-and-rivers og testrapid på Jackson 2.
// Brukes kun av apper som ikke er migrert til Jackson 3. Bytt til toi.rapids-and-rivers ved migrering.
plugins {
    id("toi.app")
}

repositories {
    maven("https://jitpack.io")
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

dependencies {
    implementation("com.github.navikt:rapids-and-rivers:2026042913501777463400")
    testImplementation(project(":technical-libs:testrapid-jackson2-deprekert"))
}
