plugins {
    kotlin("jvm")
    application
    id("dev.kordex.gradle.kordex") version "1.9.0"
}

group = "com.graywar"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

application{
    mainClass.set("com.graywar.noServerManager.dbManager.CentralServerKt")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":proto"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("com.google.protobuf:protobuf-java:4.28.2")
    implementation("io.grpc:grpc-kotlin-stub:1.4.1")
    implementation("io.grpc:grpc-protobuf:1.59.0")
    implementation("io.grpc:grpc-netty-shaded:1.75.0")
    implementation("com.sksamuel.hoplite:hoplite-core:2.9.0")
    implementation("com.sksamuel.hoplite:hoplite-hocon:2.9.0")
    implementation("org.jetbrains.exposed:exposed-core:1.1.1")
    implementation("org.jetbrains.exposed:exposed-dao:1.1.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:1.1.1")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:1.1.1")
    implementation("org.postgresql:postgresql:42.7.7")
    implementation("org.knowm.xchart:xchart:3.8.6")
}

kordEx {
    bot {
        voice = false
    }
    ignoreIncompatibleKotlinVersion.set(true)
    kordExVersion = null // latest
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}