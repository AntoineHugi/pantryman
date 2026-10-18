package org.pantry

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json
import org.pantry.postgres.DatabaseFactory
import org.pantry.plugins.*
import org.pantry.di.appModule
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger


fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    configureCors()

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            // Tolerate fields from older/newer clients (e.g. an outdated installed app)
            ignoreUnknownKeys = true
        })
    }
    install(Koin) {
        slf4jLogger()
        modules(appModule(this@module))
    }

    val config = environment.config
    val dbConfig = config.config("db")
    val url = System.getenv("DB_URL") ?: dbConfig.property("url").getString()
    val user = System.getenv("DB_USER") ?: dbConfig.property("user").getString()
    val password = System.getenv("DB_PASSWORD") ?: dbConfig.property("password").getString()

    DatabaseFactory.init(url, user, password)

    configureStatusPages()
    configureSecurity()
    configureRateLimiting()
    authAPI()
    groceriesApi()
    householdsApi()
    recipesApi()
    mealPlansApi()
    pantryApi()
}
