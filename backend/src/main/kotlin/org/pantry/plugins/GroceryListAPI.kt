package org.pantry.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import org.pantry.models.CreateGroceryListRequest
import org.pantry.models.CreateItemRequest
import org.pantry.models.ItemUpdateRequest
import org.pantry.models.UpdateGroceryListRequest
import org.pantry.services.GroceryListService
import org.pantry.services.ItemService
import java.util.UUID

fun Application.groceriesApi() {
    val groceryListService by inject <GroceryListService>()
    val itemService by inject <ItemService>()

    // Item routes act on a list the caller must own; returns the list ID.
    fun ApplicationCall.requireOwnedListId(): UUID {
        val listId = uuidParameter("listId")
        groceryListService.getById(listId, userId) ?: throw ForbiddenException("List not found or unauthorized")
        return listId
    }

    routing {
        authenticate("auth-jwt") {
            route("/lists") {
                post {
                    val request = call.receive<CreateGroceryListRequest>()
                    val created = groceryListService.create(request.name, call.userId)
                    call.respond(HttpStatusCode.Created, created)
                }
                get {
                    call.respond(groceryListService.getAll(call.userId))
                }
                route("/{listId}") {
                    get {
                        val list = groceryListService.getById(call.uuidParameter("listId"), call.userId)
                            ?: throw NotFoundException("List not found")
                        call.respond(list)
                    }
                    patch {
                        val id = call.uuidParameter("listId")
                        val request = call.receive<UpdateGroceryListRequest>()
                        if (!groceryListService.update(id, request.name, call.userId)) {
                            throw NotFoundException("List not found")
                        }
                        call.respond(HttpStatusCode.OK)
                    }
                    delete {
                        if (!groceryListService.delete(call.uuidParameter("listId"), call.userId)) {
                            throw NotFoundException("List not found")
                        }
                        call.respond(HttpStatusCode.NoContent)
                    }
                    route("/items") {
                        post {
                            val listId = call.requireOwnedListId()
                            val request = call.receive<CreateItemRequest>()
                            val item = itemService.create(
                                listId = listId,
                                name = request.name,
                                quantity = request.quantity
                            )
                            call.respond(HttpStatusCode.Created, item)
                        }
                        get {
                            val listId = call.requireOwnedListId()
                            call.respond(itemService.getAll(listId))
                        }
                        route("/{itemId}") {
                            delete {
                                val listId = call.requireOwnedListId()
                                if (!itemService.delete(listId, call.uuidParameter("itemId"))) {
                                    throw NotFoundException("Item not found")
                                }
                                call.respond(HttpStatusCode.NoContent)
                            }
                            patch {
                                val listId = call.requireOwnedListId()
                                val itemId = call.uuidParameter("itemId")
                                val request = call.receive<ItemUpdateRequest>()
                                if (!itemService.update(listId, itemId, request)) {
                                    throw NotFoundException("Item not found")
                                }
                                call.respond(HttpStatusCode.OK, mapOf("message" to "Item updated"))
                            }
                        }
                    }
                }
            }
        }
    }
}
