import dev.kordex.gradle.plugins.kordex.DataCollection

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

val exposedVersion = "1.3.1"

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":proto"))
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.23.1")
    implementation("org.apache.logging.log4j:log4j-core:2.25.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    implementation("com.google.protobuf:protobuf-java:4.28.2")
    implementation("io.grpc:grpc-kotlin-stub:1.4.1")
    implementation("io.grpc:grpc-protobuf:1.59.0")
    implementation("io.grpc:grpc-netty-shaded:1.75.0")

    implementation("com.sksamuel.hoplite:hoplite-core:3.0.4")
    implementation("com.sksamuel.hoplite:hoplite-hocon:3.0.4")

    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposedVersion")
    
    implementation("org.jetbrains.exposed:exposed-migration-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-migration-jdbc:$exposedVersion") // Use exposed-migration-r2dbc if using R2DBC
    
    implementation("org.postgresql:postgresql:42.7.14")

    implementation("org.knowm.xchart:xchart:3.8.6")

    implementation("com.fasterxml.jackson.core:jackson-core:2.22.3") // Needed by ktor, but autoimport has vulnerability.

}

kordEx {
    bot {
        voice = false
        dataCollection(DataCollection.None)
    }
    kordExVersion = null // latest
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}