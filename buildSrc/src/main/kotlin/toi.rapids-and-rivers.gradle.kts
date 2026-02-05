plugins {
    id("toi.common")
}

repositories {
    maven("https://jitpack.io")
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

dependencies {
    // Rapids and rivers fra tbd-libs (uten Ktor)
    val tbdLibsVersion = "2024.11.25-10.59-6f263a10"
    implementation("com.github.navikt.tbd-libs:rapids-and-rivers:$tbdLibsVersion")
    implementation("com.github.navikt.tbd-libs:rapids-and-rivers-api:$tbdLibsVersion")
    implementation("com.github.navikt.tbd-libs:kafka:$tbdLibsVersion")
    testImplementation("com.github.navikt.tbd-libs:rapids-and-rivers-test:$tbdLibsVersion")
    testImplementation(project(":technical-libs:testrapid"))
}