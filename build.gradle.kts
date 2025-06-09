plugins {
    kotlin("jvm") version "2.1.20"
    application
    id("io.ktor.plugin") version "3.1.3"
}

group = "org.pantry"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

application {
    // mainClass = "org.pantry.MainKt"
    mainClass = "io.ktor.server.netty.EngineMain"
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-netty")
    implementation("ch.qos.logback:logback-classic:1.4.11")
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-config-yaml")

    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}