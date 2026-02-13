package org.pantry

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.http.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json
import org.pantry.postgres.DatabaseFactory
import org.pantry.plugins.*
import org.pantry.di.appModule
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger


fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    install(CORS) {
        allowHost("localhost:5173")
        allowHost("pantryman.de", schemes = listOf("https"))
        allowHost("www.pantryman.de", schemes = listOf("https"))
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
    }

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
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

    configureSecurity()
    authAPI()
    menuApi()
    groceriesApi()
}
