plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "1.9.23"
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
    mainClass.set("io.ktor.server.netty.EngineMain")
}

dependencies {
    val ktorVersion = "3.2.1"
    val logbackVersion = "1.5.6"
    val jUnitVersion = "5.10.0"
    val hikariCPVersion="5.1.0"
    val postgresqlVersion="42.7.3"
    val flywayVersion="10.15.0"
    val exposedVersion="0.54.0"
    val mockkVersion="1.14.5"
    val kotlinxVersion="1.6.0"
    val koinVersion="4.1.0"
    val ktorCorsVersion="3.2.3"

    // Ktor
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-config-yaml:${ktorVersion}")
    implementation("io.ktor:ktor-server-content-negotiation:${ktorVersion}")
    implementation("io.ktor:ktor-serialization-kotlinx-json:${ktorVersion}")

    // Database
    implementation("org.flywaydb:flyway-core:${flywayVersion}")
    implementation("org.flywaydb:flyway-database-postgresql:${flywayVersion}")
    implementation("com.zaxxer:HikariCP:${hikariCPVersion}")
    implementation("org.postgresql:postgresql:${postgresqlVersion}")

    // Exposed
    implementation("org.jetbrains.exposed:exposed-core:${exposedVersion}")
    implementation("org.jetbrains.exposed:exposed-dao:${exposedVersion}")
    implementation("org.jetbrains.exposed:exposed-jdbc:${exposedVersion}")
    implementation("com.h2database:h2:2.2.224")

    // Logging
    implementation("ch.qos.logback:logback-classic:${logbackVersion}")

    // CORS
    implementation("io.ktor:ktor-server-cors:${ktorCorsVersion}")



    // Koin
    implementation("io.insert-koin:koin-core:${koinVersion}")
    implementation("io.insert-koin:koin-ktor:${koinVersion}")
    implementation("io.insert-koin:koin-logger-slf4j:${koinVersion}")
    testImplementation("io.insert-koin:koin-test:${koinVersion}")
    testImplementation("io.insert-koin:koin-test-junit5:${koinVersion}")

    // Unit Tests
    testImplementation("io.mockk:mockk:${mockkVersion}")
    testImplementation("org.junit.jupiter:junit-jupiter:${jUnitVersion}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${jUnitVersion}")
    testImplementation("org.junit.jupiter:junit-jupiter-params:${jUnitVersion}")

    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("io.ktor:ktor-client-content-negotiation:${ktorVersion}")
    testImplementation("io.ktor:ktor-serialization-kotlinx-json:${ktorVersion}")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${kotlinxVersion}")
    testImplementation("io.ktor:ktor-client-mock:${ktorVersion}")


    testImplementation(kotlin("test"))


}

tasks.test {
    useJUnitPlatform()
}