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
   mainClass = "org.pantry.ApplicationKt"
}

dependencies {
    val ktorVersion = "3.2.1"
    val logbackVersion = "1.5.6"
    val jUnitVersion = "5.10.0"

    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-netty")
    implementation("ch.qos.logback:logback-classic:${logbackVersion}")
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-config-yaml")

    implementation("io.ktor:ktor-server-content-negotiation:${ktorVersion}")
    implementation("io.ktor:ktor-serialization-kotlinx-json:${ktorVersion}")


    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.junit.jupiter:junit-jupiter:${jUnitVersion}")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}