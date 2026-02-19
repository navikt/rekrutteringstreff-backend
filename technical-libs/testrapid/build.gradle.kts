plugins {
    id("toi.common")
}

repositories {
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

dependencies {
    implementation("com.github.navikt:rapids-and-rivers:2026021809281771403330.8bdda3e27fff")
}