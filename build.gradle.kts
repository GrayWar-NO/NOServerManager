plugins {
    kotlin("jvm") version "2.3.0"
}

group = "org.graywar"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("com.sksamuel.hoplite:hoplite-core:2.9.0")
    implementation("com.sksamuel.hoplite:hoplite-hocon:2.9.0")
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}