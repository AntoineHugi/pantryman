package org.pantry.plugins

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.menuApi() {
    routing {
        get(
            "/menu"
        ) {
            call.respondText("Hello World!")
        }
        get("/userSettings") {
            call.respondText("Hello World!")
        }
        get("/appSettings") {
            call.respondText("Hello World!")
        }
    }
}