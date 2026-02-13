plugins {
    id("toi.common")
}

repositories {
    maven("https://jitpack.io")
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}
val tbdLibsVersion = "2026.01.28-07.21-5436e475"

dependencies {
    implementation("com.github.navikt:rapids-and-rivers:2025010715371736260653.d465d681c420")
    // Rapids and rivers fra tbd-libs (uten Ktor)
    implementation("com.github.navikt.tbd-libs:rapids-and-rivers:$tbdLibsVersion")
    implementation("com.github.navikt.tbd-libs:rapids-and-rivers-api:$tbdLibsVersion")
    implementation("com.github.navikt.tbd-libs:kafka:$tbdLibsVersion")
    testImplementation("com.github.navikt.tbd-libs:rapids-and-rivers-test:$tbdLibsVersion")
    testImplementation(project(":technical-libs:testrapid"))
}