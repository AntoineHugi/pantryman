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

fun Application.groceriesApi() {
    val groceryListService by inject <GroceryListService>()
    val itemService by inject <ItemService>()

    routing {
        route("/lists") {
            post {
                val request = call.receive<CreateGroceryListRequest>()
                val created = groceryListService.create(request.name)
                call.respond(HttpStatusCode.Created, created)
            }
            get {
                val lists = groceryListService.getAll()
                call.respond(lists)
            }
            route("/{listId}") {
                get {
                    val id = call.parameters["listId"]?.let { UUID.fromString(it) }
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid ID")
                        return@get
                    }
                    val list = groceryListService.getById(id)
                    if (list == null) {
                        call.respond(HttpStatusCode.NotFound, "List not found")
                    } else {
                        call.respond(list)
                    }
                }
                patch {
                    val id = call.parameters["listId"]?.let { UUID.fromString(it) }
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid ID")
                        return@patch
                    }
                    val body = call.receive<Map<String, String>>()
                    val updated = groceryListService.update(id, body["name"] ?: "")
                    if (updated)
                        call.respond(HttpStatusCode.OK)
                    else
                        call.respond(HttpStatusCode.NotFound)
                }
                delete {
                    val id = call.parameters["listId"]?.let { UUID.fromString(it) }
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid ID")
                        return@delete
                    }
                    val deleted = groceryListService.delete(id)
                    if (deleted) call.respond(HttpStatusCode.NoContent)
                    else call.respond(HttpStatusCode.NotFound)
                }
                route("/items") {
                    post {
                        val request = call.receive<CreateItemRequest>()
                        val item = itemService.create(
                            listId = request.listId,
                            name = request.name,
                            quantity = request.quantity
                        )
                        call.respond(HttpStatusCode.Created, item)
                    }
                    get {
                        val id = call.parameters["listId"]?.let { UUID.fromString(it) }
                        if (id == null) {
                            call.respond(HttpStatusCode.BadRequest, "Invalid ID")
                            return@get
                        }
                        val itemList = itemService.getAll(id)
                        call.respond(itemList)
                    }
                    route("/{itemId}") {
                        delete {
                            val id = UUID.fromString(call.parameters["itemId"])
                            val deleted = itemService.delete(id)
                            if (deleted) call.respond(HttpStatusCode.NoContent)
                            else call.respond(HttpStatusCode.NotFound, "Item not found")
                        }
                        patch {
                            val id = call.parameters["itemId"]?.let(UUID::fromString)
                                ?: return@patch call.respond(HttpStatusCode.BadRequest, "Invalid ID")
                            val request = call.receive<ItemUpdateRequest>()

                            try {
                                val updated = itemService.update(id, request)
                                if (updated) call.respond(HttpStatusCode.OK, "Item updated")
                                else call.respond(HttpStatusCode.NotFound, "Item not found")
                            } catch (e: IllegalArgumentException) {
                                call.respond(HttpStatusCode.BadRequest, e.message ?: "Invalid data")
                            }
                        }
                    }
                }
            }
        }
    }
}


