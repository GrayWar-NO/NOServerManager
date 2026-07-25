plugins {
    kotlin("jvm")
    application
    id("dev.kordex.gradle.kordex") version "1.9.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.10"
}

group = "com.graywar"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

application{
    mainClass.set("com.graywar.noServerManager.dbManager.CentralServerKt")
}

val ktorVersion = "3.5.1"
val exposedVersion = "1.1.1"

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

    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposedVersion")
    
    implementation("org.jetbrains.exposed:exposed-migration-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-migration-jdbc:$exposedVersion") // Use exposed-migration-r2dbc if using R2DBC
    
    implementation("org.postgresql:postgresql:42.7.7")

    implementation("org.knowm.xchart:xchart:3.8.6")

    implementation("com.fasterxml.jackson.core:jackson-core:2.21.1") // Needed by ktor, but autoimport has vulnerability.

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
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