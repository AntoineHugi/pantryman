package org.pantry

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import org.pantry.models.GroceryList
import org.pantry.models.CreateGroceryListRequest
import org.pantry.models.Item
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
                put {
                    val id = call.parameters["listId"]?.let { UUID.fromString(it) }
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid ID")
                        return@put
                    }

                    val body = call.receive<Map<String, String>>() // expects { "name": "New name" }
                    val updated = groceryListService.update(id, body["name"] ?: "")
                    if (updated) call.respond(HttpStatusCode.OK) else call.respond(HttpStatusCode.NotFound)
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
                /*post("/duplicate") {
                    call.respondText("Duplicate the list") //not implemented yet
                }*/
                route("/items") {
                    post {
                        val request = call.receive<Item>()
                        val item = itemService.create(
                            name = request.name,
                            quantity = request.quantity,
                            isChecked = request.isChecked,
                            isFavorite = request.isFavorite
                        )
                        call.respond(HttpStatusCode.Created, item)
                    }
                    route("/{itemId}") {
                        delete {
                            val id = UUID.fromString(call.parameters["itemId"])
                            val deleted = itemService.delete(id)
                            if (deleted) call.respond(HttpStatusCode.NoContent)
                            else call.respond(HttpStatusCode.NotFound, "Item not found")
                        }
                        post("/increase") {
                            val id = UUID.fromString(call.parameters["itemId"])
                            val item = itemService.getById(id)
                            if (item != null) {
                                itemService.update(id, item.name, item.quantity + 1, item.isChecked, item.isFavorite)
                                call.respond(HttpStatusCode.OK, "Quantity increased")
                            } else {
                                call.respond(HttpStatusCode.NotFound, "Item not found")
                            }
                        }
                        post("/decrease") {
                            val id = UUID.fromString(call.parameters["itemId"])
                            val item = itemService.getById(id)
                            if (item != null && item.quantity > 1) {
                                itemService.update(id, item.name, item.quantity - 1, item.isChecked, item.isFavorite)
                                call.respond(HttpStatusCode.OK, "Quantity decreased")
                            } else {
                                call.respond(HttpStatusCode.BadRequest, "Quantity cannot go below 1")
                            }
                        }
                    }
                }
            }
        }
    }
}


