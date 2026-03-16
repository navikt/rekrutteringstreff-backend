plugins {
    id("toi.common")
}

repositories {
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

dependencies {
    implementation("com.github.navikt:rapids-and-rivers:2026021921161771532161.7a37f8c9e0cc")
}