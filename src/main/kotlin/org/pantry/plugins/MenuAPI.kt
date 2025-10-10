package org.pantry.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import org.pantry.models.CreateGroceryListRequest
import org.pantry.models.CreateItemRequest
import org.pantry.models.ItemUpdateRequest
import org.pantry.services.GroceryListService
import org.pantry.services.ItemService
import java.util.UUID

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