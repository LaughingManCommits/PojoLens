plugins {
    java
}

group = "io.github.laughingmancommits.examples"
version = "2026.04.29.1809"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    implementation("io.github.laughingmancommits:pojo-lens:2026.04.29.1809")
    annotationProcessor("io.github.laughingmancommits:pojo-lens:2026.04.29.1809")

    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
}

tasks.test {
    useJUnitPlatform()
}
