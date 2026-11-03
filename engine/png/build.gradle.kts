plugins {
    `java-library`
    kotlin("jvm")
}

kotlin { jvmToolchain(17) }

dependencies {
    testImplementation(kotlin("test-junit"))
}

// ImageIO is a desktop JDK API used only by the interoperability tests.
// The Android app consumes the production JAR, which uses java.io/java.util.zip.
tasks.test {
    systemProperty("java.awt.headless", "true")
}
