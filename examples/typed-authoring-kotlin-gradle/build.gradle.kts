plugins {
    kotlin("jvm") version "2.3.20"
    kotlin("kapt") version "2.3.20"
}

group = "io.github.laughingmancommits.examples"
version = "2026.04.29.1809"

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation("io.github.laughingmancommits:pojo-lens:2026.04.29.1809")
    kapt("io.github.laughingmancommits:pojo-lens:2026.04.29.1809")

    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
}

tasks.test {
    useJUnitPlatform()
}
