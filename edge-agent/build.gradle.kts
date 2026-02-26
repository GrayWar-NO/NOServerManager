plugins {
    kotlin("jvm")
    application
    id("com.google.protobuf") version "0.9.4"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.10"
}

group = "com.graywar"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

application {
    mainClass.set("com.graywar.noServerManager.edge.MainKt")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":proto"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("com.google.protobuf:protobuf-java:4.28.2")
    implementation("io.grpc:grpc-kotlin-stub:1.4.1")
    implementation("io.grpc:grpc-protobuf:1.59.0")
    implementation("io.grpc:grpc-netty-shaded:1.75.0")
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}