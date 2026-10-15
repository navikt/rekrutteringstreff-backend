plugins {
    id("toi.app")
}

dependencies {
    implementation("com.github.navikt:rapids-and-rivers:2026091610031789545782")
    testImplementation(project(":technical-libs:testrapid"))
}