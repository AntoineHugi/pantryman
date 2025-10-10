package org.pantry

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.engine.*
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
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
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
        modules(appModule)
    }

    val config = environment.config
    val dbConfig = config.config("db")
    val url = dbConfig.property("url").getString()
    val user = dbConfig.property("user").getString()
    val password = dbConfig.property("password").getString()

    DatabaseFactory.init(url, user, password)

    menuApi()
    groceriesApi()
}
